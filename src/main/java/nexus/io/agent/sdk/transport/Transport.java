package nexus.io.agent.sdk.transport;

import java.io.IOException;

/** A line-oriented connection to an agent CLI. */
public interface Transport extends AutoCloseable {
  void start() throws IOException;

  /** Writes one NDJSON line; safe to call from several threads. */
  void write(String line) throws IOException;

  /** The next stdout line, or null at end of output. */
  String readLine() throws IOException;

  /** Closes the CLI's input, which ends the session once queued work finishes. */
  void endInput();

  /** Waits for the CLI to exit; returns its exit code, or null on timeout. */
  Integer waitFor(long timeoutMillis) throws InterruptedException;

  /** Stops the CLI if it is still running. */
  @Override
  void close();
}
