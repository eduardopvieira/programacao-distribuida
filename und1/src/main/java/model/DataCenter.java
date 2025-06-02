package model;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.function.UnaryOperator;


public class DataCenter implements Runnable {


    private MulticastSocket multicastDroneDatacenter;
    private MulticastSocket multicastDatacenterServers;

    @Override
    public void run() {
        try {
            initializeSockets();

            new Thread(this::listenToDronesMulticast).start();


        } catch (IOException e) {
            System.err.println("DataCenter initialization failed: " + e.getMessage());
        }
    }

    private void initializeSockets() throws IOException {
        // Drone communication socket
        multicastDroneDatacenter = new MulticastSocket(4446);
        multicastDroneDatacenter.joinGroup(InetAddress.getByName("230.0.0.0"));

        // Server communication socket
        multicastDatacenterServers = new MulticastSocket();

    }

    private void listenToDronesMulticast() {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastDroneDatacenter.receive(packet);

                String retorno = desserializarRetornoDrone(packet);
                if (retorno != null) {
                    String mensagemTratada = padronizarMensagem(retorno);
                    enviarMensagemParaServidores(mensagemTratada);
                }
            } catch (IOException e) {
                System.err.println("Error in drone listener: " + e.getMessage());
            }
        }
    }


    private String desserializarRetornoDrone(DatagramPacket pacote) {
        try (
                ByteArrayInputStream byteStream = new ByteArrayInputStream(pacote.getData(), 0, pacote.getLength());
                ObjectInputStream in = new ObjectInputStream(byteStream)
        ) {
            return (String) in.readObject();
        } catch (Exception e) {
            System.err.println("Erro ao desserializar objeto: " + e.getMessage());
            return null;
        }
    }


    public String padronizarMensagem(String msg) {
        UnaryOperator<String> replaceHyphen = s -> s.replace("-", "//");
        UnaryOperator<String> replaceParentheses = s -> s.replace("(", "").replace(")", "").replace(";", "//");
        UnaryOperator<String> replaceBraces = s -> s.replace("{", "").replace("}", "").replace(",", "//");
        UnaryOperator<String> replaceHash = s -> s.replace("#", "//");

        return Optional.ofNullable(msg)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    if (s.contains("-")) return replaceHyphen.apply(s);
                    if (s.contains("(")) return replaceParentheses.apply(s);
                    if (s.contains("{")) return replaceBraces.apply(s);
                    if (s.contains("#")) return replaceHash.apply(s);
                    return s;
                })
                .map(s -> "[" + s + "]")
                .orElseThrow(() -> new IllegalArgumentException("Mensagem inválida: " + msg));
    }

    public void enviarMensagemParaServidores(String mensagem) {
        try {
            InetAddress group = InetAddress.getByName("231.0.0.0");
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, group, 4447);
            multicastDatacenterServers.send(pacote);
            System.out.println("Mensagem enviada para servidores: " + mensagem);
        } catch (IOException e) {
            System.err.println("Error sending to servers: " + e.getMessage());
        }
    }


}





