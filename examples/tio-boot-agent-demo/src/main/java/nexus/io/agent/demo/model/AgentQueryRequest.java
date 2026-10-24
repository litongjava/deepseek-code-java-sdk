package nexus.io.agent.demo.model;

/** Input of a one-shot or streamed agent run, and of session creation. */
public class AgentQueryRequest {
  private String prompt;
  /** Resumes a saved agent session by id. */
  private String resume;
  private String model;
  /** default, acceptEdits, plan, bypassPermissions or dontAsk. */
  private String permissionMode;

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getResume() {
    return resume;
  }

  public void setResume(String resume) {
    this.resume = resume;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getPermissionMode() {
    return permissionMode;
  }

  public void setPermissionMode(String permissionMode) {
    this.permissionMode = permissionMode;
  }
}
