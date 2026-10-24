package nexus.io.agent.sdk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

import nexus.io.agent.sdk.json.Json;
import nexus.io.agent.sdk.transport.Transport;

/** An in-memory CLI: a script reacts to each frame the client writes. */
class ScriptedTransport implements Transport {
  private static final String EOF = "\u0000eof";
  private final BlockingQueue<String> output = new LinkedBlockingQueue<String>();
  final List<Map<String, Object>> written = new CopyOnWriteArrayList<Map<String, Object>>();
  private final BiConsumer<ScriptedTransport, Map<String, Object>> script;
  private volatile Integer exitCode;

  ScriptedTransport(BiConsumer<ScriptedTransport, Map<String, Object>> script) {
    this.script = script;
  }

  void emit(Map<String, Object> frame) {
    output.add(Json.stringify(frame));
  }

  void emitLine(String line) {
    output.add(line);
  }

  @Override
  public void start() {
  }

  @Override
  public void write(String line) {
    Map<String, Object> frame = Json.parseObject(line);
    written.add(frame);
    script.accept(this, frame);
  }

  @Override
  public String readLine() throws java.io.IOException {
    try {
      String line = output.take();
      return EOF.equals(line) ? null : line;
    } catch (InterruptedException e) {
      throw new java.io.IOException(e);
    }
  }

  @Override
  public void endInput() {
    if (exitCode == null) {
      exitCode = 0;
      output.add(EOF);
    }
  }

  @Override
  public Integer waitFor(long timeoutMillis) {
    return exitCode;
  }

  @Override
  public void close() {
    endInput();
  }
}
