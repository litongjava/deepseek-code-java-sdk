package nexus.io.agent.sdk.json;

/** A value that knows its JSON form: maps, lists, strings, numbers, booleans or null. */
public interface JsonValue {
  Object toJson();
}
