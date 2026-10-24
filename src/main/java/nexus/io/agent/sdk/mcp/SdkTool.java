package nexus.io.agent.sdk.mcp;

import java.util.Map;

/** A tool exposed to the agent by an in-process MCP server. */
public final class SdkTool {
  private final String name;
  private final String description;
  private final Map<String, Object> inputSchema;
  private final ToolHandler handler;

  /**
   * @param inputSchema JSON schema of the arguments, for example
   *                    {@code {"type":"object","properties":{...},"required":[...]}}
   */
  public SdkTool(String name, String description, Map<String, Object> inputSchema, ToolHandler handler) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Tool name is required");
    }
    this.name = name;
    this.description = description == null ? "" : description;
    this.inputSchema = inputSchema;
    this.handler = handler;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getInputSchema() {
    return inputSchema;
  }

  public ToolHandler getHandler() {
    return handler;
  }
}
