package nexus.io.agent.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.ResultMessage;

/** One-shot queries: start the CLI, run one prompt, stop the CLI. */
public final class Agent {

  private Agent() {
  }

  /** Runs one prompt and returns every message, ending with the result. */
  public static List<Message> query(String prompt, AgentOptions options) {
    final List<Message> messages = new ArrayList<Message>();
    query(prompt, options, new Consumer<Message>() {
      @Override
      public void accept(Message message) {
        messages.add(message);
      }
    });
    return messages;
  }

  /** Runs one prompt, streaming each message to the consumer, and returns the result. */
  public static ResultMessage query(String prompt, AgentOptions options, Consumer<Message> onMessage) {
    AgentClient client = new AgentClient(options);
    try {
      client.connect();
      client.query(prompt);
      return client.awaitResult(onMessage);
    } finally {
      client.close();
    }
  }
}
