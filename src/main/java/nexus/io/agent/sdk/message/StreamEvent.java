package nexus.io.agent.sdk.message;

import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** A partial-message event, sent only with {@code includePartialMessages}. */
public class StreamEvent extends Message {
  public StreamEvent(Map<String, Object> raw) {
    super(raw);
  }

  /** The raw model stream event, such as {@code content_block_delta}. */
  public Map<String, Object> getEvent() {
    return JsonMaps.object(getRaw(), "event");
  }

  public String getEventType() {
    return JsonMaps.string(getEvent(), "type");
  }

  /** The text of a {@code text_delta}, or null for other events. */
  public String getTextDelta() {
    Map<String, Object> delta = JsonMaps.object(getEvent(), "delta");
    return "text_delta".equals(JsonMaps.string(delta, "type")) ? JsonMaps.string(delta, "text") : null;
  }
}
