package com.example;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.*;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.milo.opcua.sdk.server.AccessContext;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;


public class LegacyMachineNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:com:example:legacy-machine";
    private final SubscriptionModel subscriptionModel;

    private final LegacyMachineSimulator simulator;
    private final LegacyMachineSimulator simulator2;
    private final List<UaNode> customNodes = new ArrayList<>();

    public LegacyMachineSimulator getSimulator() {
        return simulator;
    }

    public List<UaNode> getCustomNodes() {
        return customNodes;
    }

    public LegacyMachineNamespace(OpcUaServer server, LegacyMachineSimulator simulator) {
        super(server, NAMESPACE_URI);
        this.simulator = simulator;
        this.simulator2 = new LegacyMachineSimulator("FeederController-Prototype-02");
        this.subscriptionModel = new SubscriptionModel(server, this);
        getLifecycleManager().addLifecycle(subscriptionModel);
        getLifecycleManager().addStartupTask(this::createNodes);
    }

    private void createNodes() {
        System.out.println("Creating Semantic Domain Modelling namespace nodes...");

        // ── Shared type definitions (created once, reused by both instances) ──────
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId feederControllerTypeId =
                defineFeederControllerType();
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId extendedTypeId =
                defineExtendedFeederControllerType(feederControllerTypeId);
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId extendedVariantBTypeId =
                defineExtendedFeederControllerTypeVariantB(extendedTypeId);
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId maintenanceAlarmCapableTypeId =
                defineMaintenanceAlarmCapableFeederControllerType(extendedVariantBTypeId);
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId measurementVariableTypeId =
                defineMeasurementVariableType();
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId healthIndicatorTypeId =
                defineHealthIndicatorType();

        createMachineInstance("SemanticMachine_1", simulator, extendedTypeId,
                healthIndicatorTypeId, measurementVariableTypeId,false);
        createMachineInstance("SemanticMachine_2", simulator2, maintenanceAlarmCapableTypeId,
                healthIndicatorTypeId, measurementVariableTypeId,true);
    }

    // ── Type definition: FeederControllerType (subtype of BaseObjectType) ────────
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineFeederControllerType() {
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId = newNodeId("FeederControllerType");

        UaObjectTypeNode type = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("FeederControllerType"))
                .setDisplayName(LocalizedText.english("FeederControllerType"))
                .setIsAbstract(false)
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, NodeIds.BaseObjectType.expanded(), false));

        return typeId;
    }

    // ── Type definition: ExtendedFeederControllerType (subtype of FeederControllerType)
    // Adds MaintenanceMode as a site-specific extension. Used by SemanticMachine_1.
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineExtendedFeederControllerType(
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId feederControllerTypeId) {

        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId =
                newNodeId("ExtendedFeederControllerType");

        UaObjectTypeNode type = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("ExtendedFeederControllerType"))
                .setDisplayName(LocalizedText.english("ExtendedFeederControllerType"))
                .setIsAbstract(false)
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, feederControllerTypeId.expanded(), false));

        return typeId;
    }

    // ── Type definition: ExtendedFeederControllerType_VariantB ───────────────────
    // A second, sibling specialisation of FeederControllerType. Its existence
    // demonstrates that the base type supports multiple independent extensions
    // rather than a single unused abstraction. Used by SemanticMachine_2.
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineExtendedFeederControllerTypeVariantB(
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId extendedFeederControllerTypeId) {

        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId =
                newNodeId("ExtendedFeederControllerType_VariantB");

        UaObjectTypeNode type = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("ExtendedFeederControllerType_VariantB"))
                .setDisplayName(LocalizedText.english("ExtendedFeederControllerType_VariantB"))
                .setIsAbstract(false)
                .setDescription(LocalizedText.english("Second-generation specialisation extending ExtendedFeederControllerType " +
                        "by one further inheritance level."))
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, extendedFeederControllerTypeId.expanded(), false));

        return typeId;
    }

    // ── Type definition: MeasurementVariableType (subtype of BaseDataVariableType)
    // A reusable semantic VariableType applied to Temperature in both instances.
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineMeasurementVariableType() {
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId =
                newNodeId("MeasurementVariableType");

        UaVariableTypeNode type = UaVariableTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("MeasurementVariableType"))
                .setDisplayName(LocalizedText.english("MeasurementVariableType"))
                .setDataType(NodeIds.Double)
                .setIsAbstract(false)
                .setDescription(LocalizedText.english(
                        "Reusable semantic VariableType for measured physical quantities, " +
                                "enabling consistent typing and engineering-unit annotation " +
                                "across device instances."))
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, NodeIds.BaseDataVariableType.expanded(), false));

        return typeId;
    }

    // ── Type definition: HealthIndicatorType (subtype of BaseObjectType) ─────────
    // Instantiated independently in both Status and Diagnostics domains of each
    // machine, with no reference between the two instances — demonstrates type
    // reuse without introducing cross-domain coupling.
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineHealthIndicatorType() {
        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId =
                newNodeId("HealthIndicatorType");

        UaObjectTypeNode type = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("HealthIndicatorType"))
                .setDisplayName(LocalizedText.english("HealthIndicatorType"))
                .setIsAbstract(false)
                .setDescription(LocalizedText.english(
                        "Reusable semantic ObjectType representing an aggregated health indicator, " +
                                "independently instantiable across functional domains without " +
                                "cross-domain coupling."))
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, NodeIds.BaseObjectType.expanded(), false));

        return typeId;
    }
    // ── Type definition: MaintenanceAlarmCapableFeederControllerType ─────────────
    // Sₑ extension scenario: adds the MaintenanceAlarm capability by extending the
    // type hierarchy one level further, rather than modifying any previously-
    // established type. Subtype of ExtendedFeederControllerType_VariantB.
    // Used exclusively by SemanticMachine_2 — SemanticMachine_1 and all four
    // pre-existing types remain completely unaffected.
    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId defineMaintenanceAlarmCapableFeederControllerType(
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId extendedVariantBTypeId) {

        org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeId =
                newNodeId("MaintenanceAlarmCapableFeederControllerType");

        UaObjectTypeNode type = UaObjectTypeNode.builder(getNodeContext())
                .setNodeId(typeId)
                .setBrowseName(newQualifiedName("MaintenanceAlarmCapableFeederControllerType"))
                .setDisplayName(LocalizedText.english("MaintenanceAlarmCapableFeederControllerType"))
                .setIsAbstract(false)
                .setDescription(LocalizedText.english(
                        "Sₑ extension: specialises ExtendedFeederControllerType_VariantB to add " +
                                "maintenance-alarm capability, without modifying any existing type definition."))
                .build();

        getNodeManager().addNode(type);
        customNodes.add(type);

        type.addReference(new Reference(
                typeId, NodeIds.HasSubtype, extendedVariantBTypeId.expanded(), false));

        return typeId;
    }

    // ── Per-instance machine construction ─────────────────────────────────────────
    private void createMachineInstance(
            String rootName,
            LegacyMachineSimulator simulatorInstance,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeDefinitionId,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId healthIndicatorTypeId,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId measurementVariableTypeId,
            boolean withMaintenanceAlarm) {

        System.out.println("Creating instance " + rootName + "...");

        UaObjectNode machineNode = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(rootName))
                .setBrowseName(newQualifiedName(rootName))
                .setDisplayName(LocalizedText.english(rootName))
                .setTypeDefinition(typeDefinitionId)
                .build();

        getNodeManager().addNode(machineNode);
        customNodes.add(machineNode);

        machineNode.addReference(new Reference(
                machineNode.getNodeId(), NodeIds.Organizes, NodeIds.ObjectsFolder.expanded(), false));

        UaObjectNode statusFolder        = createFolder(machineNode, rootName, "Status");
        UaObjectNode diagnosticsFolder   = createFolder(machineNode, rootName, "Diagnostics");
        UaObjectNode configurationFolder = createFolder(machineNode, rootName, "Configuration");
        UaObjectNode identityFolder      = createFolder(machineNode, rootName, "Identity");
        UaObjectNode commandsObject      = createObject(machineNode, rootName, "Commands");

        // ── Status domain ─────────────────────────────────────────────────────────
        final UaVariableNode currentStateNode = addVariable(
                statusFolder, rootName + "/Status",
                "CurrentState", NodeIds.String, simulatorInstance.getCurrentState().name());
        addVariable(statusFolder, rootName + "/Status",
                "IsRunning", NodeIds.Boolean, simulatorInstance.isRunning());
        addVariable(statusFolder, rootName + "/Status",
                "IsIdle", NodeIds.Boolean, simulatorInstance.isIdle());
        addVariable(statusFolder, rootName + "/Status",
                "HasFault", NodeIds.Boolean, simulatorInstance.hasFault());
        addVariable(statusFolder, rootName + "/Status",
                "CycleActive", NodeIds.Boolean, simulatorInstance.isCycleActive());
        addVariable(statusFolder, rootName + "/Status",
                "OperationMode", NodeIds.String, simulatorInstance.getOperationMode());

        UaVariableNode temperatureNode = addVariable(
                statusFolder, rootName + "/Status",
                "Temperature", NodeIds.Double, simulatorInstance.getTemperature(),
                measurementVariableTypeId);
        addProperty(temperatureNode, rootName + "/Status/Temperature",
                "EngineeringUnits", NodeIds.String, "DegreesCelsius");
        temperatureNode.setDescription(LocalizedText.english(
                "Measured device temperature, typed via the reusable MeasurementVariableType."));

        addVariable(statusFolder, rootName + "/Status",
                "ConnectionHealth", NodeIds.String, simulatorInstance.getConnectionHealth());

        createHealthIndicator(statusFolder, rootName + "/Status", healthIndicatorTypeId,
                computeHealthScore(simulatorInstance),
                "Aggregated health indicator derived from operational status parameters.");
        if (withMaintenanceAlarm) {
            addVariable(statusFolder, rootName + "/Status",
                    "MaintenanceAlarmActive", NodeIds.Boolean, false);
        }

        // ── Diagnostics domain ────────────────────────────────────────────────────
        addVariable(diagnosticsFolder, rootName + "/Diagnostics",
                "ErrorCode", NodeIds.Int32, simulatorInstance.getErrorCode());
        addVariable(diagnosticsFolder, rootName + "/Diagnostics",
                "WarningCode", NodeIds.Int32, simulatorInstance.getWarningCode());
        addVariable(diagnosticsFolder, rootName + "/Diagnostics",
                "CommunicationRetryCounter", NodeIds.Int32, simulatorInstance.getCommunicationRetryCounter());
        addVariable(diagnosticsFolder, rootName + "/Diagnostics",
                "UptimeSeconds", NodeIds.Int64, simulatorInstance.getUptimeSeconds());

        createHealthIndicator(diagnosticsFolder, rootName + "/Diagnostics", healthIndicatorTypeId,
                computeHealthScore(simulatorInstance),
                "Aggregated health indicator derived from diagnostic and fault parameters.");

        // ── Configuration domain ──────────────────────────────────────────────────
        UaVariableNode targetSpeedNode = addVariable(
                configurationFolder, rootName + "/Configuration",
                "TargetSpeed", NodeIds.Double, simulatorInstance.getTargetSpeed());
        addProperty(targetSpeedNode, rootName + "/Configuration/TargetSpeed",
                "EngineeringUnits", NodeIds.String, "RPM");
        targetSpeedNode.setDescription(LocalizedText.english(
                "Configured target rotational speed for the feeder mechanism."));

        addVariable(configurationFolder, rootName + "/Configuration",
                "AccelerationLimit", NodeIds.Double, simulatorInstance.getAccelerationLimit());
        addVariable(configurationFolder, rootName + "/Configuration",
                "Timeout", NodeIds.Int32, simulatorInstance.getTimeout());
        addVariable(configurationFolder, rootName + "/Configuration",
                "RetryCount", NodeIds.Int32, simulatorInstance.getRetryCount());
        addVariable(configurationFolder, rootName + "/Configuration",
                "Threshold", NodeIds.Double, simulatorInstance.getThreshold());
        if (withMaintenanceAlarm) {
            addVariable(configurationFolder, rootName + "/Configuration",
                    "MaintenanceAlarmThreshold", NodeIds.Double, 80.0);
        }

        // ── Identity domain ───────────────────────────────────────────────────────
        addVariable(identityFolder, rootName + "/Identity",
                "DeviceIdentity", NodeIds.String, simulatorInstance.getDeviceIdentity());
        addVariable(identityFolder, rootName + "/Identity",
                "MaintenanceMode", NodeIds.Boolean, false);

        // ── Commands domain (typed Object structure) ──────────────────────────────
        final Runnable updateCurrentState = () -> currentStateNode.setValue(
                new DataValue(new Variant(simulatorInstance.getCurrentState().name())));

        addMethod(commandsObject, rootName + "/Commands", "Start",
                () -> { simulatorInstance.start();  updateCurrentState.run(); });
        addMethod(commandsObject, rootName + "/Commands", "Stop",
                () -> { simulatorInstance.stop();   updateCurrentState.run(); });
        addMethod(commandsObject, rootName + "/Commands", "Reset",
                () -> { simulatorInstance.reset();  updateCurrentState.run(); });
        addMethod(commandsObject, rootName + "/Commands", "Pause",
                () -> { simulatorInstance.pause();  updateCurrentState.run(); });
        addMethod(commandsObject, rootName + "/Commands", "Resume",
                () -> { simulatorInstance.resume(); updateCurrentState.run(); });
        addMethod(commandsObject, rootName + "/Commands", "Home",
                () -> { simulatorInstance.home();   updateCurrentState.run(); });
    }

    // ── Helper: instantiate HealthIndicatorType under a given domain folder ──────
    private void createHealthIndicator(
            UaObjectNode parent, String parentPath,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId healthIndicatorTypeId,
            double healthScore, String description) {

        UaObjectNode node = UaObjectNode.builder(getNodeContext())
                .setNodeId(newNodeId(parentPath + "/HealthIndicator"))
                .setBrowseName(newQualifiedName("HealthIndicator"))
                .setDisplayName(LocalizedText.english("HealthIndicator"))
                .setTypeDefinition(healthIndicatorTypeId)
                .build();

        getNodeManager().addNode(node);
        customNodes.add(node);
        parent.addComponent(node);

        node.setDescription(LocalizedText.english(description));

        addVariable(node, parentPath + "/HealthIndicator", "HealthScore", NodeIds.Double, healthScore);
        addProperty(node, parentPath + "/HealthIndicator", "Description", NodeIds.String, description);
    }

    private double computeHealthScore(LegacyMachineSimulator sim) {
        return sim.hasFault() ? 40.0 : 95.0;
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
        customNodes.add(node);
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
        customNodes.add(node);
        parent.addComponent(node);
        return node;
    }

    // ── Helper: Variable node (default BaseDataVariableType) ──────────────────────
    private UaVariableNode addVariable(
            UaObjectNode parent, String parentPath, String name,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId dataType, Object value) {
        return addVariable(parent, parentPath, name, dataType, value, NodeIds.BaseDataVariableType);
    }

    // ── Helper: Variable node with explicit VariableType (e.g. MeasurementVariableType)
    private UaVariableNode addVariable(
            UaObjectNode parent, String parentPath, String name,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId dataType, Object value,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId typeDefinition) {

        UaVariableNode node = UaVariableNode.build(
                getNodeContext(),
                builder -> builder
                        .setNodeId(newNodeId(parentPath + "/" + name))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(dataType)
                        .setTypeDefinition(typeDefinition)
                        .build()
        );
        node.setValue(new DataValue(new Variant(value)));
        getNodeManager().addNode(node);
        customNodes.add(node);
        parent.addComponent(node);
        return node;
    }

    // ── Helper: descriptive Property (PropertyType), attachable to Objects or Variables
    private UaVariableNode addProperty(
            UaNode parent, String parentPath, String name,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId dataType, Object value) {

        UaVariableNode node = UaVariableNode.build(
                getNodeContext(),
                builder -> builder
                        .setNodeId(newNodeId(parentPath + "/" + name))
                        .setAccessLevel(AccessLevel.READ_ONLY)
                        .setUserAccessLevel(AccessLevel.READ_ONLY)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(dataType)
                        .setTypeDefinition(NodeIds.PropertyType)
                        .build()
        );
        node.setValue(new DataValue(new Variant(value)));
        getNodeManager().addNode(node);
        customNodes.add(node);

        node.addReference(new Reference(
                node.getNodeId(), NodeIds.HasProperty, parent.getNodeId().expanded(), false));

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
        customNodes.add(method);
        parent.addComponent(method);
    }

    @Override public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }
    @Override public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }
    @Override public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }
    @Override public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }
}