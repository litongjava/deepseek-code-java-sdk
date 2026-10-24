# tio-boot-agent-demo

用 tio-boot 把 Agent CLI 包装成 HTTP 服务的示例，通过 `agent-cli-sdk` 调用 CLI：

- `POST /api/agent/query`：一次性运行一个 prompt，返回结果、轮次、费用和工具调用记录
- `POST /api/agent/stream`：同上，用 Server-Sent Events 实时推送 `init`、`text`、`tool_use`、`tool_result`、`result`
- 会话接口：创建会话后可多轮对话；回合进行中可查询状态（MCP 状态、上下文用量）或中断
- 服务端工具：`AgentToolService` 在 JVM 内注册 MCP 工具 `server_time`、`app_env`，Agent 以 `mcp__app__server_time` 调用
- 服务端权限策略：`AgentPermissionService` 拒绝 `agent.denied.tools` 中的工具（默认 `Bash`）
- `ApiTokenInterceptor` 校验 `Authorization: Bearer <app.api.token>`；`GlobalExceptionHandler` 把参数错误转换为 400

## 目录结构

```
src/main/java/nexus/io/agent/demo
├── AgentDemoApp.java            启动类
├── config/AgentDemoConfig.java  路由、拦截器、异常处理器
├── handler/                     每个接口一个 Handler
├── service/                     SDK 选项、查询、会话、工具、权限、消息映射
├── model/                       请求与响应实体
├── interceptor/                 Token 校验
├── exception/                   全局异常处理
└── utils/ParameterValidationUtils.java
```

## 配置

`src/main/resources/app.properties` 中的配置都可以用同名环境变量（点换成下划线并大写，例如 `AGENT_CLI_PATH`）或启动参数（`--agent.cli.path=...`）覆盖。

- `agent.cli.path`：Agent CLI 可执行文件路径
- `agent.cli.args`：额外 CLI 参数，格式为 `--flag value` 对，例如模型服务地址
- `agent.workspace`：Agent 的工作目录
- `agent.model`：默认模型
- `agent.permission.mode`：`default`、`acceptEdits`、`plan`、`bypassPermissions`、`dontAsk`
- `agent.allowed.tools`：无需确认的规则，逗号分隔
- `agent.denied.tools`：服务端始终拒绝的工具，逗号分隔
- `agent.query.timeout.seconds`：单个回合超时，超时后自动中断
- `agent.max.sessions`：同时打开的会话上限
- `app.api.token`：`/api/**` 的 Bearer Token；为空时仅在 `app.env=dev` 下放行

## 构建与运行

先在仓库根目录安装 SDK，再构建本示例：

```bash
cd ../..
mvn clean install -DskipTests -Dgpg.skip
cd examples/tio-boot-agent-demo
mvn clean package -DskipTests -Dgpg.skip
AGENT_CLI_PATH=/path/to/agent-cli APP_API_TOKEN=change-me java -jar target/tio-boot-agent-demo.jar
```

## 调用示例

一次性查询：

```bash
curl -s -H 'Authorization: Bearer change-me' -X POST localhost:8080/api/agent/query \
  -d '{"prompt":"what time is it"}'
```

```json
{"code":1,"ok":true,"data":{"subtype":"success","numTurns":2,"totalCostUsd":0.0,
 "toolCalls":[{"input":{},"isError":false,"name":"mcp__app__server_time","id":"time-1"}],
 "error":false,"result":"Tool said: 2026-10-24T03:18:35.698945093-10:00",
 "sessionId":"c854fdda-e169-4f2a-886a-15d226d0bf15"}}
```

流式输出：

```bash
curl -s -N -H 'Authorization: Bearer change-me' -X POST localhost:8080/api/agent/stream \
  -d '{"prompt":"what time is it"}'
```

```
event:init
data:{"permissionMode":"default","model":"mock-model","sessionId":"cd0fb958-...","event":"init","tools":51}

event:tool_use
data:{"input":{},"name":"mcp__app__server_time","id":"time-1","event":"tool_use"}

event:tool_result
data:{"output":"2026-10-24T03:22:33.565907122-10:00","isError":false,"event":"tool_result","toolUseId":"time-1"}

event:text
data:{"delta":"Tool said: 2026-10-24T0","event":"text"}

event:result
data:{"data":{"subtype":"success","numTurns":2,"result":"Tool said: ...","sessionId":"cd0fb958-..."},"event":"result"}
```

会话：

```bash
H='Authorization: Bearer change-me'
ID=$(curl -s -H "$H" -X POST localhost:8080/api/agent/sessions -d '{"permissionMode":"acceptEdits"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
curl -s -H "$H" -X POST localhost:8080/api/agent/sessions/$ID/messages -d '{"prompt":"time please"}'
curl -s -H "$H" localhost:8080/api/agent/sessions/$ID            # 状态，回合进行中也可调用
curl -s -H "$H" -X POST localhost:8080/api/agent/sessions/$ID/interrupt
curl -s -H "$H" -X POST localhost:8080/api/agent/sessions/$ID/close
```

同一会话同时只能运行一个回合，第二个请求会得到 400 和 “is busy” 提示；被中断的回合返回 `subtype` 为 `error_during_execution` 的结果。
