package model;

import java.io.IOException;
import java.net.*;

public class Servidor implements Runnable {
    private static final int DATACENTER_PORT = 4447;
    private static final String DATACENTER_GROUP = "231.0.0.0";
    private static final int DATABASE_PORT = 4448;
    private static final String DATABASE_GROUP = "232.0.0.0";

    private final boolean isWriter;

    public Servidor() {
        this.isWriter = false;
    }

    public Servidor(boolean isWriter) {
        this.isWriter = isWriter;
    }

    @Override
    public void run() {
        try {
            // socket pra receber a string do datacenter
            try (MulticastSocket receiverSocket = new MulticastSocket(DATACENTER_PORT)) {
                InetAddress datacenterGroup = InetAddress.getByName(DATACENTER_GROUP);
                receiverSocket.joinGroup(datacenterGroup);

                System.out.println("Servidor conectado ao grupo do DataCenter (" +
                        DATACENTER_GROUP + ":" + DATACENTER_PORT + ")");

                // socket pra enviar ao banco de dados
                try (MulticastSocket senderSocket = new MulticastSocket()) {
                    InetAddress databaseGroup = InetAddress.getByName(DATABASE_GROUP);

                    byte[] buffer = new byte[1024];

                    while (true) {

                        // essa parte recebe mensagem do datacenter
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        receiverSocket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength());
                        logReceivedMessage(message);

                        //aq escreve na base de dados. como apenas um dos servidores escreve, verifica se é writer
                        if (isWriter) {
                            sendToDatabase(senderSocket, databaseGroup, message);
                        }
                    }
                }
            }
        } catch (IOException e) {
            handleError(e);
        }
    }

    private void sendToDatabase(MulticastSocket socket, InetAddress group, String message) throws IOException {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                group,
                DATABASE_PORT
            );

            socket.send(packet);
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
