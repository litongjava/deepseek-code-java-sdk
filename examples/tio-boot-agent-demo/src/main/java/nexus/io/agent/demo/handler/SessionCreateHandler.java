package nexus.io.agent.demo.handler;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.demo.model.AgentSessionInfo;
import nexus.io.agent.demo.service.AgentQueryService;
import nexus.io.agent.demo.service.AgentSessionService;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/agent/sessions; the body (model, permissionMode, resume) is optional. */
public class SessionCreateHandler {
  public HttpResponse handle(HttpRequest request) {
    String bodyString = request.getBodyString();
    AgentQueryRequest input = bodyString == null || bodyString.trim().isEmpty() ? new AgentQueryRequest()
        : JsonUtils.parse(bodyString, AgentQueryRequest.class);
    Aop.get(AgentQueryService.class).validate(input, false);
    AgentSessionService agentSessionService = Aop.get(AgentSessionService.class);
    AgentSessionInfo session = agentSessionService.create(input);
    return TioRequestContext.getResponse().respond(RespBodyVo.ok(session));
  }
}
