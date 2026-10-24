package nexus.io.agent.demo.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jfinal.kit.Kv;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.demo.utils.ParameterValidationUtils;
import nexus.io.agent.sdk.AgentClient;
import nexus.io.agent.sdk.message.Message;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.utils.environment.EnvUtils;

/** One-shot agent runs: start the CLI, run one prompt, stop the CLI. */
public class AgentQueryService {
  private static final Logger log = LoggerFactory.getLogger(AgentQueryService.class);
  private static final int MAX_PROMPT = 100_000;

  private final ExecutorService streams = Executors.newCachedThreadPool();
  private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

  public void validate(AgentQueryRequest input, boolean promptRequired) {
    ParameterValidationUtils.required(input, "Request body must be a JSON object");
    input.setPrompt(ParameterValidationUtils.text(input.getPrompt(), "prompt", MAX_PROMPT, promptRequired));
    input.setModel(ParameterValidationUtils.text(input.getModel(), "model", 200, false));
    input.setResume(ParameterValidationUtils.text(input.getResume(), "resume", 200, false));
    if (input.getPermissionMode() != null) {
      ParameterValidationUtils.choice(input.getPermissionMode(), "permissionMode", null, "default", "acceptEdits",
          "plan", "bypassPermissions", "dontAsk");
    }
  }

  public AgentRunResult query(AgentQueryRequest input) {
    return run(input, false, null);
  }

  /** Runs on a background thread; each client-facing event goes to {@code sink}, the last one is "result". */
  public void stream(AgentQueryRequest input, Consumer<Kv> sink, Runnable onClose) {
    streams.execute(() -> {
      try {
        AgentRunResult result = run(input, true, sink);
        sink.accept(Kv.by("event", "result").set("data", result));
      } catch (RuntimeException e) {
        log.warn("Agent stream failed", e);
        sink.accept(Kv.by("event", "error").set("message", e.getMessage()));
      } finally {
        onClose.run();
      }
    });
  }

  private AgentRunResult run(AgentQueryRequest input, boolean partialMessages, Consumer<Kv> sink) {
    AgentMessageMapper mapper = Aop.get(AgentMessageMapper.class);
    AgentRunResult result = new AgentRunResult();
    try (AgentClient client = new AgentClient(Aop.get(AgentOptionsService.class).build(input, partialMessages))) {
      client.connect();
      ScheduledFuture<?> timeout = scheduleInterrupt(client);
      try {
        client.query(input.getPrompt());
        client.awaitResult((Message message) -> {
          mapper.collect(result, message);
          if (sink != null) {
            Kv event = mapper.toEvent(message);
            if (event != null) {
              sink.accept(event);
            }
          }
        });
      } finally {
        timeout.cancel(false);
      }
    }
    return result;
  }

  /** Interrupts a turn that runs longer than {@code agent.query.timeout.seconds}. */
  public ScheduledFuture<?> scheduleInterrupt(AgentClient client) {
    int seconds = EnvUtils.getInt("agent.query.timeout.seconds", 600);
    return watchdog.schedule(() -> {
      try {
        log.warn("Agent turn exceeded {}s; interrupting", seconds);
        client.interrupt();
      } catch (RuntimeException e) {
        log.warn("Interrupt failed: {}", e.getMessage());
      }
    }, seconds, TimeUnit.SECONDS);
  }
}
