package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

public class Servidor implements Runnable {
    private final int PORT;
    private final boolean isWriter;

    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;

    private final int DATACENTER_PORT = 4447;
    private final String DATACENTER_GROUP = "231.0.0.0";

    private final int DATABASE_PORT = 4448;
    private final String DATABASE_GROUP = "232.0.0.0";

    private final Set<Socket> connectedClients = ConcurrentHashMap.newKeySet();

    MulticastSocket multicastDatacenterServer;
    MulticastSocket multicastDatabaseServer;

    public Servidor(int port, boolean isWriter) {
        this.PORT = port;
        this.isWriter = isWriter;
        this.clientThreadPool = Executors.newFixedThreadPool(10);
    }

    public Servidor(int port) {
        this.PORT = port;
        this.isWriter = false;
        this.clientThreadPool = Executors.newFixedThreadPool(10);
    }

    @Override
    public void run() {
        try {
            initializeServer();
            registerWithLocServer();
            new Thread(this::datacenterListener).start();
            new Thread(this::startAcceptingClients).start();


        } catch (IOException e) {
            System.err.println("Server failed: " + e.getMessage());
        }
    }

    private void datacenterListener() {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastDatacenterServer.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                logReceivedMessage("Datacenter", message);

                if (isWriter) {
                    sendToDatabase(message);
                }

                sendToClients(message);

            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void sendToClients(String message) {
        byte[] data = (message + "\n").getBytes();

        connectedClients.removeIf(client -> {
            try {
                if (!client.isClosed()) {
                    client.getOutputStream().write(data);
                    client.getOutputStream().flush();
                    System.out.println("Data sent to client: " + client.getInetAddress() + ":" + client.getPort());
                    return false;
                }
                return true;
            } catch (IOException e) {
                return true;
            }
        });
    }

    private void sendToDatabase(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data, data.length,
                InetAddress.getByName(DATABASE_GROUP), DATABASE_PORT
        );
        multicastDatabaseServer.send(packet);
        System.out.println("Message sent to database: " + message);
    }

    private void initializeServer() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor iniciado na porta " + PORT);
        initializeConnections();
    }

    private void initializeConnections() throws IOException {
        multicastDatacenterServer = new MulticastSocket(DATACENTER_PORT);
        multicastDatacenterServer.joinGroup(InetAddress.getByName(DATACENTER_GROUP));
        System.out.println("Multicast server for datacenter initialized on group " + DATACENTER_GROUP + ":" + DATACENTER_PORT);

        multicastDatabaseServer = new MulticastSocket(DATABASE_PORT);
        multicastDatabaseServer.joinGroup(InetAddress.getByName(DATABASE_GROUP));
        System.out.println("Multicast server for database initialized on group " + DATABASE_GROUP + ":" + DATABASE_PORT);

    }


    private void registerWithLocServer() throws IOException {
        String message = "SERVER_REGISTER:" + PORT;
        byte[] data = message.getBytes();

        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("233.0.0.0"), // Grupo multicast do LocServer
                    4449 // Porta multicast do LocServer
            );
            socket.send(packet);
            System.out.println("Registrado no LocServer");
        }
    }

    private void startAcceptingClients() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientThreadPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                System.err.println("Erro ao aceitar cliente: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        connectedClients.add(clientSocket);  // Adiciona à lista de clientes

        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());
            out.println("Bem-vindo ao servidor " + PORT);

            // Mantém a conexão aberta
            while (true) {
                String input = in.readLine();
                if (input == null || input.equalsIgnoreCase("exit")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Erro com cliente: " + e.getMessage());
        } finally {
            connectedClients.remove(clientSocket);  // Remove da lista
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    private void logReceivedMessage(String source, String message) {
        System.out.printf("[Server %d][%s] received: %s\n", PORT, source, message);
    }

    private void handleError(Exception e) {
        System.err.printf("Erro no servidor %d: %s\n", PORT, e.getMessage());
        e.printStackTrace();
    }
}
