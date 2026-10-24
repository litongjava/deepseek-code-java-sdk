package nexus.io.agent.demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nexus.io.agent.sdk.AgentException;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.exception.ParameterValidationException;
import nexus.io.tio.boot.exception.TioBootExceptionHandler;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.websocket.common.WebSocketRequest;

/** Maps validation errors to 400, agent failures to 502 and everything else to 500. */
public class GlobalExceptionHandler implements TioBootExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  public RespBodyVo handler(HttpRequest request, Throwable e) {
    if (e instanceof ParameterValidationException) {
      TioRequestContext.getResponse().setStatus(400);
      return RespBodyVo.fail(e.getMessage());
    }
    if (e instanceof AgentException) {
      TioRequestContext.getResponse().setStatus(502);
      return RespBodyVo.fail("Agent error: " + e.getMessage());
    }
    log.error("Unhandled error for {}", request.getRequestLine(), e);
    TioRequestContext.getResponse().setStatus(500);
    return RespBodyVo.fail("Internal server error");
  }

  @Override
  public Object wsTextHandler(WebSocketRequest webSocketRequest, String text, ChannelContext channelContext,
      HttpRequest httpRequest, Throwable e) {
    return null;
  }

  @Override
  public Object wsBytesHandler(WebSocketRequest webSocketRequest, byte[] bytes, ChannelContext channelContext,
      HttpRequest httpRequest, Throwable e) {
    return null;
  }
}
