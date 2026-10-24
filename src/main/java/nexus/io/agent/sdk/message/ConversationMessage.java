package nexus.io.agent.sdk.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** Shared parts of assistant and user messages. */
public abstract class ConversationMessage extends Message {
  private final List<ContentBlock> content;

  protected ConversationMessage(Map<String, Object> raw) {
    super(raw);
    Map<String, Object> message = JsonMaps.object(raw, "message");
    List<ContentBlock> blocks = new ArrayList<ContentBlock>();
    Object value = message == null ? null : message.get("content");
    if (value instanceof String) {
      blocks.add(ContentBlock.parse(JsonMaps.of("type", "text", "text", value)));
    } else {
      for (Map<String, Object> block : JsonMaps.objects(message, "content")) {
        blocks.add(ContentBlock.parse(block));
      }
    }
    this.content = Collections.unmodifiableList(blocks);
  }

  public List<ContentBlock> getContent() {
    return content;
  }

  /** The tool use that spawned this message when it comes from a subagent. */
  public String getParentToolUseId() {
    return JsonMaps.string(getRaw(), "parent_tool_use_id");
  }

  /** All text blocks joined in order. */
  public String getText() {
    StringBuilder text = new StringBuilder();
    for (ContentBlock block : content) {
      if (block instanceof TextBlock) {
        text.append(((TextBlock) block).getText());
      }
    }
    return text.toString();
  }
}
