package nexus.io.agent.sdk.message;

import java.util.Map;

import nexus.io.agent.sdk.json.JsonMaps;

/** The final message of a turn. */
public class ResultMessage extends Message {
  public ResultMessage(Map<String, Object> raw) {
    super(raw);
  }

  /** {@code success}, {@code error_max_turns}, {@code error_during_execution}, ... */
  public String getSubtype() {
    return JsonMaps.string(getRaw(), "subtype");
  }

  public boolean isError() {
    return JsonMaps.bool(getRaw(), "is_error");
  }

  /** The final answer text. */
  public String getResult() {
    return JsonMaps.string(getRaw(), "result");
  }

  /** The error description when {@link #isError()} is true and the CLI supplied one. */
  public String getError() {
    Object error = getRaw().get("error");
    return error == null ? null : String.valueOf(error);
  }

  public long getDurationMs() {
    Long value = JsonMaps.longValue(getRaw(), "duration_ms");
    return value == null ? 0 : value.longValue();
  }

  public int getNumTurns() {
    Long value = JsonMaps.longValue(getRaw(), "num_turns");
    return value == null ? 0 : value.intValue();
  }

  /** Cumulative session cost in USD, or null when the CLI does not report it. */
  public Double getTotalCostUsd() {
    return JsonMaps.doubleValue(getRaw(), "total_cost_usd");
  }

  public Map<String, Object> getUsage() {
    return JsonMaps.object(getRaw(), "usage");
  }

  /** The parsed value when a JSON schema was requested. */
  public Object getStructuredOutput() {
    return getRaw().get("structured_output");
  }
}
