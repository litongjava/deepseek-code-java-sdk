package nexus.io.agent.sdk.message;

import java.util.Collections;
import java.util.Map;

/** A content block inside an assistant or user message. */
public class ContentBlock {
  private final Map<String, Object> raw;

  protected ContentBlock(Map<String, Object> raw) {
    this.raw = Collections.unmodifiableMap(raw);
  }

  public String getType() {
    Object type = raw.get("type");
    return type instanceof String ? (String) type : "";
  }

  public Map<String, Object> getRaw() {
    return raw;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + raw;
  }

  static ContentBlock parse(Map<String, Object> raw) {
    String type = String.valueOf(raw.get("type"));
    if ("text".equals(type)) {
      return new TextBlock(raw);
    }
    if ("thinking".equals(type)) {
      return new ThinkingBlock(raw);
    }
    if ("tool_use".equals(type)) {
      return new ToolUseBlock(raw);
    }
    if ("tool_result".equals(type)) {
      return new ToolResultBlock(raw);
    }
    return new ContentBlock(raw);
  }
}
