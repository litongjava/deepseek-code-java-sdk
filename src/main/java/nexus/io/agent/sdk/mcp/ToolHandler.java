package nexus.io.agent.sdk.mcp;

import java.util.Map;

/** Runs an SDK-hosted MCP tool. */
public interface ToolHandler {
  ToolResult call(Map<String, Object> arguments) throws Exception;
}
