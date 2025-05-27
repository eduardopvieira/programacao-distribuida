package model;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;

public class Drone implements Runnable {
    private Posicao posicao;

    private final int[] tempos = {2000, 3000, 4000, 5000};

    public Drone(Posicao posicao) {
        this.posicao = posicao;
    }

    @Override
    public void run() {
        System.out.println("Drone iniciado na posição " + posicao);
        while (true) {
            int tempoAleatorio = tempos[new java.util.Random().nextInt(tempos.length)];
            RetornoDrone dados = gerarDados();
            enviarPorMulticast(dados);
            try {
                Thread.sleep(tempoAleatorio);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public RetornoDrone gerarDados() {
        double pressao = Math.random() * 100;
        double radiacao = Math.random() * 100;
        double temperatura = Math.random() * 100;
        double umidade = Math.random() * 100;

        return formatarDados(pressao, radiacao, temperatura, umidade);
    }

    public RetornoDrone formatarDados(double pressao, double radiacao, double temperatura, double umidade) {
        String ret;
        switch (posicao) {
            case NORTE:
                //formato: pressao-radiacao-temperatura-umidade
                ret = String.format(Locale.US, "%.2f-%.2f-%.2f-%.2f", pressao, radiacao, temperatura, umidade);
                return new RetornoDrone(ret, posicao);

            case SUL:
                //formato: (pressao;radiacao;temperatura;umidade)
                ret =  String.format(Locale.US,"(%.2f;%.2f;%.2f;%.2f)", pressao, radiacao, temperatura, umidade);
                return new RetornoDrone(ret, posicao);

            case LESTE:
                //formato: {pressao,radiacao,temperatura,umidade}
                ret = String.format(Locale.US,"{%.2f,%.2f,%.2f,%.2f}", pressao, radiacao, temperatura, umidade);
                return new RetornoDrone(ret, posicao);

            case OESTE:
                //formato: pressao#radiacao#temperatura#umidade
                ret = String.format(Locale.US,"%.2f#%.2f#%.2f#%.2f", pressao, radiacao, temperatura, umidade);
                return new RetornoDrone(ret, posicao);

            default:
                throw new IllegalArgumentException("Posição do drone desconhecida: " + posicao);
        }

    }

    private void enviarPorMulticast(RetornoDrone mensagem) {
        String grupo = "230.0.0.0";
        int porta = 4446;

        try (DatagramSocket socket = new DatagramSocket()) {

            byte[] dados = mensagem.getBytes();
            InetAddress enderecoGrupo = InetAddress.getByName(grupo);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, enderecoGrupo, porta);
            socket.send(pacote);
            System.out.println("Drone " + this.posicao + "enviou a mensagem "+ mensagem.getMensagem());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
