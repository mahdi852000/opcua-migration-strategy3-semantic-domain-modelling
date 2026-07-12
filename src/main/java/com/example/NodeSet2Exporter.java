package com.example;

import org.eclipse.milo.opcua.sdk.server.nodes.*;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.time.Instant;
import java.util.List;

/**
 * Generates a NodeSet2-compatible XML file from the list of custom UaNode
 * objects created by a strategy namespace. Only nodes belonging to the
 * custom namespace (ns=1) are written; standard OPC UA infrastructure
 * nodes are excluded.
 *
 * Usage: call NodeSet2Exporter.export(namespace.getCustomNodes(),
 *        namespace.getNamespaceUri(), "output/nodeset2.xml")
 * after the server has started and createNodes() has completed.
 */
public class NodeSet2Exporter {

    private static final String NODESET2_NS =
            "http://opcfoundation.org/UA/2011/03/UANodeSet.xsd";

    // Reference type NodeIds used for attribute lookup in generated XML
    private static final NodeId HAS_TYPE_DEFINITION = NodeIds.HasTypeDefinition;
    private static final NodeId HAS_COMPONENT       = NodeIds.HasComponent;
    private static final NodeId HAS_PROPERTY        = NodeIds.HasProperty;
    private static final NodeId HAS_SUBTYPE         = NodeIds.HasSubtype;
    private static final NodeId ORGANIZES           = NodeIds.Organizes;

    private static String exportReferenceType(NodeId referenceTypeId) {
        if (referenceTypeId.equals(NodeIds.HasTypeDefinition)) {
            return "HasTypeDefinition";
        }
        if (referenceTypeId.equals(NodeIds.HasComponent)) {
            return "HasComponent";
        }
        if (referenceTypeId.equals(NodeIds.HasProperty)) {
            return "HasProperty";
        }
        if (referenceTypeId.equals(NodeIds.HasSubtype)) {
            return "HasSubtype";
        }
        if (referenceTypeId.equals(NodeIds.Organizes)) {
            return "Organizes";
        }

        return referenceTypeId.toParseableString();
    }

    /**
     * Serialise the supplied node list to a NodeSet2 XML file.
     *
     * @param nodes        all UaNode objects added to the custom namespace
     * @param namespaceUri the namespace URI (e.g. "urn:com:example:legacy-machine")
     * @param outputPath   destination file path
     */
    public static void export(List<UaNode> nodes,
                              String namespaceUri,
                              String outputPath) throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().newDocument();

        // ── Root <UANodeSet> ──────────────────────────────────────────────────────
        Element root = doc.createElementNS(NODESET2_NS, "UANodeSet");
        root.setAttribute("xmlns:xsi",
                "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xmlns:uax",
                "http://opcfoundation.org/UA/2008/02/Types.xsd");
        root.setAttribute("LastModified", Instant.now().toString());
        doc.appendChild(root);

        // ── <NamespaceUris> ───────────────────────────────────────────────────────
        Element nsUris = doc.createElement("NamespaceUris");
        Element uri    = doc.createElement("Uri");
        uri.setTextContent(namespaceUri);
        nsUris.appendChild(uri);
        root.appendChild(nsUris);


        Element aliases = doc.createElement("Aliases");

        addAlias(doc, aliases, "HasTypeDefinition", "i=40");
        addAlias(doc, aliases, "Organizes", "i=35");
        addAlias(doc, aliases, "HasComponent", "i=47");
        addAlias(doc, aliases, "HasProperty", "i=46");
        addAlias(doc, aliases, "HasSubtype", "i=45");

        root.appendChild(aliases);

        int runtimeCustomNs = nodes.stream()
                .map(n -> n.getNodeId().getNamespaceIndex().intValue())
                .filter(ns -> ns != 0)
                .findFirst()
                .orElse(1);

        // ── One XML element per custom node ───────────────────────────────────────
        for (UaNode node : nodes) {
            Element element = buildElement(doc, node, runtimeCustomNs);
            if (element != null) {
                root.appendChild(element);
            }
        }

        // ── Write to file ─────────────────────────────────────────────────────────
        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc),
                new StreamResult(outFile));

        System.out.println("[NodeSet2Exporter] Written to: "
                + outFile.getAbsolutePath()
                + "  (" + nodes.size() + " nodes)");


    }
    private static String exportNodeId(NodeId nodeId, int runtimeCustomNs) {
        String text = nodeId.toParseableString();
        String runtimePrefix = "ns=" + runtimeCustomNs + ";";

        if (text.startsWith(runtimePrefix)) {
            return "ns=1;" + text.substring(runtimePrefix.length());
        }

        return text;
    }

    private static String exportExpandedNodeId(ExpandedNodeId expandedNodeId,
                                               UaNode contextNode,
                                               int runtimeCustomNs) {
        return expandedNodeId
                .toNodeId(contextNode.getNodeContext().getNamespaceTable())
                .map(nodeId -> exportNodeId(nodeId, runtimeCustomNs))
                .orElse(expandedNodeId.toParseableString());
    }

    private static NodeId findParentNodeId (UaNode node) {
        for (Reference ref : node.getReferences()) {
            if (!ref.isForward() && isParentReference(ref.getReferenceTypeId())) {
                return ref.getTargetNodeId().toNodeId(node.getNodeContext().getNamespaceTable())
                        .orElse(null);
            }
        } return null;
    }
    private static boolean isParentReference(NodeId referenceTypeId) {
        return referenceTypeId.equals(NodeIds.HasComponent)
                || referenceTypeId.equals(NodeIds.Organizes)
                || referenceTypeId.equals(NodeIds.HasProperty);
              //  || referenceTypeId.equals(NodeIds.HasSubtype);
    }

    // ── Build the correct XML element for each OPC UA node class ─────────────────
    private static Element buildElement(Document doc, UaNode node, int runtimeCustomNs) {

        String tag;
        if      (node instanceof UaObjectTypeNode)   tag = "UAObjectType";
        else if (node instanceof UaVariableTypeNode) tag = "UAVariableType";
        else if (node instanceof UaObjectNode)       tag = "UAObject";
        else if (node instanceof UaVariableNode)     tag = "UAVariable";
        else if (node instanceof UaMethodNode)       tag = "UAMethod";
        else return null;

        Element element = doc.createElement(tag);

        // NodeId and BrowseName are mandatory attributes
        element.setAttribute("NodeId",exportNodeId(node.getNodeId(),runtimeCustomNs));
       /* element.setAttribute("NodeId",
                node.getNodeId().toParseableString());*/
       /* element.setAttribute("BrowseName",
                node.getBrowseName().getNamespaceIndex()
                + ":" + node.getBrowseName().getName());*/
        int browseNs = node.getBrowseName().getNamespaceIndex().intValue();
        int exportBrowseNs = (browseNs == runtimeCustomNs) ? 1 : browseNs;

        element.setAttribute("BrowseName", exportBrowseNs + ":" + node.getBrowseName().getName());


        NodeId parentNodeId = findParentNodeId(node);
      /*  if (parentNodeId !=null) {
            element.setAttribute("ParentNodeId", exportNodeId(parentNodeId,runtimeCustomNs));
        }*/

        if (!(node instanceof UaObjectTypeNode)) {
            NodeId parentId = findParentNodeId(node);
            if (parentId != null) {
                assert parentNodeId != null;
                element.setAttribute("ParentNodeId",exportNodeId(parentNodeId,runtimeCustomNs));
            }
        }

        // <DisplayName>
        Element displayName = doc.createElement("DisplayName");
        displayName.setTextContent(node.getDisplayName().getText());
        element.appendChild(displayName);

        // <Description> — optional but good practice for semantic strategies
        if (node.getDescription() != null
                && node.getDescription().getText() != null
                && !node.getDescription().getText().isEmpty()) {
            Element desc = doc.createElement("Description");
            desc.setTextContent(node.getDescription().getText());
            element.appendChild(desc);
        }

        // <References> — all forward and inverse references on the node
        Element refsElem = doc.createElement("References");
        for (Reference ref : node.getReferences()) {
            Element refElem = doc.createElement("Reference");
            refElem.setAttribute("ReferenceType",
                    exportReferenceType(ref.getReferenceTypeId()));
            if (!ref.isForward()) {
                refElem.setAttribute("IsForward", "false");
            }
            refElem.setTextContent(
                    exportExpandedNodeId(ref.getTargetNodeId(),node, runtimeCustomNs));
            refsElem.appendChild(refElem);
        }
        element.appendChild(refsElem);

        // ── Variable-specific attributes ──────────────────────────────────────────
        if (node instanceof UaVariableNode) {
            UaVariableNode v = (UaVariableNode) node;
            if (v.getDataType() != null) {
                element.setAttribute("DataType",
                        v.getDataType().toParseableString());
            }
            element.setAttribute("ValueRank", "-1"); // scalar
            if (v.getAccessLevel() != null) {
                element.setAttribute("AccessLevel",
                        String.valueOf(v.getAccessLevel().intValue()));
            }
        }

        if (node instanceof UaVariableTypeNode) {
            UaVariableTypeNode vt = (UaVariableTypeNode) node;
            if (vt.getDataType() != null) {
                element.setAttribute("DataType", vt.getDataType().toParseableString());
            }
            element.setAttribute("ValueRank", "-1");
        }

        // ── Method-specific attributes ────────────────────────────────────────────
        if (node instanceof UaMethodNode) {
            UaMethodNode m = (UaMethodNode) node;
            element.setAttribute("Executable",
                    String.valueOf(m.isExecutable()));
        }
        return element;


    } private static void addAlias(Document doc, Element aliases,
                                   String alias, String nodeId) {
        Element a = doc.createElement("Alias");
        a.setAttribute("Alias", alias);
        a.setTextContent(nodeId);
        aliases.appendChild(a);
    }
}
