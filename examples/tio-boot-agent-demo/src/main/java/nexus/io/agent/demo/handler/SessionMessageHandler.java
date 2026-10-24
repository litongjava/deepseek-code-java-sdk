package nexus.io.agent.demo.handler;

import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.demo.model.SessionMessageRequest;
import nexus.io.agent.demo.service.AgentSessionService;
import nexus.io.agent.demo.utils.ParameterValidationUtils;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/agent/sessions/{id}/messages */
public class SessionMessageHandler {
  public HttpResponse handle(HttpRequest request) {
    long id = ParameterValidationUtils.id(request, "id");
    String bodyString = ParameterValidationUtils.requestBody(request, 262144);
    SessionMessageRequest input = JsonUtils.parse(bodyString, SessionMessageRequest.class);
    ParameterValidationUtils.required(input, "Request body must be a JSON object");
    String prompt = ParameterValidationUtils.text(input.getPrompt(), "prompt", 100_000, true);
    AgentSessionService agentSessionService = Aop.get(AgentSessionService.class);
    AgentRunResult result = agentSessionService.send(id, prompt);
    return TioRequestContext.getResponse().respond(RespBodyVo.ok(result));
  }
}
