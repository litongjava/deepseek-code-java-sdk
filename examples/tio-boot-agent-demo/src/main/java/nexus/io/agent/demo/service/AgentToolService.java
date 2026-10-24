package nexus.io.agent.demo.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.mcp.SdkTool;
import nexus.io.agent.sdk.mcp.ToolResult;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * Tools this server exposes to the agent through an in-process MCP server.
 * The agent calls them as {@code mcp__app__server_time} and {@code mcp__app__app_env}.
 */
public class AgentToolService {
  public static final String SERVER_NAME = "app";

  private final SdkMcpServer server = SdkMcpServer.of(SERVER_NAME,
      new SdkTool("server_time", "Current date and time on the application server",
          JsonMaps.of("type", "object", "properties", new LinkedHashMap<String, Object>()),
          arguments -> ToolResult.text(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))),
      new SdkTool("app_env", "Deployment environment of the application (dev, test or prod)",
          JsonMaps.of("type", "object", "properties", new LinkedHashMap<String, Object>()),
          arguments -> ToolResult.text(String.valueOf(EnvUtils.appEnv()))));

  public SdkMcpServer server() {
    return server;
  }
}
