package nexus.io.agent.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import nexus.io.agent.sdk.hook.HookEvent;
import nexus.io.agent.sdk.hook.HookMatcher;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.mcp.SdkTool;
import nexus.io.agent.sdk.mcp.ToolResult;
import nexus.io.agent.sdk.message.AssistantMessage;
import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.ResultMessage;
import nexus.io.agent.sdk.message.SystemMessage;
import nexus.io.agent.sdk.message.ToolUseBlock;
import nexus.io.agent.sdk.permission.PermissionResult;

public class AgentClientProtocolTest {

  private static Map<String, Object> success(Map<String, Object> request, Map<String, Object> body) {
    return JsonMaps.of("type", "control_response", "response",
        JsonMaps.of("subtype", "success", "request_id", request.get("request_id"), "response", body));
  }

  private static String subtype(Map<String, Object> frame) {
    return JsonMaps.string(JsonMaps.object(frame, "request"), "subtype");
  }

  @Test
  public void initializeCarriesHooksAgentsAndSdkServersThenStreamsTypedMessages() {
    ScriptedTransport transport = new ScriptedTransport((cli, frame) -> {
      if ("initialize".equals(subtype(frame))) {
        cli.emit(success(frame, JsonMaps.of("commands", Arrays.asList(), "models", Arrays.asList())));
      } else if ("user".equals(frame.get("type"))) {
        cli.emitLine("not json");
        cli.emit(JsonMaps.of("type", "system", "subtype", "init", "session_id", "s1", "tools", Arrays.asList("Read")));
        cli.emit(JsonMaps.of("type", "text", "delta", "legacy event"));
        cli.emit(JsonMaps.of("type", "assistant", "session_id", "s1", "message", JsonMaps.of("id", "m1", "content",
            Arrays.asList(JsonMaps.of("type", "tool_use", "id", "t1", "name", "Read", "input", JsonMaps.of("file_path", "a"))))));
        cli.emit(JsonMaps.of("type", "result", "subtype", "success", "result", "done", "num_turns", 2L,
            "total_cost_usd", 0.5, "session_id", "s1"));
      }
    });
    List<String> stderr = new ArrayList<String>();
    AgentOptions options = new AgentOptions().agent("reviewer", new AgentDefinition("Reviews code", "Review."))
        .hook(HookEvent.PreToolUse, HookMatcher.of("Bash", (input, id) -> JsonMaps.of()))
        .mcpServer(SdkMcpServer.of("calc")).initializeField("promptSuggestions", true).stderr(stderr::add);
    try (AgentClient client = new AgentClient(options, transport)) {
      client.connect();
      Map<String, Object> initialize = JsonMaps.object(transport.written.get(0), "request");
      assertEquals(Arrays.asList("calc"), initialize.get("sdkMcpServers"));
      assertEquals(Boolean.TRUE, initialize.get("promptSuggestions"));
      assertEquals("Reviews code", JsonMaps.string(JsonMaps.object(JsonMaps.object(initialize, "agents"), "reviewer"), "description"));
      Map<String, Object> hook = JsonMaps.objects(JsonMaps.object(initialize, "hooks"), "PreToolUse").get(0);
      assertEquals("Bash", hook.get("matcher"));
      assertEquals(Arrays.asList("hook_0"), hook.get("hookCallbackIds"));
      assertTrue(client.getServerInfo().containsKey("commands"));

      String uuid = client.query("hello");
      Map<String, Object> user = transport.written.get(1);
      assertEquals(uuid, user.get("uuid"));
      assertFalse("no session id unless requested", user.containsKey("session_id"));
      List<Message> messages = new ArrayList<Message>();
      for (Message message : client.receiveResponse()) {
        messages.add(message);
      }
      assertEquals(3, messages.size());
      assertTrue(((SystemMessage) messages.get(0)).isInit());
      ToolUseBlock use = ((AssistantMessage) messages.get(1)).getToolUses().get(0);
      assertEquals("Read", use.getName());
      assertEquals("a", use.getInput().get("file_path"));
      ResultMessage result = (ResultMessage) messages.get(2);
      assertEquals("done", result.getResult());
      assertEquals(2, result.getNumTurns());
      assertEquals(Double.valueOf(0.5), result.getTotalCostUsd());
      assertEquals(Arrays.asList("[non-JSON stdout] not json"), stderr);
    }
  }

  @Test
  public void answersPermissionHookMcpAndElicitationRequestsFromTheCli() throws Exception {
    final CountDownLatch answered = new CountDownLatch(4);
    final List<Map<String, Object>> responses = new ArrayList<Map<String, Object>>();
    ScriptedTransport transport = new ScriptedTransport((cli, frame) -> {
      if ("initialize".equals(subtype(frame))) {
        cli.emit(success(frame, JsonMaps.of()));
        cli.emit(JsonMaps.of("type", "control_request", "request_id", "p1", "request", JsonMaps.of("subtype",
            "can_use_tool", "tool_name", "Write", "input", JsonMaps.of("file_path", "x"), "tool_use_id", "w1")));
        cli.emit(JsonMaps.of("type", "control_request", "request_id", "h1", "request", JsonMaps.of("subtype",
            "hook_callback", "callback_id", "hook_0", "input", JsonMaps.of("tool_name", "Bash"), "tool_use_id", "b1")));
        cli.emit(JsonMaps.of("type", "control_request", "request_id", "m1", "request", JsonMaps.of("subtype",
            "mcp_message", "server_name", "calc", "message", JsonMaps.of("jsonrpc", "2.0", "method", "notifications/initialized"))));
        cli.emit(JsonMaps.of("type", "control_request", "request_id", "e1", "request", JsonMaps.of("subtype", "elicitation")));
      } else if ("control_response".equals(frame.get("type"))) {
        synchronized (responses) {
          responses.add(JsonMaps.object(frame, "response"));
        }
        answered.countDown();
      }
    });
    AgentOptions options = new AgentOptions()
        .canUseTool((tool, input, context) -> "w1".equals(context.getToolUseId())
            ? PermissionResult.allow(JsonMaps.of("file_path", "y"))
            : PermissionResult.deny("unexpected"))
        .hook(HookEvent.PreToolUse, HookMatcher.all((input, id) -> JsonMaps.of("decision", "block", "reason", id)))
        .mcpServer(SdkMcpServer.of("calc", new SdkTool("add", "", null, arguments -> ToolResult.text("0"))));
    try (AgentClient client = new AgentClient(options, transport)) {
      client.connect();
      assertTrue(answered.await(5, TimeUnit.SECONDS));
    }
    Map<String, Map<String, Object>> byId = new java.util.HashMap<String, Map<String, Object>>();
    for (Map<String, Object> response : responses) {
      byId.put(JsonMaps.string(response, "request_id"), JsonMaps.object(response, "response"));
    }
    assertEquals(JsonMaps.of("behavior", "allow", "updatedInput", JsonMaps.of("file_path", "y")), byId.get("p1"));
    assertEquals(JsonMaps.of("decision", "block", "reason", "b1"), byId.get("h1"));
    assertTrue(byId.get("m1").containsKey("mcp_response"));
    assertEquals(JsonMaps.of("action", "cancel"), byId.get("e1"));
  }

  @Test
  public void controlErrorsAndEndedOutputFailPendingCalls() {
    ScriptedTransport transport = new ScriptedTransport((cli, frame) -> {
      if ("initialize".equals(subtype(frame))) {
        cli.emit(success(frame, JsonMaps.of()));
      } else if ("set_model".equals(subtype(frame))) {
        cli.emit(JsonMaps.of("type", "control_response", "response", JsonMaps.of("subtype", "error", "request_id",
            frame.get("request_id"), "error", "Control changes require an idle session")));
      } else if ("mcp_status".equals(subtype(frame))) {
        cli.endInput();
      }
    });
    try (AgentClient client = new AgentClient(new AgentOptions(), transport)) {
      client.connect();
      try {
        client.setModel("other");
        fail("error response must throw");
      } catch (AgentException expected) {
        assertTrue(expected.getMessage().contains("idle session"));
      }
      try {
        client.mcpStatus();
        fail("ended output must fail the pending request");
      } catch (AgentException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains("ended"));
      }
      assertFalse(client.receiveMessages().iterator().hasNext());
    }
  }
}
