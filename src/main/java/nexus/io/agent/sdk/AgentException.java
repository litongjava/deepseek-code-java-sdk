package nexus.io.agent.sdk;

/** A failure talking to the agent CLI, or an error answer to a control request. */
public class AgentException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public AgentException(String message) {
    super(message);
  }

  public AgentException(String message, Throwable cause) {
    super(message, cause);
  }
}
