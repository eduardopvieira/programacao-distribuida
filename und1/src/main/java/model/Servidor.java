package model;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Servidor implements Runnable {

    public Servidor() {}

    @Override
    public void run() {
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
                System.out.println("Mensagem recebida do drone: " + mensagem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Os dados devem ser armazenados no formato: [temperatura//umidade//pressao//radiacao].
    public String padronizarMensagem(String msg) {
        return "";

    }

}
