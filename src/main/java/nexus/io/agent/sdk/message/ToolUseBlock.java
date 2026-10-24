package nexus.io.agent.sdk.message;

import java.util.Collections;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

public class ToolUseBlock extends ContentBlock {
  ToolUseBlock(Map<String, Object> raw) {
    super(raw);
  }

  public String getId() {
    return JsonMaps.string(getRaw(), "id");
  }

  public String getName() {
    return JsonMaps.string(getRaw(), "name");
  }

  public Map<String, Object> getInput() {
    Map<String, Object> input = JsonMaps.object(getRaw(), "input");
    return input == null ? Collections.<String, Object>emptyMap() : input;
  }
}
