package nexus.io.agent.sdk.hook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Callbacks for the tools (or other targets) a matcher pattern selects. */
public final class HookMatcher {
  private final String matcher;
  private final List<HookCallback> callbacks;
  private final Integer timeoutSeconds;

  public HookMatcher(String matcher, List<HookCallback> callbacks, Integer timeoutSeconds) {
    this.matcher = matcher;
    this.callbacks = Collections.unmodifiableList(new ArrayList<HookCallback>(callbacks));
    this.timeoutSeconds = timeoutSeconds;
  }

  /** Matches every target, such as all tools. */
  public static HookMatcher all(HookCallback... callbacks) {
    return new HookMatcher(null, Arrays.asList(callbacks), null);
  }

  /** Matches targets selected by a pattern such as {@code Bash} or {@code Write|Edit}. */
  public static HookMatcher of(String matcher, HookCallback... callbacks) {
    return new HookMatcher(matcher, Arrays.asList(callbacks), null);
  }

  public HookMatcher withTimeout(int seconds) {
    return new HookMatcher(matcher, callbacks, Integer.valueOf(seconds));
  }

  public String getMatcher() {
    return matcher;
  }

  public List<HookCallback> getCallbacks() {
    return callbacks;
  }

  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }
}
