package nexus.io.agent.demo.handler;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.demo.service.AgentQueryService;
import nexus.io.agent.demo.utils.ParameterValidationUtils;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/agent/query */
public class AgentQueryHandler {
  public HttpResponse handle(HttpRequest request) {
    String bodyString = ParameterValidationUtils.requestBody(request, 262144);
    AgentQueryRequest input = JsonUtils.parse(bodyString, AgentQueryRequest.class);
    AgentQueryService agentQueryService = Aop.get(AgentQueryService.class);
    agentQueryService.validate(input, true);
    AgentRunResult result = agentQueryService.query(input);
    return TioRequestContext.getResponse().respond(RespBodyVo.ok(result));
  }
}
