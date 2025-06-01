package model;

import java.io.IOException;
import java.net.*;

public class Servidor implements Runnable {
    // Configuration constants
    private int DATACENTER_PORT = 4447;
    private String DATACENTER_GROUP = "231.0.0.0";
    private int DATABASE_PORT = 4448;
    private String DATABASE_GROUP = "232.0.0.0";
    private int RESPONSE_PORT = 5555; // DataCenter's response port

    // Server state
    private final boolean isWriter;
    private final int PORT;
    private volatile int userConnections = 0;

    // Network components
    private MulticastSocket multicastDatacenterServer;
    private MulticastSocket multicastDatabaseServer;

    public Servidor(int PORT) {
        this(PORT, false);
    }

    public Servidor(int PORT, boolean isWriter) {
        this.PORT = PORT;
        this.isWriter = isWriter;
    }

    @Override
    public void run() {
        try {
            initializeMulticastSockets();
            datacenterListener();
        } catch (IOException e) {
            handleError(e);
        }
    }

    private void initializeMulticastSockets() throws IOException {
        // Initialize DataCenter communication socket
        multicastDatacenterServer = new MulticastSocket(DATACENTER_PORT);
        multicastDatacenterServer.joinGroup(InetAddress.getByName(DATACENTER_GROUP));

        // Initialize database communication socket
        multicastDatabaseServer = new MulticastSocket(DATABASE_PORT);
        multicastDatabaseServer.joinGroup(InetAddress.getByName(DATABASE_GROUP));

        System.out.printf("Servidor %d iniciado (Writer: %b)\n", PORT, isWriter);
    }

    private void datacenterListener() {
        byte[] buffer = new byte[1024];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastDatacenterServer.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                logReceivedMessage(message);

                if (message.startsWith("REPORT_USER_COUNT")) {
                    handleCountRequest(packet.getAddress());
                } else if (isWriter) {
                    handleDatabaseMessage(message);
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void handleCountRequest(InetAddress datacenterAddress) throws IOException {
        String response = userConnections + "!" + PORT;
        byte[] buf = response.getBytes();

        // Send UNICAST response to DataCenter's response port
        try (DatagramSocket tempSocket = new DatagramSocket()) {
            DatagramPacket responsePacket = new DatagramPacket(
                    buf,
                    buf.length,
                    datacenterAddress, // DataCenter's address from received packet
                    RESPONSE_PORT      // DataCenter's response port
            );
            tempSocket.send(responsePacket);
            System.out.println("Enviado contagem de conexões: " + response);
        }
    }

    private void handleDatabaseMessage(String message) throws IOException {
        if (!message.contains("!")) { // Filter out our own count responses
            sendToDatabase(message);
        }
    }

    private void sendToDatabase(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(DATABASE_GROUP),
                DATABASE_PORT
        );
        multicastDatabaseServer.send(packet);
        System.out.println("Mensagem enviada para o banco de dados: " + message);
    }


    private void logReceivedMessage(String message) {
        System.out.printf("[Servidor %d] Recebido: %s\n", PORT, message);
    }

    private void handleError(Exception e) {
        System.err.printf("Erro no servidor %d: %s\n", PORT, e.getMessage());
    }
}
