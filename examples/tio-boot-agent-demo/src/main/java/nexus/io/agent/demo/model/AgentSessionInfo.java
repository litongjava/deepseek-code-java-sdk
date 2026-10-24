package nexus.io.agent.demo.model;

/** A long-lived agent session held by this server. */
public class AgentSessionInfo {
  /** Snowflake id used in this server's URLs. */
  private long id;
  /** The agent CLI's own session id, known after the first turn starts. */
  private String agentSessionId;
  private boolean busy;
  private long createdAt;
  private long lastUsedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getAgentSessionId() {
    return agentSessionId;
  }

  public void setAgentSessionId(String agentSessionId) {
    this.agentSessionId = agentSessionId;
  }

  public boolean isBusy() {
    return busy;
  }

  public void setBusy(boolean busy) {
    this.busy = busy;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public long getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(long lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
