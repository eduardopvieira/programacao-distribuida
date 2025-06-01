package model;

import model.auxiliar.Posicao;
import model.auxiliar.RetornoDrone;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataCenter implements Runnable {
    private final int THREAD_POOL_SIZE = 10;


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

                RetornoDrone retorno = desserializarRetornoDrone(packet);
                if (retorno != null) {
                    String mensagemTratada = padronizarMensagem(retorno);
                    System.out.println("Drone " + retorno.getPosicao() + ": " + mensagemTratada);
                    enviarMensagemParaServidores(mensagemTratada);
                }
            } catch (IOException e) {
                System.err.println("Error in drone listener: " + e.getMessage());
            }
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
        String retorno = "";
        switch (pos) {
            case NORTE:
                retorno = msg.getMensagem().replace("-", "//");
                break;
            case SUL:
                retorno = msg.getMensagem().replace("(", "").replace(")", "").replace(";", "//");
                break;
            case LESTE:
                retorno = msg.getMensagem().replace("{", "").replace("}", "").replace(",", "//");
                break;
            case OESTE:
                retorno = msg.getMensagem().replace("#", "//");
                break;
        }
        if (retorno.isEmpty()) {
            throw new IllegalArgumentException("Mensagem inválida ou não reconhecida: " + msg.getMensagem());
        }

        return ("[" + retorno + "]");
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





