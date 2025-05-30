package model;

import datastructures.HashAdaptado;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class DataCenter implements Runnable {

    private Servidor server1;
    private Servidor server2;
    private HashAdaptado database;

    public DataCenter() {
    }

    //commiting develop
    @Override
    public void run() {
        int portaDrones = 4446;
        //ip do range de 224 ate 239
        String grupoDrones = "230.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(portaDrones)) {
            InetAddress grupoMulticast = InetAddress.getByName(grupoDrones);
            socket.joinGroup(grupoMulticast);

            byte[] buffer = new byte[1024];  // Ajuste se necessário
            System.out.println("Servidor aguardando mensagens...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                RetornoDrone retorno = desserializarRetornoDrone(pacote);

                if (retorno == null) {
                    throw new RuntimeException("Erro ao desserializar o pacote recebido.");
                }

                String mensagemTratada = padronizarMensagem(retorno);
                System.out.println("Drone " + retorno.getPosicao() + ": " + mensagemTratada);

                enviarMensagemParaServidores(mensagemTratada);

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
        String grupoServidores = "231.0.0.0";
        int portaServidores = 4447;

        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress grupo = InetAddress.getByName(grupoServidores);
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, grupo, portaServidores);
            socket.send(pacote);
            System.out.println("Mensagem enviada para o grupo de servidores: " + mensagem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
