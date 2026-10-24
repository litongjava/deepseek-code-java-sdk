package nexus.io.agent.demo.model;

/** A prompt sent to an open agent session. */
public class SessionMessageRequest {
  private String prompt;

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }
}
