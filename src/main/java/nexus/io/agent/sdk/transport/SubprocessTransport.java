package nexus.io.agent.sdk.transport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import nexus.io.agent.sdk.AgentException;
import nexus.io.agent.sdk.AgentOptions;

/** Runs the agent CLI as a child process and talks NDJSON over stdin/stdout. */
public class SubprocessTransport implements Transport {
  private final AgentOptions options;
  private Process process;
  private BufferedWriter stdin;
  private BufferedReader stdout;
  private final Object writeLock = new Object();
  private volatile boolean inputClosed;

  public SubprocessTransport(AgentOptions options) {
    this.options = options;
  }

  /** The full command line: executable followed by its arguments. */
  public List<String> command() {
    List<String> command = new ArrayList<String>();
    command.add(options.resolveCliPath());
    command.addAll(options.buildArguments());
    return command;
  }

  @Override
  public void start() throws IOException {
    ProcessBuilder builder = new ProcessBuilder(command());
    if (options.getCwd() != null) {
      builder.directory(options.getCwd());
    }
    Map<String, String> environment = builder.environment();
    environment.putAll(options.getEnv());
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new AgentException("Cannot start agent CLI " + command().get(0) + ": " + e.getMessage(), e);
    }
    stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    final BufferedReader stderr = new BufferedReader(
        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
    final Consumer<String> sink = options.getStderr();
    Thread drain = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          String line;
          while ((line = stderr.readLine()) != null) {
            if (sink != null) {
              sink.accept(line);
            }
          }
        } catch (IOException ignored) {
          // The process ended; nothing more to drain.
        }
      }
    }, "agent-cli-stderr");
    drain.setDaemon(true);
    drain.start();
  }

  @Override
  public void write(String line) throws IOException {
    synchronized (writeLock) {
      if (inputClosed) {
        throw new IOException("Agent CLI input is closed");
      }
      stdin.write(line);
      stdin.write('\n');
      stdin.flush();
    }
  }

  @Override
  public String readLine() throws IOException {
    return stdout.readLine();
  }

  @Override
  public void endInput() {
    synchronized (writeLock) {
      if (inputClosed || stdin == null) {
        return;
      }
      inputClosed = true;
      try {
        stdin.close();
      } catch (IOException ignored) {
        // Already closed by the process.
      }
    }
  }

  @Override
  public Integer waitFor(long timeoutMillis) throws InterruptedException {
    if (process == null) {
      return null;
    }
    if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
      return Integer.valueOf(process.exitValue());
    }
    return null;
  }

  @Override
  public void close() {
    endInput();
    if (process != null && process.isAlive()) {
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
      }
    }
  }
}
