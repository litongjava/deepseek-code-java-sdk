package nexus.io.agent.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.agent.sdk.json.JsonValue;

/** A subagent the main agent can delegate to, sent with {@code initialize}. */
public final class AgentDefinition implements JsonValue {
  private final String description;
  private final String prompt;
  private List<String> tools;
  private String model;
  private final Map<String, Object> extra = new LinkedHashMap<String, Object>();

  public AgentDefinition(String description, String prompt) {
    this.description = description;
    this.prompt = prompt;
  }

  /** Restricts the subagent to these tools; all tools are available when unset. */
  public AgentDefinition tools(List<String> tools) {
    this.tools = new ArrayList<String>(tools);
    return this;
  }

  public AgentDefinition model(String model) {
    this.model = model;
    return this;
  }

  /** Any further definition field the CLI understands, such as {@code permissionMode}. */
  public AgentDefinition set(String field, Object value) {
    extra.put(field, value);
    return this;
  }

  @Override
  public Object toJson() {
    Map<String, Object> json = new LinkedHashMap<String, Object>();
    json.put("description", description);
    json.put("prompt", prompt);
    if (tools != null) {
      json.put("tools", tools);
    }
    if (model != null) {
      json.put("model", model);
    }
    json.putAll(extra);
    return json;
  }
}
