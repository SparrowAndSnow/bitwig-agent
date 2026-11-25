package dev.bitwigagent;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/** The advertised MCP catalog and the adapter to the existing host operations. */
final class McpTools {
    private static final Set<String> OUTCOMES = Set.of("not_dispatched", "not_applied", "unknown");
    private final JsonArray tools;
    private final Handler host;

    interface Handler {
        CompletableFuture<JsonObject> handle(String method, JsonObject args, long deadlineNanos);
    }

    McpTools(Handler host) throws IOException {
        this.host = host;
        try (InputStream input = McpTools.class.getResourceAsStream("/bitwig-agent-mcp-tools.json")) {
            if (input == null) throw new IOException("Missing MCP tool catalog");
            tools = StrictJson.parse(input.readAllBytes()).getAsJsonArray();
        }
        Set<String> names = new HashSet<>();
        for (JsonElement tool : tools) {
            if (!names.add(tool.getAsJsonObject().get("name").getAsString()))
                throw new IOException("Duplicate MCP tool");
        }
        if (!names.equals(HostBridge.supportedMethods()))
            throw new IOException("MCP tool catalog and host registry differ");
    }

    JsonObject list() {
        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return result;
    }

    JsonObject call(String name, JsonObject args, long receivedAt) {
        int timeoutMs = 5000;
        try {
            if (args.has("timeoutMs")) timeoutMs = Params.integer(args, "timeoutMs", 100, 10000);
        } catch (RpcException error) {
            return error(error.code, error.getMessage(), "not_dispatched");
        }
        args.remove("timeoutMs");
        long deadline = receivedAt + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        if (deadline - System.nanoTime() <= 0)
            return error("deadline_exceeded", "Request expired before host dispatch.", "not_dispatched");

        try {
            Future<JsonObject> pending = host.handle(name, args, deadline);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 && !pending.isDone()) throw new TimeoutException();
            JsonObject observed = pending.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
            JsonObject payload = new JsonObject();
            payload.add("result", observed);
            return result(payload, false);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RpcException) {
                RpcException error = (RpcException) cause;
                String outcome = OUTCOMES.contains(error.outcome) ? error.outcome : "unknown";
                return error(error.code, error.getMessage(), outcome);
            }
            return error("internal_error", "Host operation failed. Inspect state before another write.", "unknown");
        } catch (TimeoutException timeout) {
            return error("deadline_exceeded", "Outcome unknown. Inspect state before another write.", "unknown");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return error("unavailable", "Host response interrupted. Inspect state before another write.", "unknown");
        } catch (RuntimeException failure) {
            return error("internal_error", "Could not deliver host result. Inspect state before another write.", "unknown");
        }
    }

    static JsonObject error(String code, String message, String outcome) {
        JsonObject detail = new JsonObject();
        detail.addProperty("code", code);
        detail.addProperty("message", message);
        detail.addProperty("outcome", outcome);
        JsonObject payload = new JsonObject();
        payload.add("error", detail);
        return result(payload, true);
    }

    private static JsonObject result(JsonObject payload, boolean isError) {
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", BoundedJson.serialize(payload, McpHttpServer.MAX_BYTES,
            "response_too_large", "Host result exceeded the MCP response budget."));
        JsonArray content = new JsonArray();
        content.add(text);
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.add("structuredContent", payload);
        result.addProperty("isError", isError);
        return result;
    }
}
