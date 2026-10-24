package nexus.io.agent.demo.service;

import com.jfinal.kit.Kv;

import nexus.io.agent.demo.model.AgentRunResult;
import nexus.io.agent.sdk.message.AssistantMessage;
import nexus.io.agent.sdk.message.ContentBlock;
import nexus.io.agent.sdk.message.Message;
import nexus.io.agent.sdk.message.ResultMessage;
import nexus.io.agent.sdk.message.StreamEvent;
import nexus.io.agent.sdk.message.SystemMessage;
import nexus.io.agent.sdk.message.ToolResultBlock;
import nexus.io.agent.sdk.message.ToolUseBlock;
import nexus.io.agent.sdk.message.UserMessage;

/** Turns SDK messages into the run summary and into client-facing stream events. */
public class AgentMessageMapper {
  private static final int MAX_TOOL_OUTPUT = 2000;

  /** Records tool calls and the final result of a turn. */
  public void collect(AgentRunResult run, Message message) {
    if (message instanceof AssistantMessage) {
      for (ToolUseBlock use : ((AssistantMessage) message).getToolUses()) {
        run.getToolCalls().add(Kv.by("id", use.getId()).set("name", use.getName()).set("input", use.getInput()));
      }
    } else if (message instanceof UserMessage) {
      for (ContentBlock block : ((UserMessage) message).getContent()) {
        if (block instanceof ToolResultBlock) {
          ToolResultBlock result = (ToolResultBlock) block;
          for (Kv call : run.getToolCalls()) {
            if (result.getToolUseId() != null && result.getToolUseId().equals(call.getStr("id"))) {
              call.set("isError", result.isError());
            }
          }
        }
      }
    } else if (message instanceof ResultMessage) {
      ResultMessage result = (ResultMessage) message;
      run.setSessionId(result.getSessionId());
      run.setSubtype(result.getSubtype());
      run.setError(result.isError());
      run.setResult(result.getResult());
      run.setErrorMessage(result.getError());
      run.setNumTurns(result.getNumTurns());
      run.setTotalCostUsd(result.getTotalCostUsd());
      run.setDurationMs(result.getDurationMs());
      run.setStructuredOutput(result.getStructuredOutput());
    }
  }

  /** The stream event for a message, or null when the client does not need it. */
  public Kv toEvent(Message message) {
    if (message instanceof SystemMessage && ((SystemMessage) message).isInit()) {
      SystemMessage init = (SystemMessage) message;
      return Kv.by("event", "init").set("sessionId", init.getSessionId()).set("model", init.getModel())
          .set("permissionMode", init.getPermissionMode()).set("tools", init.getTools().size());
    }
    if (message instanceof StreamEvent) {
      String delta = ((StreamEvent) message).getTextDelta();
      return delta == null ? null : Kv.by("event", "text").set("delta", delta);
    }
    if (message instanceof AssistantMessage) {
      if (((AssistantMessage) message).getToolUses().isEmpty()) {
        return null;
      }
      ToolUseBlock use = ((AssistantMessage) message).getToolUses().get(0);
      return Kv.by("event", "tool_use").set("id", use.getId()).set("name", use.getName()).set("input", use.getInput());
    }
    if (message instanceof UserMessage) {
      for (ContentBlock block : ((UserMessage) message).getContent()) {
        if (block instanceof ToolResultBlock) {
          ToolResultBlock result = (ToolResultBlock) block;
          String output = result.getContentText();
          if (output.length() > MAX_TOOL_OUTPUT) {
            output = output.substring(0, MAX_TOOL_OUTPUT) + "...";
          }
          return Kv.by("event", "tool_result").set("toolUseId", result.getToolUseId()).set("isError", result.isError())
              .set("output", output);
        }
      }
    }
    return null;
  }
}
