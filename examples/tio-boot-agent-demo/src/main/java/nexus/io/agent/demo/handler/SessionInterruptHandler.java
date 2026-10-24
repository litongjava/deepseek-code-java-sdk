package nexus.io.agent.demo.handler;

import nexus.io.agent.demo.service.AgentSessionService;
import nexus.io.agent.demo.utils.ParameterValidationUtils;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/agent/sessions/{id}/interrupt */
public class SessionInterruptHandler {
  public HttpResponse handle(HttpRequest request) {
    long id = ParameterValidationUtils.id(request, "id");
    AgentSessionService agentSessionService = Aop.get(AgentSessionService.class);
    agentSessionService.interrupt(id);
    return TioRequestContext.getResponse().respond(RespBodyVo.ok());
  }
}
