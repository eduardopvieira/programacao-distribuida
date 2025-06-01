package model;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class LocServer implements Runnable {
    private final int PORT = 50000;
    private final int LOCSERVER_PORT = 4449;
    private final String LOCSERVER_GROUP = "233.0.0.0";

    private DatagramSocket responseSocket;
    private MulticastSocket multicastLocServerServer;
    private ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public LocServer() {}

    @Override
    public void run() {
        try {
            initializeConnections();
            startTCPServer();
        } catch (IOException e) {
            System.err.println("LocServer initialization failed: " + e.getMessage());
        }
    }

    private void initializeConnections() throws IOException {
        responseSocket = new DatagramSocket(5555);
        responseSocket.setSoTimeout(1000);

        multicastLocServerServer = new MulticastSocket(LOCSERVER_PORT);
        multicastLocServerServer.joinGroup(InetAddress.getByName(LOCSERVER_GROUP));

        System.out.println("LocServer initialized and ready for connections");
        System.out.println("Locserver listening on port 5555");
    }

    private void startTCPServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("LocServer listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("Accepted connection from client: " + clientAddress);

                threadPool.submit(() -> {
                    System.out.println("Processing request from " + clientAddress);
                    handleClientConnection(clientSocket);
                    System.out.println("Completed processing for " + clientAddress);
                });
            }
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println(InetAddress.getLocalHost().getHostAddress() + ":" + PORT);

            Map<Integer, Integer> serverConnections = Collections.synchronizedMap(new LinkedHashMap<>());
            requestUserCounts();
            int bestPort = waitForServerResponses(serverConnections);

            String response = bestPort != -1 ? "CONNECT_TO:" + bestPort : "ERROR:No available servers";
            out.println(response);
            System.out.println("Sent response to client: " + response);

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException e) { /* Ignore */ }
        }
    }

    private void requestUserCounts() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        responseSocket.setSoTimeout(100); // timeout pra limpar buffers

        try {
            while (true) {
                responseSocket.receive(packet); // limpa buffer
            }
        } catch (SocketTimeoutException e) {
            // buffer limpo
        }

        // enviando requisicao mullticast
        String request = "REPORT_USER_COUNT:" + responseSocket.getLocalPort();
        byte[] data = request.getBytes();
        DatagramPacket multicastPacket = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(LOCSERVER_GROUP), // 233.0.0.0
                LOCSERVER_PORT // 4449
        );
        multicastLocServerServer.send(multicastPacket);

        System.out.println("Requisição multicast enviada para " + LOCSERVER_GROUP + ":" + LOCSERVER_PORT);
    }

    private int waitForServerResponses(Map<Integer, Integer> serverConnections) {
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 2000;
        final long RESPONSE_WINDOW = 1500;
        byte[] buffer = new byte[1024];

        try {
            while (System.currentTimeMillis() - startTime < TIMEOUT) {
                try {
                    responseSocket.setSoTimeout(
                            (int) Math.max(100, TIMEOUT - (System.currentTimeMillis() - startTime))
                    );

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    responseSocket.receive(packet);

                    String response = new String(packet.getData(), 0, packet.getLength()).trim();
                    System.out.println("Received server response: " + response);

                    String[] parts = response.split("!");
                    if (parts.length == 2) {
                        int connections = Integer.parseInt(parts[0]);
                        int port = Integer.parseInt(parts[1]);
                        serverConnections.put(port, connections);
                        System.out.println("Server at port " + port + " reported " + connections + " connections");
                    }

                    if (System.currentTimeMillis() - startTime > RESPONSE_WINDOW) {
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    if (!serverConnections.isEmpty()) break;
                }
            }

            System.out.println("Collected server responses: " + serverConnections);
            return selectBestServer(serverConnections);

        } catch (Exception e) {
            System.err.println("Response error: " + e.getMessage());
            return -1;
        }
    }

    private int selectBestServer(Map<Integer, Integer> serverConnections) {
        if (serverConnections.isEmpty()) {
            System.out.println("No servers available for selection");
            return -1;
        }

        int minConnections = Collections.min(serverConnections.values());

        List<Integer> bestServers = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : serverConnections.entrySet()) {
            if (entry.getValue() == minConnections) {
                bestServers.add(entry.getKey());
            }
        }

        int selectedPort = bestServers.getFirst();
        System.out.println("Selected best server at port " + selectedPort +
                " with " + minConnections + " connections");

        return selectedPort;
    }
}
