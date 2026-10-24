package nexus.io.agent.sdk.json;

/** Malformed JSON, or a value that JSON cannot represent. */
public class JsonException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public JsonException(String message) {
    super(message);
  }
}
