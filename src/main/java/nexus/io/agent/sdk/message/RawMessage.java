package nexus.io.agent.sdk.message;

import java.util.Map;

/** A frame type this SDK does not model; delivered only when raw frames are enabled. */
public class RawMessage extends Message {
  public RawMessage(Map<String, Object> raw) {
    super(raw);
  }
}
