package nexus.io.agent.sdk.permission;

import java.util.Map;

/**
 * Decides whether the agent may run a tool. Called on an SDK worker thread;
 * blocking is allowed but the agent waits for the decision.
 */
public interface CanUseTool {
  PermissionResult check(String toolName, Map<String, Object> input, ToolPermissionContext context) throws Exception;
}
