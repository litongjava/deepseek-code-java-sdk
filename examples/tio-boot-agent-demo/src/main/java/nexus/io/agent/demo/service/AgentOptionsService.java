package nexus.io.agent.demo.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.PermissionMode;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.utils.environment.EnvUtils;

/** Builds SDK options from {@code agent.*} configuration and per-request overrides. */
public class AgentOptionsService {
  private static final Logger log = LoggerFactory.getLogger(AgentOptionsService.class);

  public AgentOptions build(AgentQueryRequest input, boolean partialMessages) {
    AgentOptions options = new AgentOptions()
        .cliPath(EnvUtils.getStr("agent.cli.path"))
        .permissionMode(permissionMode(input.getPermissionMode()))
        .canUseTool(Aop.get(AgentPermissionService.class))
        .mcpServer(Aop.get(AgentToolService.class).server())
        .includePartialMessages(partialMessages)
        .controlTimeoutMillis(EnvUtils.getLong("agent.control.timeout.millis", 60_000L))
        .stderr(line -> log.debug("agent stderr: {}", line));
    String workspace = EnvUtils.getStr("agent.workspace");
    if (workspace != null && !workspace.isEmpty()) {
      options.cwd(new File(workspace));
    }
    String model = input.getModel() != null ? input.getModel() : EnvUtils.getStr("agent.model");
    if (model != null && !model.isEmpty()) {
      options.model(model);
    }
    if (input.getResume() != null) {
      options.resume(input.getResume());
    }
    Integer maxTurns = EnvUtils.getInteger("agent.max.turns");
    if (maxTurns != null) {
      options.maxTurns(maxTurns);
    }
    for (String tool : EnvUtils.getStr("agent.allowed.tools", "").split(",")) {
      if (!tool.trim().isEmpty()) {
        options.allowedTools(tool.trim());
      }
    }
    applyExtraArgs(options, EnvUtils.getStr("agent.cli.args", ""));
    return options;
  }

  /** Maps the request's mode name, falling back to {@code agent.permission.mode}. */
  public PermissionMode permissionMode(String requested) {
    String name = requested != null ? requested : EnvUtils.getStr("agent.permission.mode", "default");
    for (PermissionMode mode : PermissionMode.values()) {
      if (mode.value().equals(name)) {
        return mode;
      }
    }
    throw new IllegalStateException("agent.permission.mode is invalid: " + name);
  }

  /** Parses {@code --flag value --switch} pairs from {@code agent.cli.args}. */
  public void applyExtraArgs(AgentOptions options, String args) {
    List<String> tokens = new ArrayList<String>();
    for (String token : args.trim().split("\\s+")) {
      if (!token.isEmpty()) {
        tokens.add(token);
      }
    }
    for (int i = 0; i < tokens.size(); i++) {
      String flag = tokens.get(i);
      if (!flag.startsWith("--")) {
        throw new IllegalStateException("agent.cli.args must contain --flag [value] pairs: " + flag);
      }
      String value = null;
      if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("--")) {
        value = tokens.get(++i);
      }
      options.extraArg(flag.substring(2), value);
    }
  }
}
