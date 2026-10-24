package nexus.io.agent.sdk.mcp;

import java.util.Map;

/**
 * Answers an MCP server's request for user input. Return
 * {@code {"action":"accept","content":{...}}}, {@code {"action":"decline"}} or
 * {@code {"action":"cancel"}}.
 */
public interface ElicitationHandler {
  Map<String, Object> elicit(Map<String, Object> request) throws Exception;
}
