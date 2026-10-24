package nexus.io.agent.sdk.message;

import java.util.Map;

/** A user message, such as tool results or a replayed prompt. */
public class UserMessage extends ConversationMessage {
  public UserMessage(Map<String, Object> raw) {
    super(raw);
  }
}
