package nexus.io.agent.demo.service;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nexus.io.agent.sdk.permission.CanUseTool;
import nexus.io.agent.sdk.permission.PermissionResult;
import nexus.io.agent.sdk.permission.ToolPermissionContext;
import nexus.io.tio.utils.environment.EnvUtils;

/** Server-side tool policy: tools listed in {@code agent.denied.tools} are refused. */
public class AgentPermissionService implements CanUseTool {
  private static final Logger log = LoggerFactory.getLogger(AgentPermissionService.class);

  public Set<String> deniedTools() {
    Set<String> denied = new TreeSet<String>();
    for (String tool : EnvUtils.getStr("agent.denied.tools", "Bash").split(",")) {
      if (!tool.trim().isEmpty()) {
        denied.add(tool.trim());
      }
    }
    return denied;
  }

  @Override
  public PermissionResult check(String toolName, Map<String, Object> input, ToolPermissionContext context) {
    if (deniedTools().contains(toolName)) {
      log.info("Denied tool {} ({})", toolName, context.getToolUseId());
      return PermissionResult.deny("Tool " + toolName + " is disabled on this server");
    }
    log.info("Allowed tool {} ({})", toolName, context.getToolUseId());
    return PermissionResult.allow();
  }
}
