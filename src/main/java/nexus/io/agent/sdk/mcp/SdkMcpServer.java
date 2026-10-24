package nexus.io.agent.sdk.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/**
 * An MCP server that runs inside this JVM. The CLI reaches it through
 * {@code mcp_message} control requests, so no extra process is started. Its
 * tools appear to the agent as {@code mcp__<server>__<tool>}.
 */
public final class SdkMcpServer {
  private static final String PROTOCOL_VERSION = "2025-06-18";

  private final String name;
  private final String version;
  private final Map<String, SdkTool> tools = new LinkedHashMap<String, SdkTool>();

  public SdkMcpServer(String name, String version, List<SdkTool> tools) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Server name is required");
    }
    this.name = name;
    this.version = version == null ? "1.0.0" : version;
    for (SdkTool tool : tools) {
      if (this.tools.put(tool.getName(), tool) != null) {
        throw new IllegalArgumentException("Duplicate tool: " + tool.getName());
      }
    }
  }

  public static SdkMcpServer of(String name, SdkTool... tools) {
    return new SdkMcpServer(name, null, Arrays.asList(tools));
  }

  public String getName() {
    return name;
  }

  /**
   * Answers one JSON-RPC message. Returns the JSON-RPC response, or null for a
   * notification, which has no response.
   */
  public Map<String, Object> handle(Map<String, Object> message) {
    Object id = message.get("id");
    String method = JsonMaps.string(message, "method");
    if (!message.containsKey("id")) {
      return null;
    }
    try {
      if ("initialize".equals(method)) {
        return result(id, JsonMaps.of("protocolVersion", PROTOCOL_VERSION, "capabilities",
            JsonMaps.of("tools", new LinkedHashMap<String, Object>()), "serverInfo",
            JsonMaps.of("name", name, "version", version)));
      }
      if ("tools/list".equals(method)) {
        List<Object> list = new ArrayList<Object>();
        for (SdkTool tool : tools.values()) {
          Map<String, Object> schema = tool.getInputSchema() != null ? tool.getInputSchema()
              : JsonMaps.of("type", "object", "properties", new LinkedHashMap<String, Object>());
          list.add(JsonMaps.of("name", tool.getName(), "description", tool.getDescription(), "inputSchema", schema));
        }
        return result(id, JsonMaps.of("tools", list));
      }
      if ("tools/call".equals(method)) {
        Map<String, Object> params = JsonMaps.object(message, "params");
        String toolName = JsonMaps.string(params, "name");
        SdkTool tool = toolName == null ? null : tools.get(toolName);
        if (tool == null) {
          return error(id, -32602, "Unknown tool: " + toolName);
        }
        Map<String, Object> arguments = JsonMaps.object(params, "arguments");
        ToolResult outcome;
        try {
          outcome = tool.getHandler().call(arguments == null ? new LinkedHashMap<String, Object>() : arguments);
        } catch (Exception e) {
          outcome = ToolResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return result(id, outcome == null ? ToolResult.text("").toJson() : outcome.toJson());
      }
      if ("ping".equals(method)) {
        return result(id, new LinkedHashMap<String, Object>());
      }
      return error(id, -32601, "Method not found: " + method);
    } catch (RuntimeException e) {
      return error(id, -32603, e.toString());
    }
  }

  private static Map<String, Object> result(Object id, Object result) {
    return JsonMaps.of("jsonrpc", "2.0", "id", id, "result", result);
  }

  private static Map<String, Object> error(Object id, int code, String message) {
    return JsonMaps.of("jsonrpc", "2.0", "id", id, "error", JsonMaps.of("code", code, "message", message));
  }
}
