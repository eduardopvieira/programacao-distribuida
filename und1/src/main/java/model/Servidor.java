package model;

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

            byte[] buffer = new byte[1024];
            System.out.println("Servidor aguardando mensagens...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                String msgNaoTratada = new String(pacote.getData(), 0, pacote.getLength());
                RetornoDrone retorno = transformarStringEmRetornoDrone(msgNaoTratada);
                String msgTratada = padronizarMensagem(retorno);

                System.out.println("Mensagem recebida do drone " + retorno.getPosicao() + ": " + msgTratada);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //lembrete: dados devem ser armazenados no formato [temperatura//umidade//pressao//radiacao].
    public RetornoDrone transformarStringEmRetornoDrone(String mensagem) {
        String[] partes = mensagem.split("\\*");
        if (partes.length != 2) {
            throw new IllegalArgumentException("Mensagem inválida: " + mensagem);
        }
        String dados = partes[0];
        String posicaoStr = partes[1];

        Posicao posicao;
        try {
            posicao = Posicao.valueOf(posicaoStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Posição inválida: " + posicaoStr);
        }
        return new RetornoDrone(dados, posicao);
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
