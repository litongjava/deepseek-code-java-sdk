package nexus.io.agent.demo;

import nexus.io.agent.demo.config.AgentDemoConfig;
import nexus.io.tio.boot.TioApplication;

public class AgentDemoApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    TioApplication.run(AgentDemoApp.class, new AgentDemoConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}
