package nexus.io.agent.sdk.message;

import java.util.Map;

public class TextBlock extends ContentBlock {
  TextBlock(Map<String, Object> raw) {
    super(raw);
  }

  public String getText() {
    Object text = getRaw().get("text");
    return text instanceof String ? (String) text : "";
  }
}
