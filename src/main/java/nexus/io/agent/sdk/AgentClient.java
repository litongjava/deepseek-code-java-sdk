package nexus.io.agent.sdk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import nexus.io.agent.sdk.hook.HookCallback;
import nexus.io.agent.sdk.hook.HookEvent;
import nexus.io.agent.sdk.hook.HookMatcher;
import nexus.io.agent.sdk.json.Json;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.Messages;
import nexus.io.agent.sdk.message.RawMessage;
import nexus.io.agent.sdk.message.ResultMessage;
import nexus.io.agent.sdk.permission.PermissionResult;
import nexus.io.agent.sdk.permission.ToolPermissionContext;
import nexus.io.agent.sdk.transport.SubprocessTransport;
import nexus.io.agent.sdk.transport.Transport;

/**
 * A long-lived, bidirectional session with an agent CLI.
 *
 * <pre>
 * try (AgentClient client = new AgentClient(options)) {
 *   client.connect();
 *   client.query("Summarize README.md");
 *   for (Message message : client.receiveResponse()) {
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>Messages are read on a background thread. Permission, hook, MCP and
 * elicitation callbacks run on a separate worker pool, so a slow callback
 * never blocks reading.
 */
public class AgentClient implements AutoCloseable {
  private static final Object END = new Object();

  private final AgentOptions options;
  private final Transport transport;
  private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<Object>();
  private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>>();
  private final Map<String, Future<?>> inbound = new ConcurrentHashMap<String, Future<?>>();
  private final Map<String, HookCallback> hookCallbacks = new LinkedHashMap<String, HookCallback>();
  private final Map<String, SdkMcpServer> mcpServers = new LinkedHashMap<String, SdkMcpServer>();
  private final AtomicLong counter = new AtomicLong();
  private final ExecutorService workers;
  private volatile Map<String, Object> serverInfo;
  private volatile Throwable readFailure;
  private volatile boolean connected;
  private volatile boolean closed;
  private Integer exitCode;

  public AgentClient(AgentOptions options) {
    this(options, new SubprocessTransport(options));
  }

  public AgentClient(AgentOptions options, Transport transport) {
    this.options = options;
    this.transport = transport;
    for (SdkMcpServer server : options.getMcpServers()) {
      mcpServers.put(server.getName(), server);
    }
    this.workers = Executors.newCachedThreadPool(new ThreadFactory() {
      private final AtomicLong number = new AtomicLong();

      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "agent-sdk-callback-" + number.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      }
    });
  }

  /** Starts the CLI and sends {@code initialize}. */
  public synchronized void connect() {
    if (connected) {
      return;
    }
    if (closed) {
      throw new AgentException("Client is closed");
    }
    try {
      transport.start();
    } catch (IOException e) {
      throw new AgentException("Cannot start agent CLI: " + e.getMessage(), e);
    }
    Thread reader = new Thread(new Runnable() {
      @Override
      public void run() {
        readLoop();
      }
    }, "agent-sdk-reader");
    reader.setDaemon(true);
    reader.start();
    connected = true;
    serverInfo = control(initializeRequest());
  }

  /** The {@code initialize} answer: commands, agents, models, output styles... */
  public Map<String, Object> getServerInfo() {
    return serverInfo;
  }

  /** Sends a user prompt and returns its message UUID (usable with {@link #rewindFiles}). */
  public String query(String prompt) {
    return send(prompt, null);
  }

  /**
   * Sends content blocks, for example text and
   * {@code {"type":"image","source":{"type":"base64","media_type":"image/png","data":"..."}}}.
   */
  public String query(List<Map<String, Object>> content) {
    return send(content, null);
  }

  /** Sends a prompt that must belong to the given session id. */
  public String query(String prompt, String sessionId) {
    return send(prompt, sessionId);
  }

  private String send(Object content, String sessionId) {
    requireConnected();
    String uuid = UUID.randomUUID().toString();
    Map<String, Object> frame = new LinkedHashMap<String, Object>();
    frame.put("type", "user");
    frame.put("message", JsonMaps.of("role", "user", "content", content));
    frame.put("parent_tool_use_id", null);
    if (sessionId != null) {
      frame.put("session_id", sessionId);
    }
    frame.put("uuid", uuid);
    write(frame);
    return uuid;
  }

  /**
   * The next message, waiting up to the timeout. Returns null on timeout or
   * when the CLI has ended; {@link #isFinished()} tells the two apart.
   */
  public Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException {
    Object next = inbox.poll(timeout, unit);
    if (next == END) {
      inbox.offer(END);
      throwReadFailure();
      return null;
    }
    return (Message) next;
  }

  /** True once the CLI's output has ended and every message has been taken. */
  public boolean isFinished() {
    return inbox.peek() == END;
  }

  /** Every message until the CLI ends. */
  public Iterable<Message> receiveMessages() {
    return iterate(false);
  }

  /** Messages up to and including the next {@link ResultMessage}. */
  public Iterable<Message> receiveResponse() {
    return iterate(true);
  }

  /** Waits for the next result, passing every message, the result included, to the consumer. */
  public ResultMessage awaitResult(Consumer<Message> onMessage) {
    for (Message message : receiveResponse()) {
      if (onMessage != null) {
        onMessage.accept(message);
      }
      if (message instanceof ResultMessage) {
        return (ResultMessage) message;
      }
    }
    throw new AgentException("Agent CLI ended before a result" + exitSuffix());
  }

  private Iterable<Message> iterate(final boolean untilResult) {
    return new Iterable<Message>() {
      @Override
      public Iterator<Message> iterator() {
        return new Iterator<Message>() {
          private Message next;
          private boolean done;

          @Override
          public boolean hasNext() {
            if (next != null) {
              return true;
            }
            if (done) {
              return false;
            }
            try {
              Object item = inbox.take();
              if (item == END) {
                inbox.offer(END);
                done = true;
                throwReadFailure();
                return false;
              }
              next = (Message) item;
              if (untilResult && next instanceof ResultMessage) {
                done = true;
              }
              return true;
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AgentException("Interrupted while waiting for the agent", e);
            }
          }

          @Override
          public Message next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            Message message = next;
            next = null;
            return message;
          }
        };
      }
    };
  }

  /** Stops the running turn; the CLI then reports an error result for it. */
  public void interrupt() {
    control(JsonMaps.of("subtype", "interrupt"));
  }

  public void setPermissionMode(PermissionMode mode) {
    control(JsonMaps.of("subtype", "set_permission_mode", "mode", mode.value()));
  }

  /** Switches the model for later turns; requires an idle session. */
  public void setModel(String model) {
    control(JsonMaps.of("subtype", "set_model", "model", model));
  }

  /** Sets the thinking budget; null restores the default, 0 turns thinking off. */
  public void setMaxThinkingTokens(Integer tokens) {
    control(JsonMaps.of("subtype", "set_max_thinking_tokens", "max_thinking_tokens", tokens));
  }

  public List<Map<String, Object>> mcpStatus() {
    return JsonMaps.objects(control(JsonMaps.of("subtype", "mcp_status")), "mcpServers");
  }

  public void mcpToggle(String serverName, boolean enabled) {
    control(JsonMaps.of("subtype", "mcp_toggle", "serverName", serverName, "enabled", Boolean.valueOf(enabled)));
  }

  public void mcpReconnect(String serverName) {
    control(JsonMaps.of("subtype", "mcp_reconnect", "serverName", serverName));
  }

  /** Context window use by category: totalTokens, maxTokens, percentage, categories. */
  public Map<String, Object> getContextUsage() {
    return control(JsonMaps.of("subtype", "get_context_usage"));
  }

  /** Effective merged settings, each source layer, and the applied model and effort. */
  public Map<String, Object> getSettings() {
    return control(JsonMaps.of("subtype", "get_settings"));
  }

  public void applyFlagSettings(Map<String, Object> settings) {
    control(JsonMaps.of("subtype", "apply_flag_settings", "settings", settings));
  }

  /**
   * Restores files changed since the given user message.
   *
   * @return canRewind, error, filesChanged, insertions, deletions
   */
  public Map<String, Object> rewindFiles(String userMessageUuid, boolean dryRun) {
    return control(JsonMaps.of("subtype", "rewind_files", "user_message_id", userMessageUuid, "dry_run",
        Boolean.valueOf(dryRun)));
  }

  /** Drops a queued prompt that has not started yet; false when it already ran. */
  public boolean cancelQueuedMessage(String messageUuid) {
    return JsonMaps.bool(control(JsonMaps.of("subtype", "cancel_async_message", "message_uuid", messageUuid)),
        "cancelled");
  }

  public void stopTask(String taskId) {
    control(JsonMaps.of("subtype", "stop_task", "task_id", taskId));
  }

  /** Reloads plugins and returns the refreshed commands, agents, plugins and MCP servers. */
  public Map<String, Object> reloadPlugins() {
    return control(JsonMaps.of("subtype", "reload_plugins"));
  }

  /** Sends any control request and returns its response object. */
  public Map<String, Object> control(Map<String, Object> request) {
    requireConnected();
    String id = "req_" + counter.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 8);
    CompletableFuture<Map<String, Object>> answer = new CompletableFuture<Map<String, Object>>();
    pending.put(id, answer);
    try {
      write(JsonMaps.of("type", "control_request", "request_id", id, "request", request));
      return answer.get(options.getControlTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new AgentException("Control request " + request.get("subtype") + " timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AgentException("Interrupted while waiting for " + request.get("subtype"), e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof AgentException) {
        throw new AgentException(cause.getMessage(), cause);
      }
      throw new AgentException(String.valueOf(cause), cause);
    } finally {
      pending.remove(id);
    }
  }

  /** Closes the CLI's input; queued prompts still finish before it exits. */
  public void endInput() {
    transport.endInput();
  }

  /** The CLI's exit code once it has ended, else null. */
  public synchronized Integer getExitCode() {
    return exitCode;
  }

  /** Ends input, waits briefly for the CLI to exit, then stops it. */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    transport.endInput();
    try {
      exitCode = transport.waitFor(10_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    transport.close();
    workers.shutdownNow();
  }

  private Map<String, Object> initializeRequest() {
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("subtype", "initialize");
    Map<HookEvent, List<HookMatcher>> hooks = options.getHooks();
    if (!hooks.isEmpty()) {
      Map<String, Object> config = new LinkedHashMap<String, Object>();
      for (Map.Entry<HookEvent, List<HookMatcher>> entry : hooks.entrySet()) {
        List<Object> matchers = new ArrayList<Object>();
        for (HookMatcher matcher : entry.getValue()) {
          List<String> ids = new ArrayList<String>();
          for (HookCallback callback : matcher.getCallbacks()) {
            String id = "hook_" + hookCallbacks.size();
            hookCallbacks.put(id, callback);
            ids.add(id);
          }
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          if (matcher.getMatcher() != null) {
            item.put("matcher", matcher.getMatcher());
          }
          item.put("hookCallbackIds", ids);
          if (matcher.getTimeoutSeconds() != null) {
            item.put("timeout", matcher.getTimeoutSeconds());
          }
          matchers.add(item);
        }
        config.put(entry.getKey().name(), matchers);
      }
      request.put("hooks", config);
    }
    if (!mcpServers.isEmpty()) {
      request.put("sdkMcpServers", new ArrayList<String>(mcpServers.keySet()));
    }
    if (!options.getAgents().isEmpty()) {
      request.put("agents", options.getAgents());
    }
    request.putAll(options.getInitializeFields());
    return request;
  }

  private void readLoop() {
    try {
      String line;
      while ((line = transport.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        Map<String, Object> frame;
        try {
          frame = Json.parseObject(line);
        } catch (RuntimeException e) {
          Consumer<String> stderr = options.getStderr();
          if (stderr != null) {
            stderr.accept("[non-JSON stdout] " + line);
          }
          continue;
        }
        dispatch(frame);
      }
    } catch (IOException e) {
      if (!closed) {
        readFailure = e;
      }
    } catch (RuntimeException e) {
      readFailure = e;
    } finally {
      AgentException ended = new AgentException("Agent CLI output ended" + exitSuffix());
      for (CompletableFuture<Map<String, Object>> answer : pending.values()) {
        answer.completeExceptionally(ended);
      }
      inbox.offer(END);
    }
  }

  private void dispatch(final Map<String, Object> frame) {
    String type = JsonMaps.string(frame, "type");
    if ("control_response".equals(type)) {
      Map<String, Object> response = JsonMaps.object(frame, "response");
      CompletableFuture<Map<String, Object>> answer = pending.get(JsonMaps.string(response, "request_id"));
      if (answer != null) {
        if ("success".equals(JsonMaps.string(response, "subtype"))) {
          Map<String, Object> body = JsonMaps.object(response, "response");
          answer.complete(body == null ? new LinkedHashMap<String, Object>() : body);
        } else {
          answer.completeExceptionally(new AgentException(String.valueOf(response.get("error"))));
        }
      }
      return;
    }
    if ("control_request".equals(type)) {
      final String id = JsonMaps.string(frame, "request_id");
      if (id == null) {
        return;
      }
      FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
        @Override
        public void run() {
          answerIncoming(id, JsonMaps.object(frame, "request"));
        }
      }, null);
      // Registered before it runs, so a fast answer is never mistaken for a cancelled one.
      inbound.put(id, task);
      workers.execute(task);
      return;
    }
    if ("control_cancel_request".equals(type)) {
      Future<?> task = inbound.remove(JsonMaps.string(frame, "request_id"));
      if (task != null) {
        task.cancel(true);
      }
      return;
    }
    if ("keep_alive".equals(type)) {
      return;
    }
    Message message = Messages.parse(frame);
    if (message instanceof RawMessage && !options.isIncludeRawFrames()) {
      return;
    }
    inbox.offer(message);
  }

  private void answerIncoming(String id, Map<String, Object> request) {
    Map<String, Object> response;
    try {
      Map<String, Object> body = handleIncoming(request);
      response = JsonMaps.of("subtype", "success", "request_id", id, "response", body);
    } catch (Exception e) {
      response = JsonMaps.of("subtype", "error", "request_id", id, "error",
          e.getMessage() == null ? e.toString() : e.getMessage());
    }
    if (inbound.remove(id) == null) {
      // The CLI cancelled this request; it no longer expects an answer.
      return;
    }
    try {
      write(JsonMaps.of("type", "control_response", "response", response));
    } catch (AgentException ignored) {
      // Input already closed: the CLI has ended this run.
    }
  }

  private Map<String, Object> handleIncoming(Map<String, Object> request) throws Exception {
    String subtype = JsonMaps.string(request, "subtype");
    if ("can_use_tool".equals(subtype)) {
      Map<String, Object> input = JsonMaps.object(request, "input");
      if (input == null) {
        input = new LinkedHashMap<String, Object>();
      }
      PermissionResult result = options.getCanUseTool() == null
          ? PermissionResult.deny("No permission handler is configured in the SDK client")
          : options.getCanUseTool().check(JsonMaps.string(request, "tool_name"), input,
              new ToolPermissionContext(request));
      if (result == null) {
        throw new AgentException("Permission handler returned no decision");
      }
      return result.toResponse(input);
    }
    if ("hook_callback".equals(subtype)) {
      String callbackId = JsonMaps.string(request, "callback_id");
      HookCallback callback = hookCallbacks.get(callbackId);
      if (callback == null) {
        throw new AgentException("Unknown hook callback: " + callbackId);
      }
      Map<String, Object> input = JsonMaps.object(request, "input");
      Map<String, Object> output = callback.call(input == null ? new LinkedHashMap<String, Object>() : input,
          JsonMaps.string(request, "tool_use_id"));
      return output == null ? new LinkedHashMap<String, Object>() : output;
    }
    if ("mcp_message".equals(subtype)) {
      String name = JsonMaps.string(request, "server_name");
      SdkMcpServer server = mcpServers.get(name);
      if (server == null) {
        throw new AgentException("Unknown SDK MCP server: " + name);
      }
      Map<String, Object> message = JsonMaps.object(request, "message");
      Map<String, Object> reply = server.handle(message == null ? new LinkedHashMap<String, Object>() : message);
      // Notifications have no JSON-RPC answer, but the envelope is still required.
      return JsonMaps.of("mcp_response", reply != null ? reply
          : JsonMaps.of("jsonrpc", "2.0", "result", Collections.emptyMap()));
    }
    if ("elicitation".equals(subtype)) {
      if (options.getElicitationHandler() == null) {
        return JsonMaps.of("action", "cancel");
      }
      Map<String, Object> answer = options.getElicitationHandler().elicit(request);
      return answer == null ? JsonMaps.of("action", "cancel") : answer;
    }
    throw new AgentException("Unsupported control request from CLI: " + subtype);
  }

  private void write(Map<String, Object> frame) {
    try {
      transport.write(Json.stringify(frame));
    } catch (IOException e) {
      throw new AgentException("Cannot write to agent CLI: " + e.getMessage() + exitSuffix(), e);
    }
  }

  private void requireConnected() {
    if (!connected) {
      throw new AgentException("Call connect() first");
    }
    if (closed) {
      throw new AgentException("Client is closed");
    }
  }

  private void throwReadFailure() {
    Throwable failure = readFailure;
    if (failure != null) {
      throw new AgentException("Reading agent output failed: " + failure.getMessage(), failure);
    }
  }

  private String exitSuffix() {
    try {
      Integer code = transport.waitFor(0);
      return code == null ? "" : " (exit code " + code + ")";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "";
    }
  }
}
