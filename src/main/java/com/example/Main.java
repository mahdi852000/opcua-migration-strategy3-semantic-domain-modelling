package com.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {

            LegacyOpcUaGateway gateway = new LegacyOpcUaGateway();

            gateway.startup().get();
            System.out.println("OPC UA Gateway Started.");
            System.out.println("Endpoint: opc.tcp://127.0.01:4843/semantic-domain");
            System.out.println("Press Enter to stop the server...");
            System.in.read();
            gateway.shutdown().get();
            System.out.println("OPC UA Gateway stopped");
        }
    }
