package dev.bitwigagent;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;

/** Local, stateless Streamable HTTP transport for MCP tools. */
final class McpHttpServer implements AutoCloseable {
    static final int MAX_BYTES = 1024 * 1024;
    private static final String PATH = "/mcp";
    private static final String LATEST_VERSION = "2025-11-25";
    private static final Set<String> VERSIONS = Set.of("2025-03-26", "2025-06-18", LATEST_VERSION);
    private static final String INSTRUCTIONS = "Discover capabilities and context.get first. Indices are zero-based. "
        + "Use exact discovered targets and inspect bounded windows and verification. "
        + "Timeouts are not cancellation. Inspect unknown outcomes before another write.";
    private final McpTools tools;
    private final HttpServer server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(8), task -> {
            Thread thread = new Thread(task, "bitwig-agent-mcp-http");
            thread.setDaemon(true);
            return thread;
        });

    McpHttpServer(int port, McpTools.Handler host) throws IOException {
        tools = new McpTools(host);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.setExecutor(workers);
        server.createContext(PATH, this::receive);
    }

    void start() { server.start(); }

    int port() { return server.getAddress().getPort(); }

    private void receive(HttpExchange exchange) throws IOException {
        long receivedAt = System.nanoTime();
        try {
            if (!PATH.equals(exchange.getRequestURI().getRawPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!localRequest(exchange)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                exchange.sendResponseHeaders(405, -1); // Standalone SSE and session deletion are optional.
                return;
            }
            String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
            if (version != null && !VERSIONS.contains(version)) {
                send(exchange, 400, protocolError(JsonNull.INSTANCE, -32600, "Unsupported MCP-Protocol-Version"));
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                exchange.sendResponseHeaders(415, -1);
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                exchange.sendResponseHeaders(413, -1);
                return;
            }
            JsonElement request;
            try {
                request = StrictJson.parse(body);
            } catch (IOException | IllegalStateException invalid) {
                send(exchange, 400, protocolError(JsonNull.INSTANCE, -32700, "Invalid UTF-8 JSON"));
                return;
            }
            JsonObject response = handle(request, receivedAt);
            if (response == null) exchange.sendResponseHeaders(202, -1);
            else send(exchange, response.get("id").isJsonNull() ? 400 : 200, response);
        } finally {
            exchange.close();
        }
    }

    private boolean localRequest(HttpExchange exchange) {
        int port = server.getAddress().getPort();
        List<String> hosts = exchange.getRequestHeaders().get("Host");
        if (hosts == null || hosts.size() != 1) return false;
        String host = hosts.get(0).toLowerCase(Locale.ROOT);
        if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)
                && !(port == 80 && (host.equals("127.0.0.1") || host.equals("localhost")))) return false;
        List<String> origins = exchange.getRequestHeaders().get("Origin");
        // Browser pages on other origins must not be able to issue local DAW commands.
        return origins == null || (origins.size() == 1 && origins.get(0).equalsIgnoreCase("http://" + host));
    }

    private JsonObject handle(JsonElement message, long receivedAt) {
        if (!message.isJsonObject())
            return protocolError(JsonNull.INSTANCE, -32600, "Expected one JSON-RPC object");
        JsonObject request = message.getAsJsonObject();
        JsonElement id = request.get("id");
        if (!"2.0".equals(string(request, "jsonrpc")) || (id != null && !validId(id)))
            return protocolError(JsonNull.INSTANCE, -32600, "Invalid JSON-RPC envelope");

        String method = string(request, "method");
        if (method == null) return protocolError(JsonNull.INSTANCE, -32600, "Expected JSON-RPC method");
        JsonElement params = request.get("params");
        if (params != null && !params.isJsonObject())
            return protocolError(id == null ? JsonNull.INSTANCE : id, -32602, "params must be an object");
        if (id == null) return null; // Notifications never schedule a host operation.
        JsonObject args = params == null ? new JsonObject() : params.getAsJsonObject();
        switch (method) {
            case "initialize":
                String requested = string(args, "protocolVersion");
                if (requested == null || !object(args, "clientInfo") || !object(args, "capabilities"))
                    return protocolError(id, -32602, "Expected protocolVersion, clientInfo and capabilities");
                return reply(id, initialize(requested));
            case "ping":
                return reply(id, new JsonObject());
            case "tools/list":
                if (args.has("cursor")) return protocolError(id, -32602, "Unknown tool list cursor");
                return reply(id, tools.list());
            case "tools/call":
                String name = string(args, "name");
                if (name == null || !HostBridge.supportedMethods().contains(name))
                    return protocolError(id, -32602, "Unknown tool. Use tools/list to discover supported tools.");
                if (args.has("arguments") && !object(args, "arguments"))
                    return protocolError(id, -32602, "arguments must be an object");
                JsonObject arguments = args.has("arguments") ? args.getAsJsonObject("arguments") : new JsonObject();
                return reply(id, tools.call(name, arguments, receivedAt));
            default:
                return protocolError(id, -32601, "MCP method not found");
        }
    }

    private static JsonObject initialize(String requestedVersion) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", VERSIONS.contains(requestedVersion) ? requestedVersion : LATEST_VERSION);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);
        JsonObject info = new JsonObject();
        info.addProperty("name", "bitwig-agent");
        info.addProperty("version", BuildInfo.load().version);
        result.add("serverInfo", info);
        result.addProperty("instructions", INSTRUCTIONS);
        return result;
    }

    private static boolean validId(JsonElement id) {
        if (!id.isJsonPrimitive()) return false;
        JsonPrimitive value = id.getAsJsonPrimitive();
        String text = value.getAsString();
        return text.length() <= 128 && (value.isString() || (value.isNumber() && text.matches("-?[0-9]+")));
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : null;
    }

    private static boolean object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject();
    }

    private static JsonObject envelope(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        return response;
    }

    private static JsonObject reply(JsonElement id, JsonObject result) {
        JsonObject response = envelope(id);
        response.add("result", result);
        return response;
    }

    private static JsonObject protocolError(JsonElement id, int code, String message) {
        JsonObject response = envelope(id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static byte[] encode(JsonObject response) {
        return BoundedJson.serialize(response, MAX_BYTES, "response_too_large", "MCP response exceeds 1 MiB.")
            .getBytes(StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, JsonObject response) throws IOException {
        byte[] data;
        try {
            data = encode(response);
            if (data.length > MAX_BYTES) throw new IllegalStateException("MCP response exceeds 1 MiB");
        } catch (RpcException | IllegalStateException tooLarge) {
            JsonObject error = McpTools.error("response_too_large",
                "MCP response exceeded 1 MiB. Inspect state before another write.", "unknown");
            data = encode(reply(response.get("id"), error));
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
    }

    @Override public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
