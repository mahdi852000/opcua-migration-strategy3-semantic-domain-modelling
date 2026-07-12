package com.example;

public class Main {
    public static void main(String[] args) throws Exception {

        LegacyOpcUaGateway gateway = new LegacyOpcUaGateway();

        gateway.startup().get();
        System.out.println("OPC UA Gateway Started.");
        System.out.println("Endpoint: opc.tcp://127.0.0.1:4843/semantic-domain");

        Thread.sleep(500);
        NodeSet2Exporter.export(
                gateway.getNamespace().getCustomNodes(),
                LegacyMachineNamespace.NAMESPACE_URI,
                "output/nodeset2.xml"
        );

        System.out.println("Press Enter to stop the server...");
        System.in.read();
        gateway.shutdown().get();
        System.out.println("OPC UA Gateway stopped.");
    }
}