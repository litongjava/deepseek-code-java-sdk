package nexus.io.agent.sdk.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** The MCP {@code tools/call} result of an SDK tool. */
public final class ToolResult {
  private final List<Map<String, Object>> content;
  private final boolean error;

  public ToolResult(List<Map<String, Object>> content, boolean error) {
    this.content = Collections.unmodifiableList(new ArrayList<Map<String, Object>>(content));
    this.error = error;
  }

  public static ToolResult text(String text) {
    return new ToolResult(Collections.singletonList(JsonMaps.of("type", "text", "text", text)), false);
  }

  public static ToolResult error(String text) {
    return new ToolResult(Collections.singletonList(JsonMaps.of("type", "text", "text", text)), true);
  }

  public Map<String, Object> toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("content", content);
    if (error) {
      result.put("isError", Boolean.TRUE);
    }
    return result;
  }
}
