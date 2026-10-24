package nexus.io.agent.demo.handler;

import com.jfinal.kit.Kv;

import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.environment.EnvUtils;

/** GET /health */
public class HealthHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv data = Kv.by("status", "ok").set("env", EnvUtils.appEnv());
    return TioRequestContext.getResponse().respond(RespBodyVo.ok(data));
  }
}
