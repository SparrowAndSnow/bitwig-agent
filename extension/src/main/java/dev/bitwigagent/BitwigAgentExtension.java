package dev.bitwigagent;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import java.io.IOException;

public final class BitwigAgentExtension extends ControllerExtension {
    private final ControllerHost host;
    private HostBridge bridge;
    private McpHttpServer server;

    BitwigAgentExtension(BitwigAgentExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
        this.host = host;
    }

    @Override public void init() {
        int port = configuredPort(System.getenv("BITWIG_AGENT_PORT"));
        bridge = new HostBridge(host);
        try {
            server = new McpHttpServer(port, bridge::handle);
            server.start();
            host.println("Bitwig Agent MCP listening at http://127.0.0.1:" + port + "/mcp");
        } catch (IOException | RuntimeException error) {
            exit();
            throw new IllegalStateException("Cannot start Bitwig Agent MCP server", error);
        }
    }

    static int configuredPort(String value) {
        if (value == null) return 61172;
        if (!value.matches("[0-9]{1,5}"))
            throw new IllegalArgumentException("BITWIG_AGENT_PORT must be an ASCII decimal port in 1..65535");
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("BITWIG_AGENT_PORT must be in 1..65535");
        return port;
    }

    @Override public void exit() {
        if (bridge != null) bridge.close();
        if (server != null) server.close();
        server = null;
        bridge = null;
    }

    @Override public void flush() { if (bridge != null) bridge.flush(); }
}
