package com.example;

import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;

public class LegacyOpcUaGateway {
    private final LegacyMachineSimulator simulator;
    private final LegacyMachineNamespace namespace;
    private static final int TCP_BIND_PORT = 4843;

    public final OpcUaServer server;

    public LegacyOpcUaGateway() {

        Set<EndpointConfig> endpoints = createEndpointConfigs();

        OpcUaServerConfig config = OpcUaServerConfig.builder()
                .setApplicationName(LocalizedText.english("LegacyMachine OPC UA Gateway"))
                .setApplicationUri("urn:com:example:legacy-machine-gateway")
                .setProductUri("urn:com:example:legacy-machine")
                .setBuildInfo(new BuildInfo(
                        "urn:com:example:legacy-machine",
                        "com.example",
                        "LegacyMachine OPC UA Gateway",
                        OpcUaServer.SDK_VERSION,
                        "",
                        DateTime.now()
                ))
                .setEndpoints(endpoints)
                .setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
                .build();

        server = new OpcUaServer(
                config,
                transportProfile -> {
                    if (transportProfile != TransportProfile.TCP_UASC_UABINARY) {
                        throw new IllegalArgumentException("Unsupported transport profile: " + transportProfile);
                    }

                    OpcTcpServerTransportConfig transportConfig =
                            OpcTcpServerTransportConfig.newBuilder().build();

                    return new OpcTcpServerTransport(transportConfig);
                }
        );
        simulator = new LegacyMachineSimulator();
        namespace = new LegacyMachineNamespace(server, simulator);
        namespace.startup();
    }

    private Set<EndpointConfig> createEndpointConfigs() {
        Set<EndpointConfig> endpoints = new LinkedHashSet<>();

        EndpointConfig endpoint = EndpointConfig.newBuilder()
                .setBindAddress("0.0.0.0")
                .setHostname("127.0.0.1")
                .setBindPort(TCP_BIND_PORT)
                .setPath("/semantic-domain")
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None)
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                .addTokenPolicies(USER_TOKEN_POLICY_ANONYMOUS)
                .build();

        endpoints.add(endpoint);

        return endpoints;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        return server.shutdown();
    }
}