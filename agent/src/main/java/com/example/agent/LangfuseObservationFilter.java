package com.example.agent;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

/**
 * "Translator" for Langfuse.
 *
 * Spring AI records each step (chat call, tool call) as a span, but stores the content under
 * its own attribute names (or not at all for chat calls). Langfuse only shows Input/Output
 * when it finds the attributes "langfuse.observation.input" and "langfuse.observation.output".
 * This filter runs when each step finishes and copies the content under those names.
 */
@Component
public class LangfuseObservationFilter implements ObservationFilter {

    private static final String INPUT = "langfuse.observation.input";
    private static final String OUTPUT = "langfuse.observation.output";

    @Override
    public Observation.Context map(Observation.Context context) {
        // A tool call (e.g. query_database): input = arguments (the SQL), output = rows returned
        if (context instanceof ToolCallingObservationContext tool) {
            add(context, INPUT, tool.getToolCallArguments());
            add(context, OUTPUT, tool.getToolCallResult());
        }
        // One call to the AI model ("the brain"): input = all messages sent, output = its reply
        else if (context instanceof ChatModelObservationContext chat) {
            if (chat.getRequest() != null) {
                add(context, INPUT, format(chat.getRequest().getInstructions()));
            }
            add(context, OUTPUT, reply(chat.getResponse()));
        }
        // The whole question → answer run: input = the user's question, output = the final answer
        else if (context instanceof ChatClientObservationContext client) {
            if (client.getRequest() != null) {
                add(context, INPUT, client.getRequest().prompt().getUserMessage().getText());
            }
            if (client.getResponse() != null) {
                add(context, OUTPUT, reply(client.getResponse().chatResponse()));
            }
        }
        return context;
    }

    private static void add(Observation.Context context, String key, String value) {
        if (value != null && !value.isBlank()) {
            context.addHighCardinalityKeyValue(KeyValue.of(key, value));
        }
    }

    /** One line per message: "[USER] How many…", "[TOOL query_database] [{count:43}]", … */
    private static String format(List<Message> messages) {
        return messages.stream().map(LangfuseObservationFilter::format).collect(Collectors.joining("\n\n"));
    }

    private static String format(Message m) {
        if (m instanceof ToolResponseMessage tr) {
            return tr.getResponses().stream()
                    .map(r -> "[TOOL RESULT " + r.name() + "] " + r.responseData())
                    .collect(Collectors.joining("\n"));
        }
        if (m instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
            return am.getToolCalls().stream()
                    .map(c -> "[ASSISTANT asks tool " + c.name() + "] " + c.arguments())
                    .collect(Collectors.joining("\n"));
        }
        return "[" + m.getMessageType() + "] " + m.getText();
    }

    /** The model's reply: either text, or a request to call tools. */
    private static String reply(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        Generation g = response.getResult();
        return format(g.getOutput());
    }
}
