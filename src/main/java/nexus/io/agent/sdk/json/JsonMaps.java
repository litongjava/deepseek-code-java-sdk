package nexus.io.agent.sdk.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed reads from decoded JSON objects. */
public final class JsonMaps {

  private JsonMaps() {
  }

  /** Builds an ordered map from alternating keys and values. */
  public static Map<String, Object> of(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("Keys and values must come in pairs");
    }
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
    }
    return map;
  }

  public static String string(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value instanceof String ? (String) value : null;
  }

  public static boolean bool(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return Boolean.TRUE.equals(value);
  }

  public static Long longValue(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
  }

  public static Double doubleValue(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : null;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> list(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value instanceof List ? (List<Object>) value : Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> objects(Map<String, Object> map, String key) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : list(map, key)) {
      if (item instanceof Map) {
        result.add((Map<String, Object>) item);
      }
    }
    return result;
  }

  public static List<String> strings(Map<String, Object> map, String key) {
    List<String> result = new ArrayList<String>();
    for (Object item : list(map, key)) {
      if (item instanceof String) {
        result.add((String) item);
      }
    }
    return result;
  }
}
