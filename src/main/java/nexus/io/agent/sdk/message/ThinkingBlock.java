package nexus.io.agent.sdk.message;

import java.util.Map;

public class ThinkingBlock extends ContentBlock {
  ThinkingBlock(Map<String, Object> raw) {
    super(raw);
  }

  public String getThinking() {
    Object text = getRaw().get("thinking");
    return text instanceof String ? (String) text : "";
  }
}
