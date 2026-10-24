package nexus.io.agent.demo.handler;

import java.nio.charset.StandardCharsets;

import com.jfinal.kit.Kv;

import nexus.io.agent.demo.model.AgentQueryRequest;
import nexus.io.agent.demo.service.AgentQueryService;
import nexus.io.agent.demo.utils.ParameterValidationUtils;
import nexus.io.http.common.sse.SsePacket;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.util.SseEmitter;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * POST /api/agent/stream
 *
 * <p>Server-Sent Events: {@code init}, {@code text}, {@code tool_use},
 * {@code tool_result}, then {@code result} (or {@code error}).
 */
public class AgentStreamHandler {
  public HttpResponse handle(HttpRequest request) {
    String bodyString = ParameterValidationUtils.requestBody(request, 262144);
    AgentQueryRequest input = JsonUtils.parse(bodyString, AgentQueryRequest.class);
    AgentQueryService agentQueryService = Aop.get(AgentQueryService.class);
    // Validate before switching to SSE, so bad input still gets a JSON 400.
    agentQueryService.validate(input, true);

    ChannelContext channelContext = request.getChannelContext();
    HttpResponse response = TioRequestContext.getResponse();
    response.addServerSentEventsHeader();
    Tio.bSend(channelContext, response);
    response.setSend(false);

    agentQueryService.stream(input, (Kv event) -> {
      String name = event.getStr("event");
      byte[] data = JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8);
      Tio.bSend(channelContext, new SsePacket().event(name).data(data));
    }, () -> SseEmitter.closeSeeConnection(channelContext));
    return response;
  }
}
