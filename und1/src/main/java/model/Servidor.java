package model;

import java.io.IOException;
import java.net.*;

public class Servidor implements Runnable {
    private final int DATACENTER_PORT = 4447;
    private final String DATACENTER_GROUP = "231.0.0.0";
    private final int DATABASE_PORT = 4448;
    private final String DATABASE_GROUP = "232.0.0.0";
    private final int LOCSERVER_PORT = 4449;
    private final String LOCSERVER_GROUP = "233.0.0.0";
    private final int RESPONSE_PORT = 5555; //locserver espera respostas aqui

    private final boolean isWriter;
    private final int PORT;
    private int userConnections = 0;

    private MulticastSocket multicastDatacenterServer;
    private MulticastSocket multicastDatabaseServer;
    private MulticastSocket multicastLocServerServer;

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
            System.out.println("Servidor " + PORT + " iniciado e ouvindo em:" +
                    "\n- Datacenter: " + DATACENTER_GROUP + ":" + DATACENTER_PORT +
                    "\n- Database: " + DATABASE_GROUP + ":" + DATABASE_PORT +
                    "\n- LocServer: " + LOCSERVER_GROUP + ":" + LOCSERVER_PORT);

            //thread ouvindo o datacenter
            new Thread(this::datacenterListener).start();

            // thread ouvindo o LocServer
            new Thread(this::locServerListener).start();

        } catch (IOException e) {
            handleError(e);
        }
    }

    private void initializeMulticastSockets() throws IOException {
        multicastDatacenterServer = new MulticastSocket(DATACENTER_PORT);
        multicastDatacenterServer.joinGroup(InetAddress.getByName(DATACENTER_GROUP));

        multicastDatabaseServer = new MulticastSocket(DATABASE_PORT);
        multicastDatabaseServer.joinGroup(InetAddress.getByName(DATABASE_GROUP));

        multicastLocServerServer = new MulticastSocket(LOCSERVER_PORT);
        multicastLocServerServer.joinGroup(InetAddress.getByName(LOCSERVER_GROUP));
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
                    handleDatabaseMessage(message);
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void locServerListener() {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastLocServerServer.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                logReceivedMessage("LocServer", message);

                if (message.startsWith("REPORT_USER_COUNT")) {
                    handleCountRequest();
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void handleCountRequest() throws IOException {
        String response = userConnections + "!" + PORT;
        byte[] buf = response.getBytes();

        try (DatagramSocket tempSocket = new DatagramSocket()) {
            DatagramPacket responsePacket = new DatagramPacket(
                    buf,
                    buf.length,
                    InetAddress.getLocalHost(), // se tiver que rodar em mais pcs tem q trocar isso aq
                    RESPONSE_PORT //response socket
            );
            tempSocket.send(responsePacket);
            System.out.println("Response sent to LocServer: " + response);
        }
    }

    private void handleDatabaseMessage(String message) throws IOException {
        if (!message.startsWith("REPORT_USER_COUNT")) {
            sendToDatabase(message);
        }
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

    private void logReceivedMessage(String source, String message) {
        System.out.printf("[Server %d][%s] received: %s\n", PORT, source, message);
    }

    private void handleError(Exception e) {
        System.err.printf("Erro no servidor %d: %s\n", PORT, e.getMessage());
        e.printStackTrace();
    }

}
