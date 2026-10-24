package nexus.io.agent.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.permission.PermissionResult;
import nexus.io.agent.sdk.transport.SubprocessTransport;

public class AgentOptionsTest {

  @Test
  public void buildsProtocolFlagsOnlyForConfiguredOptions() {
    AgentOptions options = new AgentOptions().cliPath("/opt/agent").model("m").permissionMode(PermissionMode.ACCEPT_EDITS)
        .allowedTools("Read", "Bash(git status)").maxTurns(3).addDir(new File("extra"))
        .jsonSchema(JsonMaps.of("type", "object")).includePartialMessages(true)
        .canUseTool((tool, input, context) -> PermissionResult.allow()).extraArg("base-url", "http://localhost:1")
        .extraArg("verbose", null);
    List<String> command = new SubprocessTransport(options).command();
    assertEquals(Arrays.asList("/opt/agent", "--output-format", "stream-json", "--input-format", "stream-json",
        "--model", "m", "--permission-mode", "acceptEdits", "--allowedTools", "Read,Bash(git status)", "--max-turns",
        "3", "--add-dir", "extra", "--json-schema", "{\"type\":\"object\"}", "--include-partial-messages",
        "--permission-prompt-tool", "stdio", "--base-url", "http://localhost:1", "--verbose"), command);
  }

  @Test
  public void requiresAnExplicitCli() {
    if (System.getenv(AgentOptions.CLI_PATH_ENV) != null) {
      return;
    }
    try {
      new AgentOptions().resolveCliPath();
      fail("A CLI must be configured");
    } catch (AgentException expected) {
      assertTrue(expected.getMessage().contains(AgentOptions.CLI_PATH_ENV));
    }
  }

  @Test
  public void permissionResultsKeepOriginalInputUnlessReplaced() {
    assertEquals(JsonMaps.of("behavior", "allow", "updatedInput", JsonMaps.of("a", 1L)),
        PermissionResult.allow().toResponse(JsonMaps.of("a", 1L)));
    assertEquals(JsonMaps.of("behavior", "deny", "message", "no", "interrupt", true),
        PermissionResult.denyAndInterrupt("no").toResponse(JsonMaps.of()));
  }
}
