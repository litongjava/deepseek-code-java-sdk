# agent-cli-sdk

用 Java 驱动 Agent CLI 的 SDK。SDK 通过子进程启动 CLI，在 stdin/stdout 上使用 stream-json 控制协议（NDJSON）通信，提供：

- 一次性查询与长连接会话（多轮对话、排队消息、中断）
- 类型化消息：`SystemMessage`、`AssistantMessage`、`UserMessage`、`ResultMessage`、`StreamEvent`
- 工具权限回调 `CanUseTool`，可改写工具输入、附带权限规则更新
- Hook 回调（`PreToolUse`、`PostToolUse`、`UserPromptSubmit` 等）
- 进程内 MCP 服务器：在 JVM 里实现工具，Agent 以 `mcp__<server>__<tool>` 调用
- 子 Agent 定义、结构化输出（JSON Schema）、部分消息流
- 会话控制：切换模型 / 权限模式、思考预算、MCP 状态与开关、上下文用量、设置查询、文件回退、停止后台任务、取消排队消息

SDK 不依赖任何第三方库，支持 Java 8 及以上版本。SDK 不绑定具体的 CLI：任何实现了 stream-json 控制协议的可执行文件都可以使用，路径通过 `AgentOptions.cliPath(...)` 或环境变量 `AGENT_CLI_PATH` 指定。

## 安装

```xml
<dependency>
  <groupId>nexus.io</groupId>
  <artifactId>agent-cli-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

本地构建见 `build.txt`：

```bash
mvn clean install -DskipTests -Dgpg.skip
```

## 一次性查询

```java
import java.io.File;

import nexus.io.agent.sdk.Agent;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.PermissionMode;
import nexus.io.agent.sdk.message.AssistantMessage;
import nexus.io.agent.sdk.message.ResultMessage;

public class QuickStart {
  public static void main(String[] args) {
    AgentOptions options = new AgentOptions()
        .cliPath("/usr/local/bin/my-agent")
        .cwd(new File("/path/to/project"))
        .permissionMode(PermissionMode.ACCEPT_EDITS)
        .allowedTools("Read", "Grep", "Glob");

    ResultMessage result = Agent.query("总结 README.md 的内容", options, message -> {
      if (message instanceof AssistantMessage) {
        System.out.print(((AssistantMessage) message).getText());
      }
    });
    System.out.println();
    System.out.println("结果: " + result.getResult());
    System.out.println("轮次: " + result.getNumTurns() + "，费用(USD): " + result.getTotalCostUsd());
  }
}
```

## 多轮会话

`AgentClient` 保持一个 CLI 进程，可以连续提问，也可以在回合进行中查询状态或中断。

```java
import nexus.io.agent.sdk.AgentClient;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.ResultMessage;
import nexus.io.agent.sdk.message.SystemMessage;

public class Conversation {
  public static void main(String[] args) {
    AgentOptions options = new AgentOptions().cliPath("/usr/local/bin/my-agent");
    try (AgentClient client = new AgentClient(options)) {
      client.connect();

      client.query("项目使用什么构建工具？");
      for (Message message : client.receiveResponse()) {
        if (message instanceof SystemMessage && ((SystemMessage) message).isInit()) {
          System.out.println("会话: " + message.getSessionId() + "，模型: " + ((SystemMessage) message).getModel());
        }
        if (message instanceof ResultMessage) {
          System.out.println(((ResultMessage) message).getResult());
        }
      }

      client.query("列出构建命令");
      ResultMessage second = client.awaitResult(null);
      System.out.println(second.getResult());
      System.out.println("上下文: " + client.getContextUsage().get("totalTokens") + " tokens");
    }
  }
}
```

`receiveResponse()` 返回到下一条 `ResultMessage`（含）为止的消息；`receiveMessages()` 返回直到 CLI 结束的全部消息；`awaitResult(consumer)` 把每条消息交给回调并返回结果。

## 工具权限

不设置 `canUseTool` 时，所有需要确认的工具调用都会被拒绝。

```java
import java.util.Map;

import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.PermissionMode;
import nexus.io.agent.sdk.permission.CanUseTool;
import nexus.io.agent.sdk.permission.PermissionResult;
import nexus.io.agent.sdk.permission.ToolPermissionContext;

public class Permissions {
  public static AgentOptions options() {
    CanUseTool policy = (String toolName, Map<String, Object> input, ToolPermissionContext context) -> {
      if ("Bash".equals(toolName) && String.valueOf(input.get("command")).contains("rm -rf")) {
        return PermissionResult.deny("禁止删除目录");
      }
      if ("Write".equals(toolName)) {
        // 允许，并采纳 CLI 建议的会话规则，之后同类写入不再询问
        return PermissionResult.allow(input, context.getSuggestions());
      }
      return PermissionResult.allow();
    };
    return new AgentOptions()
        .cliPath("/usr/local/bin/my-agent")
        .permissionMode(PermissionMode.DEFAULT)
        .canUseTool(policy);
  }
}
```

`PermissionResult.allow(updatedInput)` 可以改写工具输入；`denyAndInterrupt(message)` 拒绝并结束整个回合。

## Hook

```java
import java.util.Map;

import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.hook.HookEvent;
import nexus.io.agent.sdk.hook.HookMatcher;
import nexus.io.agent.sdk.json.JsonMaps;

public class Hooks {
  public static AgentOptions options() {
    return new AgentOptions()
        .cliPath("/usr/local/bin/my-agent")
        .hook(HookEvent.PreToolUse, HookMatcher.of("Bash", (Map<String, Object> input, String toolUseId) -> {
          Map<String, Object> toolInput = JsonMaps.object(input, "tool_input");
          if (String.valueOf(toolInput.get("command")).startsWith("git push")) {
            return JsonMaps.of("hookSpecificOutput", JsonMaps.of(
                "hookEventName", "PreToolUse",
                "permissionDecision", "deny",
                "permissionDecisionReason", "推送需要人工确认"));
          }
          return JsonMaps.of();
        }).withTimeout(30))
        .hook(HookEvent.PostToolUse, HookMatcher.all((input, toolUseId) -> {
          System.out.println("工具完成: " + input.get("tool_name"));
          return JsonMaps.of();
        }));
  }
}
```

## 进程内 MCP 工具

```java
import java.util.Arrays;

import nexus.io.agent.sdk.Agent;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.PermissionMode;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.mcp.SdkMcpServer;
import nexus.io.agent.sdk.mcp.SdkTool;
import nexus.io.agent.sdk.mcp.ToolResult;
import nexus.io.agent.sdk.message.ResultMessage;

public class InProcessTools {
  public static void main(String[] args) {
    SdkTool weather = new SdkTool("weather", "查询城市天气",
        JsonMaps.of("type", "object",
            "properties", JsonMaps.of("city", JsonMaps.of("type", "string")),
            "required", Arrays.asList("city")),
        arguments -> ToolResult.text(arguments.get("city") + "：晴，22°C"));

    AgentOptions options = new AgentOptions()
        .cliPath("/usr/local/bin/my-agent")
        .permissionMode(PermissionMode.BYPASS_PERMISSIONS)
        .mcpServer(SdkMcpServer.of("tools", weather));

    ResultMessage result = Agent.query("上海天气怎么样？", options, null);
    System.out.println(result.getResult());
  }
}
```

工具抛出的异常会作为 `isError` 结果返回给 Agent，不会中断会话。

## 子 Agent 与结构化输出

```java
import java.util.Arrays;

import nexus.io.agent.sdk.Agent;
import nexus.io.agent.sdk.AgentDefinition;
import nexus.io.agent.sdk.AgentOptions;
import nexus.io.agent.sdk.json.JsonMaps;
import nexus.io.agent.sdk.message.ResultMessage;

public class Structured {
  public static void main(String[] args) {
    AgentOptions options = new AgentOptions()
        .cliPath("/usr/local/bin/my-agent")
        .agent("reviewer", new AgentDefinition("审查代码改动", "你是严格的代码审查员。")
            .tools(Arrays.asList("Read", "Grep")))
        .jsonSchema(JsonMaps.of("type", "object",
            "properties", JsonMaps.of("issues", JsonMaps.of("type", "integer")),
            "required", Arrays.asList("issues")));

    ResultMessage result = Agent.query("审查 src 目录并统计问题数量", options, null);
    System.out.println(result.getStructuredOutput());
  }
}
```

## 会话控制

- `interrupt()`：中断正在运行的回合
- `setPermissionMode(mode)`：切换权限模式（回合中也可调用）
- `setModel(model)`：切换后续回合的模型（需空闲）
- `setMaxThinkingTokens(n)`：设置思考预算，`null` 恢复默认，`0` 关闭
- `mcpStatus()` / `mcpToggle()` / `mcpReconnect()`：MCP 服务器状态、启停、重连
- `getContextUsage()`：上下文窗口用量
- `getSettings()` / `applyFlagSettings()`：读取合并后的设置；应用会话级设置
- `rewindFiles(uuid, dryRun)`：回退某条用户消息之后的文件改动；`query` 返回的就是该 uuid
- `cancelQueuedMessage(uuid)`：取消尚未开始的排队消息
- `stopTask(id)`：停止后台任务
- `reloadPlugins()`：重新加载插件
- `control(request)`：发送任意控制请求

## 其它选项

- `includePartialMessages(true)`：接收 `StreamEvent`，`getTextDelta()` 取增量文本，适合打字机效果。
- `extraArg("flag", "value")`：传递 SDK 未建模的 CLI 参数（不带前导 `--`）。
- `env(name, value)`：为 CLI 进程设置环境变量。
- `stderr(consumer)`：接收 CLI 的 stderr 输出。
- `resume(sessionId)` / `continueConversation(true)` / `forkSession(true)`：恢复或分叉会话。
- `initializeField(name, value)`：向 `initialize` 请求添加字段，例如 `promptSuggestions`。

## 测试

```bash
mvn test -Dgpg.skip
```

`CliEndToEndTest` 在 JVM 内启动一个 OpenAI 兼容的模拟模型，驱动真实 CLI 完成 MCP 工具、权限、Hook 与控制请求的完整流程。设置以下环境变量后启用：

- `AGENT_CLI_PATH`：CLI 路径
- `AGENT_CLI_MOCK_ARGS`：让 CLI 连接模拟模型的参数，`{url}` 替换为模拟服务地址，`{data}` 替换为临时数据目录

## 许可

Apache License 2.0
