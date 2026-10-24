package nexus.io.agent.sdk;

/** Permission modes defined by the stream-json protocol. */
public enum PermissionMode {
  DEFAULT("default"), ACCEPT_EDITS("acceptEdits"), PLAN("plan"), BYPASS_PERMISSIONS("bypassPermissions"),
  DONT_ASK("dontAsk");

  private final String value;

  PermissionMode(String value) {
    this.value = value;
  }

  /** The protocol name, such as {@code acceptEdits}. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
