package model;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Servidor implements Runnable {

    public Servidor() {}

    //commiting develop
    @Override
    public void run() {
        int porta = 4446;
        String grupo = "230.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(porta)) {
            InetAddress grupoMulticast = InetAddress.getByName(grupo);
            socket.joinGroup(grupoMulticast);

            byte[] buffer = new byte[1024];  // Ajuste se necessário
            System.out.println("Servidor aguardando mensagens...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                //erros ja sao lançados dentro dessa funçao, se ela der errado o programa inteiro morre
                RetornoDrone retorno = desserializarRetornoDrone(pacote);

                if (retorno != null) {
                    String mensagemTratada = padronizarMensagem(retorno);
                    System.out.println("Drone " + retorno.getPosicao() + ": " + mensagemTratada);
                }




            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RetornoDrone desserializarRetornoDrone(DatagramPacket pacote) {
        try (
                ByteArrayInputStream byteStream = new ByteArrayInputStream(pacote.getData(), 0, pacote.getLength());
                ObjectInputStream in = new ObjectInputStream(byteStream)
        ) {
            return (RetornoDrone) in.readObject();
        } catch (Exception e) {
            System.err.println("Erro ao desserializar objeto: " + e.getMessage());
            return null;
        }
    }


    public String padronizarMensagem(RetornoDrone msg) {
        Posicao pos = msg.getPosicao();

        switch (pos) {
            case NORTE:
                return msg.getMensagem().replace("-", "//");
            case SUL:
                return msg.getMensagem().replace("(", "").replace(")", "").replace(";", "//");
            case LESTE:
                return msg.getMensagem().replace("{", "").replace("}", "").replace(",", "//");
            case OESTE:
                return msg.getMensagem().replace("#", "//");
            default:
                throw new IllegalArgumentException("Posição do drone desconhecida: " + pos);
        }
    }

}
