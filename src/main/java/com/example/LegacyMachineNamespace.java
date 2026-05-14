package com.example;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;

import java.util.List;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.AccessContext;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;


public class LegacyMachineNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:com:example:legacy-machine";

    private final LegacyMachineSimulator simulator;
    private UaVariableNode currentStateNode;

    public LegacyMachineNamespace(OpcUaServer server, LegacyMachineSimulator simulator) {
        super(server, NAMESPACE_URI);

        this.simulator = simulator;

        getLifecycleManager().addStartupTask(this::createNodes);
    }
    private void createNodes() {
        final String MACHINE_OBJECT = "SemanticMachine_1";
        System.out.println("Creating LegacyMachine namespace nodes...");

        UaObjectNode machineNode = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT))
                .setBrowseName(newQualifiedName(MACHINE_OBJECT))
                .setDisplayName(LocalizedText.english(MACHINE_OBJECT))
                .setTypeDefinition(NodeIds.BaseObjectType)
                .build();

        getNodeManager().addNode(machineNode);

        machineNode.addReference(
                new org.eclipse.milo.opcua.sdk.core.Reference(
                        machineNode.getNodeId(),
                        NodeIds.Organizes,
                        NodeIds.ObjectsFolder.expanded(),
                        false
                )
        );
        UaObjectNode statusFolder = createFolder(machineNode, MACHINE_OBJECT,"Status");
        UaObjectNode diagnosticsFolder = createFolder(machineNode, MACHINE_OBJECT,"Diagnostics");
        UaObjectNode configurationFolder = createFolder(machineNode, MACHINE_OBJECT,"Configuration");
        UaObjectNode identityFolder = createFolder(machineNode, MACHINE_OBJECT, "Identity");
        UaObjectNode commandsFolder = createFolder(machineNode, MACHINE_OBJECT,"Commands");

        currentStateNode = UaVariableNode.build(
                getNodeContext(),
                builder->builder
                        .setNodeId(newNodeId(MACHINE_OBJECT + "/Status/CurrentState"))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName( "CurrentState"))
                        .setDisplayName(LocalizedText.english("CurrentState"))
                        .setDataType(NodeIds.String)
                        .setTypeDefinition(NodeIds.BaseDataVariableType)
                        .build()
        );

        currentStateNode.setValue(
                new DataValue(
                        new Variant(simulator.getCurrentState().name())
                )
        );
        getNodeManager().addNode(currentStateNode);
        statusFolder.addComponent(currentStateNode);
        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "IsRunning",
                NodeIds.Boolean,
                simulator.isRunning()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "IsIdle",
                NodeIds.Boolean,
                simulator.isIdle()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "HasFault",
                NodeIds.Boolean,
                simulator.hasFault()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "OperationMode",
                NodeIds.String,
                simulator.getOperationMode()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "Temperature",
                NodeIds.Double,
                simulator.getTemperature()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "ConnectionHealth",
                NodeIds.String,
                simulator.getConnectionHealth()
        );

        addVariable(
                statusFolder,
                MACHINE_OBJECT + "/Status",
                "CycleActive",
                NodeIds.Boolean,
                simulator.isCycleActive()
        );

        addVariable(
                diagnosticsFolder,
                MACHINE_OBJECT + "/Diagnostics",
                "ErrorCode",
                NodeIds.Int32,
                simulator.getErrorCode()
        );

        addVariable(
                diagnosticsFolder,
                MACHINE_OBJECT + "/Diagnostics",
                "WarningCode",
                NodeIds.Int32,
                simulator.getWarningCode()
        );

        addVariable(
                diagnosticsFolder,
                MACHINE_OBJECT + "/Diagnostics",
                "CommunicationRetryCounter",
                NodeIds.Int32,
                simulator.getCommunicationRetryCounter()
        );

        addVariable(
                diagnosticsFolder,
                MACHINE_OBJECT + "/Diagnostics",
                "UptimeSeconds",
                NodeIds.Int64,
                simulator.getUptimeSeconds()
        );

        addVariable(
                configurationFolder,
                MACHINE_OBJECT + "/Configuration",
                "TargetSpeed",
                NodeIds.Double,
                simulator.getTargetSpeed()
        );
        addVariable(
                configurationFolder,
                MACHINE_OBJECT + "/Configuration",
                "AccelerationLimit",
                NodeIds.Double,
                simulator.getAccelerationLimit()
        );

        addVariable(
                configurationFolder,
                MACHINE_OBJECT + "/Configuration",
                "Timeout",
                NodeIds.Int32,
                simulator.getTimeout()
        );

        addVariable(
                configurationFolder,
                MACHINE_OBJECT + "/Configuration",
                "RetryCount",
                NodeIds.Int32,
                simulator.getRetryCount()
        );

        addVariable(
                configurationFolder,
                MACHINE_OBJECT + "/Configuration",
                "Threshold",
                NodeIds.Double,
                simulator.getThreshold()
        );
        addVariable(
                identityFolder,
                MACHINE_OBJECT + "/Identity",
                "DeviceIdentity",
                NodeIds.String,
                simulator.getDeviceIdentity()
        );

        UaMethodNode startMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Start"))
                .setBrowseName(newQualifiedName("Start"))
                .setDisplayName(LocalizedText.english("Start"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        startMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {
                System.out.println("Start method called from OPC UA client.");
                simulator.start();
                currentStateNode.setValue(
                        new DataValue(new Variant(simulator.getCurrentState().name()))
                );
                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(startMethod);
        commandsFolder.addComponent(startMethod);

        UaMethodNode stopMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Stop"))
                .setBrowseName(newQualifiedName("Stop"))
                .setDisplayName(LocalizedText.english("Stop"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        stopMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {

                System.out.println("Stop method called from OPC UA client.");

                simulator.stop();

                currentStateNode.setValue(
                        new DataValue(
                                new Variant(simulator.getCurrentState().name())
                        )
                );

                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(stopMethod);
        commandsFolder.addComponent(stopMethod);

        UaMethodNode resetMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Reset"))
                .setBrowseName(newQualifiedName("Reset"))
                .setDisplayName(LocalizedText.english("Reset"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        resetMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {

                System.out.println("Reset method called from OPC UA client.");

                simulator.reset();

                currentStateNode.setValue(
                        new DataValue(
                                new Variant(simulator.getCurrentState().name())
                        )
                );

                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(resetMethod);
        commandsFolder.addComponent(resetMethod);

        UaMethodNode pauseMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Pause"))
                .setBrowseName(newQualifiedName("Pause"))
                .setDisplayName(LocalizedText.english("Pause"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        pauseMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {
                System.out.println("Pause method called from OPC UA client.");

                simulator.pause();

                currentStateNode.setValue(
                        new DataValue(new Variant(simulator.getCurrentState().name()))
                );

                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(pauseMethod);
        commandsFolder.addComponent(pauseMethod);

        UaMethodNode resumeMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Resume"))
                .setBrowseName(newQualifiedName("Resume"))
                .setDisplayName(LocalizedText.english("Resume"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        resumeMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {
                System.out.println("Resume method called from OPC UA client.");

                simulator.resume();

                currentStateNode.setValue(
                        new DataValue(new Variant(simulator.getCurrentState().name()))
                );

                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(resumeMethod);
        commandsFolder.addComponent(resumeMethod);

        UaMethodNode homeMethod = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT + "/Commands/Home"))
                .setBrowseName(newQualifiedName("Home"))
                .setDisplayName(LocalizedText.english("Home"))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        homeMethod.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext accessContext, CallMethodRequest request) {
                System.out.println("Home method called from OPC UA client.");

                simulator.home();

                currentStateNode.setValue(
                        new DataValue(new Variant(simulator.getCurrentState().name()))
                );

                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(homeMethod);
        commandsFolder.addComponent(homeMethod);


    }


    private UaObjectNode createFolder(
            UaObjectNode parent,
            String parentPath,
            String name
    ) {
        UaObjectNode folderNode = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(parentPath + "/" + name))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setTypeDefinition(NodeIds.FolderType)
                .build();
        getNodeManager().addNode(folderNode);
        parent.addComponent(folderNode);

        return folderNode;
    }

    private void addVariable(
            UaObjectNode parent,
            String parentPath,
            String name,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId dataType,
            Object value
    ) {
        UaVariableNode variableNode = UaVariableNode.build(
                getNodeContext(),
                builder -> builder
                        .setNodeId(newNodeId(parentPath+ "/" + name))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(dataType)
                        .setTypeDefinition(NodeIds.BaseDataVariableType)
                        .build()
        );

        variableNode.setValue(new DataValue(new Variant(value)));

        getNodeManager().addNode(variableNode);
        parent.addComponent(variableNode);
    }


    @Override
    public void onDataItemsCreated(List<DataItem> list) {
        // Not required for PoC
    }

    @Override
    public void onDataItemsModified(List<DataItem> list) {
        // Not required for PoC
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> list) {
        // Not required for PoC
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> list) {
        // Not required for PoC
    }
}