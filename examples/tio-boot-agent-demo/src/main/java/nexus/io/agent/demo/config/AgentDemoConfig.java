package nexus.io.agent.demo.config;

import nexus.io.agent.demo.exception.GlobalExceptionHandler;
import nexus.io.agent.demo.handler.AgentQueryHandler;
import nexus.io.agent.demo.handler.AgentStreamHandler;
import nexus.io.agent.demo.handler.HealthHandler;
import nexus.io.agent.demo.handler.SessionCloseHandler;
import nexus.io.agent.demo.handler.SessionCreateHandler;
import nexus.io.agent.demo.handler.SessionInterruptHandler;
import nexus.io.agent.demo.handler.SessionListHandler;
import nexus.io.agent.demo.handler.SessionMessageHandler;
import nexus.io.agent.demo.handler.SessionStatusHandler;
import nexus.io.agent.demo.interceptor.ApiTokenInterceptor;
import nexus.io.agent.demo.service.AgentSessionService;
import nexus.io.context.BootConfiguration;
import nexus.io.hook.HookCan;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.interceptor.HttpInteceptorConfigure;
import nexus.io.tio.boot.http.interceptor.HttpInterceptorModel;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/** Routes, the API token interceptor and the exception handler. */
public class AgentDemoConfig implements BootConfiguration {

  @Override
  public void config() {
    TioBootServer server = TioBootServer.me();
    HttpRequestRouter router = server.getRequestRouter();
    router.get("/health", new HealthHandler()::handle);
    router.post("/api/agent/query", new AgentQueryHandler()::handle);
    router.post("/api/agent/stream", new AgentStreamHandler()::handle);
    router.get("/api/agent/sessions", new SessionListHandler()::handle);
    router.post("/api/agent/sessions", new SessionCreateHandler()::handle);
    router.get("/api/agent/sessions/{id}", new SessionStatusHandler()::handle);
    router.post("/api/agent/sessions/{id}/messages", new SessionMessageHandler()::handle);
    router.post("/api/agent/sessions/{id}/interrupt", new SessionInterruptHandler()::handle);
    router.post("/api/agent/sessions/{id}/close", new SessionCloseHandler()::handle);

    HttpInterceptorModel token = new HttpInterceptorModel();
    token.setName("apiToken");
    token.addBlockUrl("/api/**");
    token.setInterceptor(new ApiTokenInterceptor());
    HttpInteceptorConfigure interceptors = new HttpInteceptorConfigure();
    interceptors.add(token);
    server.setHttpInteceptorConfigure(interceptors);

    server.setExceptionHandler(new GlobalExceptionHandler());
    // Each session owns a CLI process; stop them with the server.
    HookCan.me().addDestroyMethod(() -> Aop.get(AgentSessionService.class).closeAll());
  }
}
