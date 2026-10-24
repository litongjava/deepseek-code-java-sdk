package nexus.io.agent.demo.handler;

import nexus.io.agent.demo.service.AgentSessionService;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/agent/sessions */
public class SessionListHandler {
  public HttpResponse handle(HttpRequest request) {
    AgentSessionService agentSessionService = Aop.get(AgentSessionService.class);
    return TioRequestContext.getResponse().respond(RespBodyVo.ok(agentSessionService.list()));
  }
}
