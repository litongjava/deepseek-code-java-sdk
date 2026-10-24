package nexus.io.agent.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.mcp.SdkTool;
import nexus.io.agent.sdk.mcp.ToolResult;

public class SdkMcpServerTest {
  private final SdkMcpServer server = SdkMcpServer.of("calc",
      new SdkTool("add", "Add two numbers", JsonMaps.of("type", "object"), arguments -> ToolResult
          .text(String.valueOf(((Number) arguments.get("a")).longValue() + ((Number) arguments.get("b")).longValue()))),
      new SdkTool("fail", "Always fails", null, arguments -> {
        throw new IllegalStateException("boom");
      }));

  @Test
  public void answersInitializeListAndCall() {
    Map<String, Object> init = server.handle(JsonMaps.of("jsonrpc", "2.0", "id", "1", "method", "initialize"));
    assertEquals("calc", JsonMaps.string(JsonMaps.object(JsonMaps.object(init, "result"), "serverInfo"), "name"));
    List<Map<String, Object>> tools = JsonMaps.objects(JsonMaps.object(
        server.handle(JsonMaps.of("jsonrpc", "2.0", "id", 2L, "method", "tools/list")), "result"), "tools");
    assertEquals(2, tools.size());
    Map<String, Object> call = server.handle(JsonMaps.of("jsonrpc", "2.0", "id", 3L, "method", "tools/call", "params",
        JsonMaps.of("name", "add", "arguments", JsonMaps.of("a", 2L, "b", 3L))));
    assertEquals(3L, call.get("id"));
    assertEquals("5", JsonMaps.string(JsonMaps.objects(JsonMaps.object(call, "result"), "content").get(0), "text"));
  }

  @Test
  public void reportsToolFailuresAsResultsAndUnknownToolsAsErrors() {
    Map<String, Object> failed = JsonMaps.object(server.handle(JsonMaps.of("jsonrpc", "2.0", "id", 4L, "method",
        "tools/call", "params", JsonMaps.of("name", "fail"))), "result");
    assertEquals(Boolean.TRUE, failed.get("isError"));
    Map<String, Object> unknown = server.handle(JsonMaps.of("jsonrpc", "2.0", "id", 5L, "method", "tools/call",
        "params", JsonMaps.of("name", "nope")));
    assertEquals(Long.valueOf(-32602), JsonMaps.longValue(JsonMaps.object(unknown, "error"), "code"));
    assertNull(server.handle(JsonMaps.of("jsonrpc", "2.0", "method", "notifications/initialized")));
  }
}
