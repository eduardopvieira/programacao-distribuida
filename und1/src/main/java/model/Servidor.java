package model;

import javax.swing.plaf.multi.MultiDesktopIconUI;
import java.io.IOException;
import java.net.*;

public class Servidor implements Runnable {

    //multicast com o datacenter
    private static final int DATACENTER_PORT = 4447;
    private static final String DATACENTER_GROUP = "231.0.0.0";

    //multicast com o banco de dados
    private static final int DATABASE_PORT = 4448;
    private static final String DATABASE_GROUP = "232.0.0.0";

    private final boolean isWriter;
    private int PORT;
    private int userConnections = 0;

    private MulticastSocket multicastDatacenterServer;


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
        // conexao com o multicast com o datacenter
        connectToDatacenterServerMulticast();


        //TODO: REFACTORING NESSA PARTE DO CODIGO.
        //TODO: METODO UNICO PARA CONEXAO E ESCRITA NO BANCO DE DADOS
        //TODO: METODO UNICO DE DATACENTER LISTENER

        // socket pra enviar ao banco de dados
        try (MulticastSocket senderSocket = new MulticastSocket()) {
            InetAddress databaseGroup = InetAddress.getByName(DATABASE_GROUP);

            byte[] buffer = new byte[1024];

            while (true) {

                // essa parte recebe mensagem do datacenter
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastDatacenterServer.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                logReceivedMessage(message);

                //aq escreve na base de dados. como apenas um dos servidores escreve, verifica se é writer
                if (message.equals("REPORT_USER_COUNT")) {

                    InetAddress group = InetAddress.getByName("231.0.0.0");
                    String answer = Integer.toString(userConnections);
                    answer = answer + "!" + PORT;
                    byte[] buf = answer.getBytes();

                    DatagramPacket packetAnswer = new DatagramPacket(buf, buf.length, group, 4447);
                    multicastDatacenterServer.send(packet);
                    System.out.println("Sent user count request to multicast group");
                }

                if (isWriter) {
                    sendToDatabase(senderSocket, databaseGroup, message);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectToDatacenterServerMulticast() {
        try (MulticastSocket receiverSocket = new MulticastSocket(DATACENTER_PORT)) {
            InetAddress datacenterGroup = InetAddress.getByName(DATACENTER_GROUP);
            receiverSocket.joinGroup(datacenterGroup);

            this.multicastDatacenterServer = receiverSocket;

            System.out.println("Servidor conectado ao grupo do DataCenter (" +
                    DATACENTER_GROUP + ":" + DATACENTER_PORT + ")");

        } catch (IOException e) {
            throw new RuntimeException(e);
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

