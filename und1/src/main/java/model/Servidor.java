package model;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Servidor {
    public static void main(String[] args) {
        int porta = 4446;
        String grupo = "230.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(porta)) {
            InetAddress grupoMulticast = InetAddress.getByName(grupo);
            socket.joinGroup(grupoMulticast);

            byte[] buffer = new byte[1024];
            System.out.println("Servidor aguardando mensagens...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                String mensagem = new String(pacote.getData(), 0, pacote.getLength());
                System.out.println("Mensagem recebida: " + mensagem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
