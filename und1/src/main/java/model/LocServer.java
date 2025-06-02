package model;

import datastructures.ConsistentHash;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class LocServer implements Runnable {
    private final int PORT = 50000;
    private final int LOCSERVER_PORT = 4449;
    private final String LOCSERVER_GROUP = "233.0.0.0";

    private MulticastSocket multicastLocServerServer;
    private ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private ConsistentHash<Integer> consistentHash;
    private Set<Integer> availableServers = Collections.synchronizedSet(new HashSet<>());

    public LocServer() {
        this.consistentHash = new ConsistentHash<>(10, Collections.emptyList());
    }

    @Override
    public void run() {
        try {
            initializeConnections();
            startDiscoveryListener();
            startTCPServer();
        } catch (IOException e) {
            System.err.println("LocServer initialization failed: " + e.getMessage());
        }
    }

    private void initializeConnections() throws IOException {
        multicastLocServerServer = new MulticastSocket(LOCSERVER_PORT);
        multicastLocServerServer.joinGroup(InetAddress.getByName(LOCSERVER_GROUP));
        System.out.println("LocServer initialized and ready for connections");
    }

    private void startDiscoveryListener() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastLocServerServer.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();

                    if (message.startsWith("SERVER_REGISTER:")) {
                        int serverPort = Integer.parseInt(message.substring("SERVER_REGISTER:".length()));
                        registerServer(serverPort);
                    }

                } catch (IOException e) {
                    System.err.println("Discovery listener error: " + e.getMessage());
                }
            }
        }).start();
    }

    private synchronized void registerServer(int port) {
        if (availableServers.add(port)) {
            consistentHash.add(port);
            System.out.println("Server registered: " + port);
            System.out.println("Available servers: " + availableServers);
        }
    }


    private void startTCPServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("LocServer listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleClientConnection(clientSocket));
            }
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String clientId = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            Integer selectedPort = consistentHash.get(clientId);

            String response = (selectedPort != null) ?
                    "CONNECT_TO:" + selectedPort :
                    "ERROR:No available servers";

            out.println(response);
            System.out.println("Routing client " + clientId + " to server: " + selectedPort);

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException e) { /* Ignore */ }
        }
    }
}
