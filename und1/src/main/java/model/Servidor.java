package model;

import java.io.IOException;
import java.net.*;

public class Servidor implements Runnable {

    //multicast com o datacenter
    private static final int DATACENTER_PORT = 4447;
    private static final String DATACENTER_GROUP = "231.0.0.0";

    //multicast com o banco de dados
    private static final int DATABASE_PORT = 4448;
    private static final String DATABASE_GROUP = "232.0.0.0";

    //informações de configuração do servidor
    private final boolean isWriter;
    private int PORT;
    private int userConnections = 0;

    //multicasts
    private MulticastSocket multicastDatacenterServer;
    private MulticastSocket multicastDatabaseServer;

    public Servidor(int PORT) {
        this.isWriter = false;
        this.PORT = PORT;
    }

    public Servidor(int PORT, boolean isWriter) {
        this.isWriter = isWriter;
        this.PORT = PORT;
    }

    @Override
    public void run() {
        try {
            initializeMulticastSockets();

            while (true) {
                datacenterListener();
            }
        } catch (IOException e) {
            handleError(e);
        }
    }

    private void initializeMulticastSockets() throws IOException {
        // multicast do servidor c o datacnenter
        multicastDatacenterServer = new MulticastSocket(DATACENTER_PORT);
        InetAddress datacenterGroup = InetAddress.getByName(DATACENTER_GROUP);
        multicastDatacenterServer.joinGroup(datacenterGroup);
        System.out.println("Servidor conectado ao grupo do DataCenter (" +
                DATACENTER_GROUP + ":" + DATACENTER_PORT + ")");

        // multicast do servidor c o banco de dados
        multicastDatabaseServer = new MulticastSocket(DATABASE_PORT);
        InetAddress databaseGroup = InetAddress.getByName(DATABASE_GROUP);
        multicastDatabaseServer.joinGroup(databaseGroup);
        System.out.println("Servidor conectado ao grupo do Banco de Dados (" +
                DATABASE_GROUP + ":" + DATABASE_PORT + ")");
    }


    private void datacenterListener() {
        try {
            byte[] buffer = new byte[1024];
            // essa parte recebe mensagem do datacenter
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            multicastDatacenterServer.receive(packet);

            String message = new String(packet.getData(), 0, packet.getLength());
            logReceivedMessage(message);

            if (message.startsWith("REPORT_USER_COUNT")) {

                InetAddress group = InetAddress.getByName("231.0.0.0");
                String answer = Integer.toString(userConnections);
                answer = answer + "!" + PORT;
                byte[] buf = answer.getBytes();

                DatagramPacket packetAnswer = new DatagramPacket(buf, buf.length, group, 4447);
                multicastDatacenterServer.send(packetAnswer);
                System.out.println("Sent user count request to multicast group");
            } else {
                //aq escreve na base de dados. como apenas um dos servidores escreve, verifica se é writer
                if (isWriter) {
                    sendToDatabase(message);
                }
            }
        } catch (IOException e) {
            handleError(e);
        }
    }

    private void sendToDatabase(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName("232.0.0.0"),
                DATABASE_PORT
        );

        multicastDatabaseServer.send(packet);
        System.out.println("Mensagem encaminhada para o Banco de Dados: " + message);

    }

    private void logReceivedMessage(String message) {
        System.out.println("[DataCenter → Servidor] Mensagem recebida: " + message);
    }

    private void handleError(Exception e) {
        System.err.println("Erro no servidor:");
        e.printStackTrace();
    }
}

