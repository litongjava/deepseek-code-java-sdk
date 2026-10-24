package nexus.io.agent.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import com.jfinal.kit.Kv;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.demo.model.AgentSessionInfo;
import nexus.io.agent.sdk.AgentClient;
import nexus.io.agent.sdk.message.Message;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.exception.ParameterValidationException;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/** Long-lived agent sessions: one CLI process per session, one turn at a time. */
public class AgentSessionService {

  private static final class Entry {
    final AgentSessionInfo info;
    final AgentClient client;
    final ReentrantLock turn = new ReentrantLock();

    Entry(AgentSessionInfo info, AgentClient client) {
      this.info = info;
      this.client = client;
    }
  }

  private final Map<Long, Entry> sessions = new ConcurrentHashMap<Long, Entry>();

  public synchronized AgentSessionInfo create(AgentQueryRequest input) {
    int limit = EnvUtils.getInt("agent.max.sessions", 8);
    if (sessions.size() >= limit) {
      throw new ParameterValidationException("Too many open sessions (limit " + limit + "); close one first");
    }
    AgentClient client = new AgentClient(Aop.get(AgentOptionsService.class).build(input, false));
    client.connect();
    AgentSessionInfo info = new AgentSessionInfo();
    info.setId(SnowflakeIdUtils.id());
    info.setCreatedAt(System.currentTimeMillis());
    info.setLastUsedAt(info.getCreatedAt());
    sessions.put(info.getId(), new Entry(info, client));
    return info;
  }

  public AgentRunResult send(long id, String prompt) {
    Entry entry = get(id);
    if (!entry.turn.tryLock()) {
      throw new ParameterValidationException("Session " + id + " is busy; wait for the current turn or interrupt it");
    }
    try {
      entry.info.setBusy(true);
      AgentMessageMapper mapper = Aop.get(AgentMessageMapper.class);
      AgentRunResult result = new AgentRunResult();
      ScheduledFuture<?> timeout = Aop.get(AgentQueryService.class).scheduleInterrupt(entry.client);
      try {
        entry.client.query(prompt);
        entry.client.awaitResult((Message message) -> mapper.collect(result, message));
      } finally {
        timeout.cancel(false);
      }
      entry.info.setAgentSessionId(result.getSessionId());
      return result;
    } finally {
      entry.info.setBusy(false);
      entry.info.setLastUsedAt(System.currentTimeMillis());
      entry.turn.unlock();
    }
  }

  /** Session details plus live MCP status and context usage from the CLI. */
  public Kv status(long id) {
    Entry entry = get(id);
    return Kv.by("session", entry.info).set("mcpServers", entry.client.mcpStatus())
        .set("contextUsage", Kv.create().set(entry.client.getContextUsage()));
  }

  public List<AgentSessionInfo> list() {
    List<AgentSessionInfo> list = new ArrayList<AgentSessionInfo>();
    for (Entry entry : sessions.values()) {
      list.add(entry.info);
    }
    return list;
  }

  public void interrupt(long id) {
    get(id).client.interrupt();
  }

  public void close(long id) {
    Entry entry = sessions.remove(id);
    if (entry == null) {
      throw new ParameterValidationException("Unknown session: " + id);
    }
    entry.client.close();
  }

  public void closeAll() {
    for (Long id : new ArrayList<Long>(sessions.keySet())) {
      Entry entry = sessions.remove(id);
      if (entry != null) {
        entry.client.close();
      }
    }
  }

  private Entry get(long id) {
    Entry entry = sessions.get(id);
    if (entry == null) {
      throw new ParameterValidationException("Unknown session: " + id);
    }
    return entry;
  }
}
