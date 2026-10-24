package nexus.io.agent.sdk.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** A completed assistant content block (text, thinking or tool use). */
public class AssistantMessage extends ConversationMessage {
  public AssistantMessage(Map<String, Object> raw) {
    super(raw);
  }

  public String getModel() {
    return JsonMaps.string(JsonMaps.object(getRaw(), "message"), "model");
  }

  /** The model message id; content blocks of one model round share it. */
  public String getMessageId() {
    return JsonMaps.string(JsonMaps.object(getRaw(), "message"), "id");
  }

  public List<ToolUseBlock> getToolUses() {
    List<ToolUseBlock> uses = new ArrayList<ToolUseBlock>();
    for (ContentBlock block : getContent()) {
      if (block instanceof ToolUseBlock) {
        uses.add((ToolUseBlock) block);
      }
    }
    return uses;
  }
}
