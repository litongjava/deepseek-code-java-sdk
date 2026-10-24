package nexus.io.agent.sdk.hook;

import java.util.Map;

/**
 * Handles one hook invocation. The returned map is the hook output, for example
 * {@code {"decision":"block","reason":"..."}} or a {@code hookSpecificOutput}
 * object; return an empty map to continue without changes.
 */
public interface HookCallback {
  Map<String, Object> call(Map<String, Object> input, String toolUseId) throws Exception;
}
