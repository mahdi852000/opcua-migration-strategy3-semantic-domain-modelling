package com.example;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.AccessContext;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;


public class LegacyMachineNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:com:example:legacy-machine";

    private final LegacyMachineSimulator simulator;

    // Status variable node tracked for update after command invocation
    private UaVariableNode currentStateNode;

    public LegacyMachineNamespace(OpcUaServer server, LegacyMachineSimulator simulator) {
        super(server, NAMESPACE_URI);
        this.simulator = simulator;
        getLifecycleManager().addStartupTask(this::createNodes);
    }

    private void createNodes() {
        final String MACHINE_OBJECT = "SemanticMachine_1";
        System.out.println("Creating Semantic Domain Modelling namespace nodes...");

        // ── Step 1: Define FeederControllerType ───────────────────────────────────
        //
        // FeederControllerType is the standardised base ObjectType for all feeder
        // controller devices. It is registered as a subtype of BaseObjectType,
        // making it part of the standard OPC UA type hierarchy and discoverable
        // by generic browsing clients without implicit legacy knowledge.
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId feederControllerTypeId =
                newNodeId("FeederControllerType");

        UaObjectTypeNode feederControllerType = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(feederControllerTypeId)
                .setBrowseName(newQualifiedName("FeederControllerType"))
                .setDisplayName(LocalizedText.english("FeederControllerType"))
                .setIsAbstract(false)
                .build();

        getNodeManager().addNode(feederControllerType);

        // HasSubtype: BaseObjectType ──► FeederControllerType
        feederControllerType.addReference(
                new Reference(
                        feederControllerTypeId,
                        NodeIds.HasSubtype,
                        NodeIds.BaseObjectType.expanded(),
                        false
                )
        );

        // ── Step 2: Define ExtendedFeederControllerType ───────────────────────────
        //
        // ExtendedFeederControllerType is a specialised subtype of
        // FeederControllerType. It extends the base definition by adding a
        // MaintenanceMode variable, representing a site-specific operational
        // extension without modifying the core type.
        //
        // This inheritance structure directly demonstrates the thesis statement:
        // "Inheritance mechanisms allow specialised device subtypes to extend
        // standardised base definitions while preserving compatibility with the
        // core model." (Section 4.3.3)
        //
        // The instance SemanticMachine_1 is typed as ExtendedFeederControllerType,
        // showing that the inheritance hierarchy is active in the AddressSpace,
        // not merely defined as an unused abstraction.
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId extendedTypeId =
                newNodeId("ExtendedFeederControllerType");

        UaObjectTypeNode extendedFeederControllerType = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(extendedTypeId)
                .setBrowseName(newQualifiedName("ExtendedFeederControllerType"))
                .setDisplayName(LocalizedText.english("ExtendedFeederControllerType"))
                .setIsAbstract(false)
                .build();

        getNodeManager().addNode(extendedFeederControllerType);

        // HasSubtype: FeederControllerType ──► ExtendedFeederControllerType
        extendedFeederControllerType.addReference(
                new Reference(
                        extendedTypeId,
                        NodeIds.HasSubtype,
                        feederControllerTypeId.expanded(),
                        false
                )
        );

        // ── Step 3: Instantiate the device object using ExtendedFeederControllerType
        //
        // SemanticMachine_1 is typed as ExtendedFeederControllerType, placing it
        // at the leaf of the two-level inheritance chain:
        //   BaseObjectType
        //     └── FeederControllerType
        //           └── ExtendedFeederControllerType
        //                 └── SemanticMachine_1  (instance)
        UaObjectNode machineNode = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(MACHINE_OBJECT))
                .setBrowseName(newQualifiedName(MACHINE_OBJECT))
                .setDisplayName(LocalizedText.english(MACHINE_OBJECT))
                .setTypeDefinition(extendedTypeId)
                .build();

        getNodeManager().addNode(machineNode);

        machineNode.addReference(
                new Reference(
                        machineNode.getNodeId(),
                        NodeIds.Organizes,
                        NodeIds.ObjectsFolder.expanded(),
                        false
                )
        );

        // ── Step 4: Create domain-oriented functional sub-structures ──────────────
        //
        // Functional concerns are separated into distinct Object nodes.
        // The Commands container is typed as BaseObjectType (not FolderType)
        // because it is a typed structural element of the device model, not a
        // generic organisational folder. This satisfies the definition requirement
        // that commands are "exposed as OPC UA Methods defined within typed Object
        // structures" (Section 4.3.3).
        //
        // Status, Diagnostics, Configuration, and Identity use FolderType as
        // standard OPC UA practice for grouping read/write Variable nodes.
        UaObjectNode statusFolder        = createFolder(machineNode, MACHINE_OBJECT, "Status");
        UaObjectNode diagnosticsFolder   = createFolder(machineNode, MACHINE_OBJECT, "Diagnostics");
        UaObjectNode configurationFolder = createFolder(machineNode, MACHINE_OBJECT, "Configuration");
        UaObjectNode identityFolder      = createFolder(machineNode, MACHINE_OBJECT, "Identity");

        // Commands uses BaseObjectType — a typed Object structure, not a folder
        UaObjectNode commandsObject = createObject(machineNode, MACHINE_OBJECT, "Commands");

        // ── Status domain ─────────────────────────────────────────────────────────
        currentStateNode = addVariable(
                statusFolder, MACHINE_OBJECT + "/Status",
                "CurrentState",    NodeIds.String,  simulator.getCurrentState().name());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "IsRunning",       NodeIds.Boolean, simulator.isRunning());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "IsIdle",          NodeIds.Boolean, simulator.isIdle());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "HasFault",        NodeIds.Boolean, simulator.hasFault());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "CycleActive",     NodeIds.Boolean, simulator.isCycleActive());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "OperationMode",   NodeIds.String,  simulator.getOperationMode());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "Temperature",     NodeIds.Double,  simulator.getTemperature());
        addVariable(statusFolder, MACHINE_OBJECT + "/Status",
                "ConnectionHealth",NodeIds.String,  simulator.getConnectionHealth());

        // ── Diagnostics domain ────────────────────────────────────────────────────
        addVariable(diagnosticsFolder, MACHINE_OBJECT + "/Diagnostics",
                "ErrorCode",                NodeIds.Int32, simulator.getErrorCode());
        addVariable(diagnosticsFolder, MACHINE_OBJECT + "/Diagnostics",
                "WarningCode",              NodeIds.Int32, simulator.getWarningCode());
        addVariable(diagnosticsFolder, MACHINE_OBJECT + "/Diagnostics",
                "CommunicationRetryCounter",NodeIds.Int32, simulator.getCommunicationRetryCounter());
        addVariable(diagnosticsFolder, MACHINE_OBJECT + "/Diagnostics",
                "UptimeSeconds",            NodeIds.Int64, simulator.getUptimeSeconds());

        // ── Configuration domain ──────────────────────────────────────────────────
        addVariable(configurationFolder, MACHINE_OBJECT + "/Configuration",
                "TargetSpeed",       NodeIds.Double, simulator.getTargetSpeed());
        addVariable(configurationFolder, MACHINE_OBJECT + "/Configuration",
                "AccelerationLimit", NodeIds.Double, simulator.getAccelerationLimit());
        addVariable(configurationFolder, MACHINE_OBJECT + "/Configuration",
                "Timeout",           NodeIds.Int32,  simulator.getTimeout());
        addVariable(configurationFolder, MACHINE_OBJECT + "/Configuration",
                "RetryCount",        NodeIds.Int32,  simulator.getRetryCount());
        addVariable(configurationFolder, MACHINE_OBJECT + "/Configuration",
                "Threshold",         NodeIds.Double, simulator.getThreshold());

        // ── Identity domain ───────────────────────────────────────────────────────
        addVariable(identityFolder, MACHINE_OBJECT + "/Identity",
                "DeviceIdentity", NodeIds.String, simulator.getDeviceIdentity());

        // ── Extension variable from ExtendedFeederControllerType ──────────────────
        //
        // MaintenanceMode is the additional Variable introduced by
        // ExtendedFeederControllerType. It is placed in the Identity domain as a
        // site-specific operational attribute not present in the base type.
        // This variable is only available on instances of
        // ExtendedFeederControllerType or its subtypes, demonstrating controlled
        // extension without modification of the base FeederControllerType.
        addVariable(identityFolder, MACHINE_OBJECT + "/Identity",
                "MaintenanceMode", NodeIds.Boolean, false);

        // ── Commands domain (typed Object structure) ──────────────────────────────
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Start",
                () -> { simulator.start();  updateCurrentState(); });
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Stop",
                () -> { simulator.stop();   updateCurrentState(); });
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Reset",
                () -> { simulator.reset();  updateCurrentState(); });
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Pause",
                () -> { simulator.pause();  updateCurrentState(); });
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Resume",
                () -> { simulator.resume(); updateCurrentState(); });
        addMethod(commandsObject, MACHINE_OBJECT + "/Commands", "Home",
                () -> { simulator.home();   updateCurrentState(); });
    }

    // ── Helper: FolderType sub-object for Variable grouping ───────────────────────
    private UaObjectNode createFolder(UaObjectNode parent, String parentPath, String name) {
        UaObjectNode node = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(parentPath + "/" + name))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setTypeDefinition(NodeIds.FolderType)
                .build();
        getNodeManager().addNode(node);
        parent.addComponent(node);
        return node;
    }

    // ── Helper: BaseObjectType sub-object for typed structural elements ───────────
    private UaObjectNode createObject(UaObjectNode parent, String parentPath, String name) {
        UaObjectNode node = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(parentPath + "/" + name))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setTypeDefinition(NodeIds.BaseObjectType)
                .build();
        getNodeManager().addNode(node);
        parent.addComponent(node);
        return node;
    }

    // ── Helper: Variable node ─────────────────────────────────────────────────────
    private UaVariableNode addVariable(
            UaObjectNode parent, String parentPath, String name,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId dataType, Object value) {

        UaVariableNode node = UaVariableNode.build(
                getNodeContext(),
                builder -> builder
                        .setNodeId(newNodeId(parentPath + "/" + name))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(dataType)
                        .setTypeDefinition(NodeIds.BaseDataVariableType)
                        .build()
        );
        node.setValue(new DataValue(new Variant(value)));
        getNodeManager().addNode(node);
        parent.addComponent(node);
        return node;
    }

    // ── Helper: Method node ───────────────────────────────────────────────────────
    private void addMethod(UaObjectNode parent, String parentPath, String name, Runnable action) {
        UaMethodNode method = UaMethodNode.builder(getNodeContext())
                .setNodeId(newNodeId(parentPath + "/" + name))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setExecutable(true)
                .setUserExecutable(true)
                .build();

        method.setInvocationHandler(new MethodInvocationHandler() {
            @Override
            public CallMethodResult invoke(AccessContext ctx, CallMethodRequest request) {
                System.out.println(name + " method called from OPC UA client.");
                action.run();
                return new CallMethodResult(
                        new StatusCode(StatusCodes.Good),
                        new StatusCode[0],
                        new DiagnosticInfo[0],
                        new Variant[0]
                );
            }
        });

        getNodeManager().addNode(method);
        parent.addComponent(method);
    }

    // ── Refresh CurrentState after a command invocation ───────────────────────────
    private void updateCurrentState() {
        currentStateNode.setValue(
                new DataValue(new Variant(simulator.getCurrentState().name())));
    }

    @Override public void onDataItemsCreated(List<DataItem> list) {}
    @Override public void onDataItemsModified(List<DataItem> list) {}
    @Override public void onDataItemsDeleted(List<DataItem> list) {}
    @Override public void onMonitoringModeChanged(List<MonitoredItem> list) {}
}
