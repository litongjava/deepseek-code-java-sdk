package nexus.io.agent.sdk.message;

import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

public class ToolResultBlock extends ContentBlock {
  ToolResultBlock(Map<String, Object> raw) {
    super(raw);
  }

  public String getToolUseId() {
    return JsonMaps.string(getRaw(), "tool_use_id");
  }

  /** The result as text; array content is joined from its text blocks. */
  public String getContentText() {
    Object content = getRaw().get("content");
    if (content instanceof String) {
      return (String) content;
    }
    StringBuilder text = new StringBuilder();
    if (content instanceof List) {
      for (Map<String, Object> block : JsonMaps.objects(getRaw(), "content")) {
        String part = JsonMaps.string(block, "text");
        if (part != null) {
          if (text.length() > 0) {
            text.append('\n');
          }
          text.append(part);
        }
      }
    }
    return text.toString();
  }

  public boolean isError() {
    return JsonMaps.bool(getRaw(), "is_error");
  }
}
