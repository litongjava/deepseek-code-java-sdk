package nexus.io.agent.sdk.permission;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** Details the CLI sent with a permission request. */
public class ToolPermissionContext {
  private final Map<String, Object> request;

  public ToolPermissionContext(Map<String, Object> request) {
    this.request = Collections.unmodifiableMap(request);
  }

  public String getToolUseId() {
    return JsonMaps.string(request, "tool_use_id");
  }

  /** Permission updates the CLI suggests, to pass back with {@link PermissionResult#allow}. */
  public List<Map<String, Object>> getSuggestions() {
    return JsonMaps.objects(request, "permission_suggestions");
  }

  /** The path outside the allowed directories, when that caused the request. */
  public String getBlockedPath() {
    return JsonMaps.string(request, "blocked_path");
  }

  public Map<String, Object> getRequest() {
    return request;
  }
}
