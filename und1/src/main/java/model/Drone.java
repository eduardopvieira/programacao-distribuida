package model;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Drone implements Runnable {
    private Posicao posicao;
    private int[] tempos = {2000, 3000, 4000, 5000};

    public Drone(Posicao posicao) {
        this.posicao = posicao;
    }

    @Override
    public void run() {
        System.out.println("Drone iniciado na posição " + posicao);
        while (true) {
            int tempoAleatorio = tempos[new java.util.Random().nextInt(tempos.length)];
            String dados = gerarDados();
            System.out.println("Drone na posição " + posicao + ": " + dados);
            enviarPorMulticast(dados);
            try {
                Thread.sleep(tempoAleatorio);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public String gerarDados() {
        double pressao = Math.random() * 100;
        double radiacao = Math.random() * 100;
        double temperatura = Math.random() * 100;
        double umidade = Math.random() * 100;

        return formatarDados(pressao, radiacao, temperatura, umidade);
    }

    public String formatarDados(double pressao, double radiacao, double temperatura, double umidade) {
        return switch (posicao) {
            case NORTE -> String.format("%.2f-%.2f-%.2f-%.2f", pressao, radiacao, temperatura, umidade);
            case SUL -> String.format("(%.2f;%.2f;%.2f;%.2f)", pressao, radiacao, temperatura, umidade);
            case LESTE -> String.format("{%.2f,%.2f,%.2f,%.2f}", pressao, radiacao, temperatura, umidade);
            case OESTE -> String.format("%.2f#%.2f#%.2f#%.2f", pressao, radiacao, temperatura, umidade);
            default -> throw new IllegalArgumentException("Posição do drone desconhecida: " + posicao);
        };
    }

    private void enviarPorMulticast(String mensagem) {
        String grupo = "230.0.0.0";
        int porta = 4446;

        try (DatagramSocket socket = new DatagramSocket()) {

            byte[] dados = mensagem.getBytes();
            InetAddress enderecoGrupo = InetAddress.getByName(grupo);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, enderecoGrupo, porta);
            socket.send(pacote);
            System.out.println("Drone " + this.posicao + "enviou a mensagem "+ mensagem);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
