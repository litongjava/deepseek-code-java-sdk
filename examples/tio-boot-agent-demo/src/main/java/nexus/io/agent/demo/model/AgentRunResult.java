package nexus.io.agent.demo.model;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

/** The outcome of one agent turn. */
public class AgentRunResult {
  private String sessionId;
  private String subtype;
  private boolean error;
  private String result;
  private String errorMessage;
  private int numTurns;
  private Double totalCostUsd;
  private long durationMs;
  private Object structuredOutput;
  /** Tool calls in order: name, input and, once known, isError. */
  private List<Kv> toolCalls = new ArrayList<Kv>();

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getSubtype() {
    return subtype;
  }

  public void setSubtype(String subtype) {
    this.subtype = subtype;
  }

  public boolean isError() {
    return error;
  }

  public void setError(boolean error) {
    this.error = error;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public int getNumTurns() {
    return numTurns;
  }

  public void setNumTurns(int numTurns) {
    this.numTurns = numTurns;
  }

  public Double getTotalCostUsd() {
    return totalCostUsd;
  }

  public void setTotalCostUsd(Double totalCostUsd) {
    this.totalCostUsd = totalCostUsd;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  public Object getStructuredOutput() {
    return structuredOutput;
  }

  public void setStructuredOutput(Object structuredOutput) {
    this.structuredOutput = structuredOutput;
  }

  public List<Kv> getToolCalls() {
    return toolCalls;
  }

  public void setToolCalls(List<Kv> toolCalls) {
    this.toolCalls = toolCalls;
  }
}
