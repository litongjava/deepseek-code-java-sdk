package nexus.io.agent.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import nexus.io.agent.sdk.hook.HookEvent;
import nexus.io.agent.sdk.hook.HookMatcher;
import nexus.io.agent.sdk.json.Json;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.mcp.SdkTool;
import nexus.io.agent.sdk.mcp.ToolResult;
import nexus.io.agent.sdk.message.AssistantMessage;
import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.ResultMessage;
import nexus.io.agent.sdk.message.SystemMessage;
import nexus.io.agent.sdk.message.ToolResultBlock;
import nexus.io.agent.sdk.message.ToolUseBlock;
import nexus.io.agent.sdk.message.UserMessage;
import nexus.io.agent.sdk.permission.PermissionResult;

/**
 * Drives a real agent CLI against an OpenAI-compatible mock model in this JVM.
 *
 * <p>Runs only when {@code AGENT_CLI_PATH} names the CLI and
 * {@code AGENT_CLI_MOCK_ARGS} holds the CLI flags that point it at a model
 * endpoint; {@code {url}} and {@code {data}} are replaced with the mock
 * server URL and a temporary data directory.
 */
public class CliEndToEndTest {
  private HttpServer server;
  private File workspace;
  private final List<Map<String, Object>> modelRequests = new CopyOnWriteArrayList<Map<String, Object>>();

  @Before
  public void start() throws IOException {
    assumeTrue(System.getenv("AGENT_CLI_PATH") != null && System.getenv("AGENT_CLI_MOCK_ARGS") != null);
    workspace = Files.createTempDirectory("agent-sdk-e2e").toFile();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = readAll(exchange.getRequestBody());
      Map<String, Object> request = Json.parseObject(new String(body, StandardCharsets.UTF_8));
      String reply;
      if (!Boolean.TRUE.equals(request.get("stream"))) {
        reply = Json.stringify(JsonMaps.of("id", "x", "object", "chat.completion", "model", "fixture-model", "choices",
            Arrays.asList(JsonMaps.of("index", 0L, "message", JsonMaps.of("role", "assistant", "content", "next"),
                "finish_reason", "stop"))));
        exchange.getResponseHeaders().add("content-type", "application/json");
      } else {
        modelRequests.add(request);
        reply = streamFor(request);
        exchange.getResponseHeaders().add("content-type", "text/event-stream");
      }
      byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      OutputStream out = exchange.getResponseBody();
      out.write(bytes);
      out.close();
    });
    server.start();
  }

  @After
  public void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) > 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /** First call the SDK MCP tool, then Write, then answer with text. */
  private static String streamFor(Map<String, Object> request) {
    int toolResults = 0;
    for (Map<String, Object> message : JsonMaps.objects(request, "messages")) {
      if ("tool".equals(message.get("role"))) {
        toolResults++;
      }
    }
    Map<String, Object> delta;
    String finish;
    if (toolResults == 0) {
      delta = toolCall("calc-1", "mcp__calc__add", JsonMaps.of("a", 2L, "b", 3L));
      finish = "tool_calls";
    } else if (toolResults == 1) {
      delta = toolCall("write-1", "Write", JsonMaps.of("file_path", "out.txt", "content", "FROM_JAVA"));
      finish = "tool_calls";
    } else {
      delta = JsonMaps.of("content", "finished");
      finish = "stop";
    }
    return "data: " + Json.stringify(JsonMaps.of("id", "x", "choices", Arrays.asList(JsonMaps.of("index", 0L, "delta",
        delta, "finish_reason", null)))) + "\n\n" + "data: "
        + Json.stringify(JsonMaps.of("choices", Arrays.asList(JsonMaps.of("index", 0L, "delta", JsonMaps.of(),
            "finish_reason", finish)), "usage", JsonMaps.of("prompt_tokens", 10L, "completion_tokens", 4L)))
        + "\n\ndata: [DONE]\n\n";
  }

  private static Map<String, Object> toolCall(String id, String name, Map<String, Object> arguments) {
    return JsonMaps.of("tool_calls", Arrays.asList(JsonMaps.of("index", 0L, "id", id, "type", "function", "function",
        JsonMaps.of("name", name, "arguments", Json.stringify(arguments)))));
  }

  private AgentOptions options(List<String> permissionChecks, List<String> hookCalls, List<Map<String, Object>> mcpCalls)
      throws IOException {
    File data = Files.createTempDirectory("agent-sdk-data").toFile();
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    List<String> extra = new ArrayList<String>(Arrays.asList(System.getenv("AGENT_CLI_MOCK_ARGS").trim().split("\\s+")));
    AgentOptions options = new AgentOptions().cwd(workspace).permissionMode(PermissionMode.DEFAULT)
        .env("AGENT_SDK_FIXTURE_KEY", "fixture-only").stderr(line -> {
        }).canUseTool((tool, input, context) -> {
          permissionChecks.add(tool);
          return PermissionResult.allow();
        }).hook(HookEvent.PreToolUse, HookMatcher.of("Write", (input, toolUseId) -> {
          hookCalls.add(String.valueOf(input.get("tool_name")) + ":" + toolUseId);
          return JsonMaps.of();
        })).mcpServer(SdkMcpServer.of("calc", new SdkTool("add", "Add two integers",
            JsonMaps.of("type", "object", "properties", JsonMaps.of("a", JsonMaps.of("type", "integer"), "b",
                JsonMaps.of("type", "integer")), "required", Arrays.asList("a", "b")),
            arguments -> {
              mcpCalls.add(arguments);
              long sum = ((Number) arguments.get("a")).longValue() + ((Number) arguments.get("b")).longValue();
              return ToolResult.text(String.valueOf(sum));
            })));
    for (int i = 0; i < extra.size(); i++) {
      String token = extra.get(i).replace("{url}", url).replace("{data}", data.getPath());
      if (!token.startsWith("--")) {
        throw new IllegalArgumentException("AGENT_CLI_MOCK_ARGS must be --flag [value] pairs: " + token);
      }
      String value = i + 1 < extra.size() && !extra.get(i + 1).startsWith("--")
          ? extra.get(++i).replace("{url}", url).replace("{data}", data.getPath())
          : null;
      options.extraArg(token.substring(2), value);
    }
    return options;
  }

  @Test
  public void runsSdkToolPermissionAndHookThroughTheRealCli() throws Exception {
    List<String> permissionChecks = new CopyOnWriteArrayList<String>();
    List<String> hookCalls = new CopyOnWriteArrayList<String>();
    List<Map<String, Object>> mcpCalls = new CopyOnWriteArrayList<Map<String, Object>>();
    try (AgentClient client = new AgentClient(options(permissionChecks, hookCalls, mcpCalls))) {
      client.connect();
      assertNotNull(client.getServerInfo().get("commands"));
      client.query("Add 2 and 3, then save the answer");
      List<Message> messages = new ArrayList<Message>();
      ResultMessage result = client.awaitResult(messages::add);

      assertTrue(result.getRaw().toString(), !result.isError());
      assertEquals("success", result.getSubtype());
      assertEquals("finished", result.getResult());
      assertEquals(3, result.getNumTurns());
      SystemMessage init = (SystemMessage) messages.get(0);
      assertTrue(init.isInit());
      assertTrue(init.getTools().toString(), init.getTools().contains("mcp__calc__add"));
      assertEquals(Collections.singletonList(JsonMaps.of("a", 2L, "b", 3L)), mcpCalls);
      assertTrue(permissionChecks.toString(), permissionChecks.contains("Write"));
      assertEquals(Collections.singletonList("Write:write-1"), hookCalls);
      assertEquals("FROM_JAVA", new String(Files.readAllBytes(new File(workspace, "out.txt").toPath()), StandardCharsets.UTF_8));

      List<String> toolNames = new ArrayList<String>();
      String calcOutput = null;
      for (Message message : messages) {
        if (message instanceof AssistantMessage) {
          for (ToolUseBlock use : ((AssistantMessage) message).getToolUses()) {
            toolNames.add(use.getName());
          }
        }
        if (message instanceof UserMessage && calcOutput == null) {
          calcOutput = ((ToolResultBlock) ((UserMessage) message).getContent().get(0))
              .getContentText();
        }
      }
      assertEquals(Arrays.asList("mcp__calc__add", "Write"), toolNames);
      assertTrue(calcOutput, calcOutput.contains("5"));

      boolean calcListed = false;
      for (Map<String, Object> status : client.mcpStatus()) {
        calcListed |= "calc".equals(status.get("name")) && "connected".equals(status.get("status"));
      }
      assertTrue(calcListed);
      assertEquals(Boolean.TRUE, client.getContextUsage().get("measured"));
      client.setPermissionMode(PermissionMode.PLAN);
      assertEquals("fixture-model", JsonMaps.string(JsonMaps.object(client.getSettings(), "applied"), "model"));
      client.setMaxThinkingTokens(Integer.valueOf(0));
      assertEquals(Boolean.FALSE, Boolean.valueOf(client.cancelQueuedMessage("not-queued")));
    }
  }

  @Test
  public void oneShotQueryReturnsTheResult() throws Exception {
    List<String> permissionChecks = new CopyOnWriteArrayList<String>();
    ResultMessage result = Agent.query("Add 2 and 3, then save the answer",
        options(permissionChecks, new CopyOnWriteArrayList<String>(), new CopyOnWriteArrayList<Map<String, Object>>()),
        null);
    assertEquals("finished", result.getResult());
    assertEquals(3, modelRequests.size());
  }
}
