package model;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Servidor implements Runnable {

    @Override
    public void run() {
        int portaServidorDatacenter = 4447;
        //ip do range de 224 ate 239
        String grupoServidorDatacenter = "231.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(portaServidorDatacenter)) {
            InetAddress grupoMulticast = InetAddress.getByName(grupoServidorDatacenter);
            socket.joinGroup(grupoMulticast);

            byte[] buffer = new byte[1024];  // Ajuste se necessário
            System.out.println("Servidor aguardando mensagens...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                // Aqui você pode processar o pacote recebido
                String mensagemRecebida = new String(pacote.getData(), 0, pacote.getLength());
                System.out.println("Mensagem recebida: " + mensagemRecebida);


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
