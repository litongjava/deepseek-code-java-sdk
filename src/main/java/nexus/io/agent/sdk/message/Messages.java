package nexus.io.agent.sdk.message;

import java.util.Map;

/** Maps decoded frames onto message classes. */
public final class Messages {

  private Messages() {
  }

  /** Returns the typed message, or a {@link RawMessage} for other frame types. */
  public static Message parse(Map<String, Object> frame) {
    Object type = frame.get("type");
    if ("assistant".equals(type)) {
      return new AssistantMessage(frame);
    }
    if ("user".equals(type)) {
      return new UserMessage(frame);
    }
    if ("system".equals(type)) {
      return new SystemMessage(frame);
    }
    if ("result".equals(type)) {
      return new ResultMessage(frame);
    }
    if ("stream_event".equals(type)) {
      return new StreamEvent(frame);
    }
    return new RawMessage(frame);
  }
}
