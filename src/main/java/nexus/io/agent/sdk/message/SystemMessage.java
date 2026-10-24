package nexus.io.agent.sdk.message;

import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** A system message; {@code subtype} {@code init} describes the session. */
public class SystemMessage extends Message {
  public SystemMessage(Map<String, Object> raw) {
    super(raw);
  }

  public String getSubtype() {
    return JsonMaps.string(getRaw(), "subtype");
  }

  public boolean isInit() {
    return "init".equals(getSubtype());
  }

  public String getModel() {
    return JsonMaps.string(getRaw(), "model");
  }

  public String getCwd() {
    return JsonMaps.string(getRaw(), "cwd");
  }

  public String getPermissionMode() {
    return JsonMaps.string(getRaw(), "permissionMode");
  }

  public List<String> getTools() {
    return JsonMaps.strings(getRaw(), "tools");
  }

  public List<Map<String, Object>> getMcpServers() {
    return JsonMaps.objects(getRaw(), "mcp_servers");
  }
}
