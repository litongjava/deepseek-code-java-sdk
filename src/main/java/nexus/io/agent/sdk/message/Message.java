package nexus.io.agent.sdk.message;

import java.util.Collections;
import java.util.Map;

/** One message read from the CLI's stream-json output. */
public abstract class Message {
  private final Map<String, Object> raw;

  protected Message(Map<String, Object> raw) {
    this.raw = Collections.unmodifiableMap(raw);
  }

  /** The {@code type} field, such as {@code assistant} or {@code result}. */
  public String getType() {
    Object type = raw.get("type");
    return type instanceof String ? (String) type : "";
  }

  public String getSessionId() {
    Object id = raw.get("session_id");
    return id instanceof String ? (String) id : null;
  }

  public String getUuid() {
    Object id = raw.get("uuid");
    return id instanceof String ? (String) id : null;
  }

  /** The decoded frame, including fields this SDK does not model. */
  public Map<String, Object> getRaw() {
    return raw;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + raw;
  }
}
