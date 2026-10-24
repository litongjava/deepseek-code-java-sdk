package nexus.io.agent.sdk;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import nexus.io.agent.sdk.hook.HookEvent;
import nexus.io.agent.sdk.hook.HookMatcher;
import nexus.io.agent.sdk.json.Json;
import nexus.io.agent.sdk.mcp.ElicitationHandler;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.permission.CanUseTool;

/**
 * How to start and configure the agent CLI. Setters return {@code this}.
 *
 * <p>The SDK is not tied to one CLI: {@link #cliPath(String)} names any
 * executable that speaks the stream-json protocol. When unset, the
 * {@code AGENT_CLI_PATH} environment variable is used.
 */
public class AgentOptions {
  public static final String CLI_PATH_ENV = "AGENT_CLI_PATH";

  private String cliPath;
  private File cwd;
  private String model;
  private PermissionMode permissionMode;
  private String systemPrompt;
  private String appendSystemPrompt;
  private final List<String> allowedTools = new ArrayList<String>();
  private final List<String> disallowedTools = new ArrayList<String>();
  private Integer maxTurns;
  private String resume;
  private boolean continueConversation;
  private boolean forkSession;
  private final List<File> addDirs = new ArrayList<File>();
  private Map<String, Object> jsonSchema;
  private boolean includePartialMessages;
  private boolean includeRawFrames;
  private CanUseTool canUseTool;
  private ElicitationHandler elicitationHandler;
  private final Map<HookEvent, List<HookMatcher>> hooks = new EnumMap<HookEvent, List<HookMatcher>>(HookEvent.class);
  private final List<SdkMcpServer> mcpServers = new ArrayList<SdkMcpServer>();
  private final Map<String, AgentDefinition> agents = new LinkedHashMap<String, AgentDefinition>();
  private final Map<String, Object> initializeFields = new LinkedHashMap<String, Object>();
  private final Map<String, String> env = new LinkedHashMap<String, String>();
  private final Map<String, String> extraArgs = new LinkedHashMap<String, String>();
  private Consumer<String> stderr;
  private long controlTimeoutMillis = 60_000;

  public AgentOptions cliPath(String cliPath) {
    this.cliPath = cliPath;
    return this;
  }

  /** Working directory of the agent; defaults to the JVM's working directory. */
  public AgentOptions cwd(File cwd) {
    this.cwd = cwd;
    return this;
  }

  public AgentOptions model(String model) {
    this.model = model;
    return this;
  }

  public AgentOptions permissionMode(PermissionMode permissionMode) {
    this.permissionMode = permissionMode;
    return this;
  }

  /** Replaces the CLI's default system prompt. */
  public AgentOptions systemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  public AgentOptions appendSystemPrompt(String appendSystemPrompt) {
    this.appendSystemPrompt = appendSystemPrompt;
    return this;
  }

  /** Permission rules that run without asking, such as {@code Read} or {@code Bash(git status)}. */
  public AgentOptions allowedTools(String... rules) {
    allowedTools.addAll(Arrays.asList(rules));
    return this;
  }

  public AgentOptions disallowedTools(String... rules) {
    disallowedTools.addAll(Arrays.asList(rules));
    return this;
  }

  public AgentOptions maxTurns(int maxTurns) {
    this.maxTurns = Integer.valueOf(maxTurns);
    return this;
  }

  /** Resumes a saved session by id. */
  public AgentOptions resume(String sessionId) {
    this.resume = sessionId;
    return this;
  }

  /** Continues the most recent session in the working directory. */
  public AgentOptions continueConversation(boolean continueConversation) {
    this.continueConversation = continueConversation;
    return this;
  }

  /** With {@link #resume(String)}, continues in a new session id. */
  public AgentOptions forkSession(boolean forkSession) {
    this.forkSession = forkSession;
    return this;
  }

  public AgentOptions addDir(File directory) {
    addDirs.add(directory);
    return this;
  }

  /** Requests a structured final answer matching this JSON schema. */
  public AgentOptions jsonSchema(Map<String, Object> jsonSchema) {
    this.jsonSchema = jsonSchema;
    return this;
  }

  /** Delivers {@code StreamEvent} partial-message frames. */
  public AgentOptions includePartialMessages(boolean includePartialMessages) {
    this.includePartialMessages = includePartialMessages;
    return this;
  }

  /** Delivers frame types this SDK does not model as {@code RawMessage}. */
  public AgentOptions includeRawFrames(boolean includeRawFrames) {
    this.includeRawFrames = includeRawFrames;
    return this;
  }

  /** Answers tool permission requests. Without it every request is denied. */
  public AgentOptions canUseTool(CanUseTool canUseTool) {
    this.canUseTool = canUseTool;
    return this;
  }

  /** Answers MCP elicitation requests; they are cancelled when unset. */
  public AgentOptions elicitationHandler(ElicitationHandler elicitationHandler) {
    this.elicitationHandler = elicitationHandler;
    return this;
  }

  public AgentOptions hook(HookEvent event, HookMatcher matcher) {
    List<HookMatcher> matchers = hooks.get(event);
    if (matchers == null) {
      matchers = new ArrayList<HookMatcher>();
      hooks.put(event, matchers);
    }
    matchers.add(matcher);
    return this;
  }

  /** Hosts an MCP server inside this JVM. */
  public AgentOptions mcpServer(SdkMcpServer server) {
    for (SdkMcpServer existing : mcpServers) {
      if (existing.getName().equals(server.getName())) {
        throw new IllegalArgumentException("Duplicate MCP server: " + server.getName());
      }
    }
    mcpServers.add(server);
    return this;
  }

  public AgentOptions agent(String name, AgentDefinition definition) {
    agents.put(name, definition);
    return this;
  }

  /** Adds a field to the {@code initialize} request, such as {@code promptSuggestions}. */
  public AgentOptions initializeField(String name, Object value) {
    initializeFields.put(name, value);
    return this;
  }

  public AgentOptions env(String name, String value) {
    env.put(name, value);
    return this;
  }

  /**
   * Passes a CLI flag the options do not model. The name has no leading dashes;
   * a null value passes the flag alone.
   */
  public AgentOptions extraArg(String flag, String value) {
    extraArgs.put(flag, value);
    return this;
  }

  /** Receives the CLI's stderr lines; they are discarded when unset. */
  public AgentOptions stderr(Consumer<String> stderr) {
    this.stderr = stderr;
    return this;
  }

  /** How long a control request may wait for its answer. */
  public AgentOptions controlTimeoutMillis(long controlTimeoutMillis) {
    this.controlTimeoutMillis = controlTimeoutMillis;
    return this;
  }

  public String resolveCliPath() {
    String path = cliPath != null && !cliPath.isEmpty() ? cliPath : System.getenv(CLI_PATH_ENV);
    if (path == null || path.isEmpty()) {
      throw new AgentException("No agent CLI configured: call cliPath(...) or set " + CLI_PATH_ENV);
    }
    return path;
  }

  /** The command line, without the executable. */
  public List<String> buildArguments() {
    List<String> args = new ArrayList<String>();
    args.add("--output-format");
    args.add("stream-json");
    args.add("--input-format");
    args.add("stream-json");
    flag(args, "--model", model);
    flag(args, "--permission-mode", permissionMode == null ? null : permissionMode.value());
    flag(args, "--system-prompt", systemPrompt);
    flag(args, "--append-system-prompt", appendSystemPrompt);
    flag(args, "--allowedTools", join(allowedTools));
    flag(args, "--disallowedTools", join(disallowedTools));
    flag(args, "--max-turns", maxTurns == null ? null : maxTurns.toString());
    flag(args, "--resume", resume);
    if (continueConversation) {
      args.add("--continue");
    }
    if (forkSession) {
      args.add("--fork-session");
    }
    for (File dir : addDirs) {
      flag(args, "--add-dir", dir.getPath());
    }
    if (jsonSchema != null) {
      flag(args, "--json-schema", Json.stringify(jsonSchema));
    }
    if (includePartialMessages) {
      args.add("--include-partial-messages");
    }
    if (canUseTool != null) {
      flag(args, "--permission-prompt-tool", "stdio");
    }
    for (Map.Entry<String, String> extra : extraArgs.entrySet()) {
      args.add("--" + extra.getKey());
      if (extra.getValue() != null) {
        args.add(extra.getValue());
      }
    }
    return args;
  }

  private static void flag(List<String> args, String name, String value) {
    if (value != null && !value.isEmpty()) {
      args.add(name);
      args.add(value);
    }
  }

  private static String join(List<String> values) {
    StringBuilder joined = new StringBuilder();
    for (String value : values) {
      if (joined.length() > 0) {
        joined.append(',');
      }
      joined.append(value);
    }
    return joined.toString();
  }

  public File getCwd() {
    return cwd;
  }

  public boolean isIncludeRawFrames() {
    return includeRawFrames;
  }

  public CanUseTool getCanUseTool() {
    return canUseTool;
  }

  public ElicitationHandler getElicitationHandler() {
    return elicitationHandler;
  }

  public Map<HookEvent, List<HookMatcher>> getHooks() {
    return Collections.unmodifiableMap(hooks);
  }

  public List<SdkMcpServer> getMcpServers() {
    return Collections.unmodifiableList(mcpServers);
  }

  public Map<String, AgentDefinition> getAgents() {
    return Collections.unmodifiableMap(agents);
  }

  public Map<String, Object> getInitializeFields() {
    return Collections.unmodifiableMap(initializeFields);
  }

  public Map<String, String> getEnv() {
    return Collections.unmodifiableMap(env);
  }

  public Consumer<String> getStderr() {
    return stderr;
  }

  public long getControlTimeoutMillis() {
    return controlTimeoutMillis;
  }
}
