package nexus.io.agent.sdk.permission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonValue;

/** A tool permission decision. */
public final class PermissionResult implements JsonValue {
  private final boolean allowed;
  private final Map<String, Object> updatedInput;
  private final List<Map<String, Object>> updatedPermissions;
  private final String message;
  private final boolean interrupt;

  private PermissionResult(boolean allowed, Map<String, Object> updatedInput, List<Map<String, Object>> updatedPermissions,
      String message, boolean interrupt) {
    this.allowed = allowed;
    this.updatedInput = updatedInput;
    this.updatedPermissions = updatedPermissions;
    this.message = message;
    this.interrupt = interrupt;
  }

  /** Allows the tool with its original input. */
  public static PermissionResult allow() {
    return new PermissionResult(true, null, null, null, false);
  }

  /** Allows the tool with a replacement input. */
  public static PermissionResult allow(Map<String, Object> updatedInput) {
    return new PermissionResult(true, updatedInput, null, null, false);
  }

  /**
   * Allows the tool and applies permission updates, for example a session rule
   * from {@link ToolPermissionContext#getSuggestions()}.
   */
  public static PermissionResult allow(Map<String, Object> updatedInput, List<Map<String, Object>> updatedPermissions) {
    return new PermissionResult(true, updatedInput, new ArrayList<Map<String, Object>>(updatedPermissions), null, false);
  }

  public static PermissionResult deny(String message) {
    return new PermissionResult(false, null, null, message, false);
  }

  /** Denies the tool and stops the whole turn. */
  public static PermissionResult denyAndInterrupt(String message) {
    return new PermissionResult(false, null, null, message, true);
  }

  public boolean isAllowed() {
    return allowed;
  }

  /** The wire form; {@code originalInput} fills in an allow without replacement input. */
  public Map<String, Object> toResponse(Map<String, Object> originalInput) {
    Map<String, Object> response = new LinkedHashMap<String, Object>();
    if (allowed) {
      response.put("behavior", "allow");
      response.put("updatedInput", updatedInput != null ? updatedInput : originalInput);
      if (updatedPermissions != null) {
        response.put("updatedPermissions", updatedPermissions);
      }
    } else {
      response.put("behavior", "deny");
      response.put("message", message == null ? "" : message);
      if (interrupt) {
        response.put("interrupt", Boolean.TRUE);
      }
    }
    return response;
  }

  @Override
  public Object toJson() {
    return toResponse(new LinkedHashMap<String, Object>());
  }
}
