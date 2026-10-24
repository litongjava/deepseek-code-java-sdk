package nexus.io.agent.demo.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.common.RequestLine;
import nexus.io.tio.http.server.intf.HttpRequestInterceptor;
import nexus.io.tio.utils.environment.EnvUtils;

/** Requires {@code Authorization: Bearer <app.api.token>} on agent APIs. */
public class ApiTokenInterceptor implements HttpRequestInterceptor {
  private static final Logger log = LoggerFactory.getLogger(ApiTokenInterceptor.class);

  @Override
  public HttpResponse doBeforeHandler(HttpRequest request, RequestLine requestLine, HttpResponse response) {
    String expected = EnvUtils.getStr("app.api.token");
    if (expected == null || expected.isEmpty()) {
      if (EnvUtils.isDev()) {
        log.warn("app.api.token is not set; agent APIs are open in dev mode");
        return null;
      }
      return response.setStatus(401).setJson(RespBodyVo.fail("app.api.token is not configured"));
    }
    if (!expected.equals(request.getBearerToken())) {
      return response.setStatus(401).setJson(RespBodyVo.fail("Invalid or missing bearer token"));
    }
    return null;
  }

  @Override
  public void doAfterHandler(HttpRequest request, RequestLine requestLine, HttpResponse response, long cost) {
  }
}
