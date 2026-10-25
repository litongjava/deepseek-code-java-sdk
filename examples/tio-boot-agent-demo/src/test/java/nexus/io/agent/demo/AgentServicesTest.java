package nexus.io.agent.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.jfinal.kit.Kv;

import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.demo.service.AgentMessageMapper;
import nexus.io.agent.demo.service.AgentOptionsService;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.message.Messages;
import nexus.io.tio.utils.json.JsonUtils;

public class AgentServicesTest {

  @Test
  public void extraArgsBecomeFlagValuePairs() {
    AgentOptions options = new AgentOptions();
    new AgentOptionsService().applyExtraArgs(options, " --base-url http://127.0.0.1:1  --verbose --model m ");
    List<String> args = options.buildArguments();
    assertEquals(Arrays.asList("--base-url", "http://127.0.0.1:1", "--verbose", "--model", "m"),
        args.subList(args.size() - 5, args.size()));
    try {
      new AgentOptionsService().applyExtraArgs(new AgentOptions(), "value-without-flag");
      fail("a bare value must be rejected");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void mapperCollectsToolCallsAndResult() {
    AgentMessageMapper mapper = new AgentMessageMapper();
    AgentRunResult run = new AgentRunResult();
    mapper.collect(run, Messages.parse(JsonMaps.of("type", "assistant", "message", JsonMaps.of("content",
        Arrays.asList(JsonMaps.of("type", "tool_use", "id", "t1", "name", "Read", "input", JsonMaps.of()))))));
    mapper.collect(run, Messages.parse(JsonMaps.of("type", "user", "message", JsonMaps.of("content",
        Arrays.asList(JsonMaps.of("type", "tool_result", "tool_use_id", "t1", "content", "boom", "is_error", true))))));
    mapper.collect(run, Messages.parse(JsonMaps.of("type", "result", "subtype", "success", "result", "done",
        "num_turns", 2L, "duration_ms", 1370L, "session_id", "s1")));
    Kv call = run.getToolCalls().get(0);
    assertEquals("Read", call.getStr("name"));
    assertEquals(Boolean.TRUE, call.get("isError"));
    assertEquals("done", run.getResult());
    assertEquals(2, run.getNumTurns());
    assertEquals("s1", run.getSessionId());
    assertTrue(JsonUtils.toJson(run).contains("\"durationMs\":1370"));
  }

  @Test
  public void streamEventsCarryTextDeltasOnly() {
    AgentMessageMapper mapper = new AgentMessageMapper();
    Kv text = mapper.toEvent(Messages.parse(JsonMaps.of("type", "stream_event", "event",
        JsonMaps.of("type", "content_block_delta", "delta", JsonMaps.of("type", "text_delta", "text", "hi")))));
    assertEquals("hi", text.getStr("delta"));
    assertNull(mapper.toEvent(Messages.parse(JsonMaps.of("type", "stream_event", "event",
        JsonMaps.of("type", "message_stop")))));
  }
}
