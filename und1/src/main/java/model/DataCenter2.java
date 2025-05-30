package model;

import model.auxiliar.Posicao;
import model.auxiliar.RetornoDrone;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataCenter2 implements Runnable {
    private static final int PORT = 50000;
    private static final int THREAD_POOL_SIZE = 10;

    private ExecutorService threadPool;

    @Override
    public void run() {
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        new Thread(this::listenToDronesMulticast).start();

        startTCPServer();
    }

    private void listenToDronesMulticast() {
        int portaDrones = 4446;
        String grupoDrones = "230.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(portaDrones)) {
            InetAddress grupoMulticast = InetAddress.getByName(grupoDrones);
            socket.joinGroup(grupoMulticast);

            byte[] buffer = new byte[1024];
            System.out.println("Servidor aguardando mensagens multicast...");

            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                RetornoDrone retorno = desserializarRetornoDrone(pacote);

                if (retorno == null) {
                    System.err.println("Erro ao desserializar o pacote recebido.");
                    continue;
                }

                String mensagemTratada = padronizarMensagem(retorno);
                System.out.println("Drone " + retorno.getPosicao() + ": " + mensagemTratada);

                enviarMensagemParaServidores(mensagemTratada);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTCPServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("DataCenter TCP Server iniciado na porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleClientConnection(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor TCP: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Mensagem do cliente: " + inputLine);
                // Processar comando do cliente aqui
                out.println("ACK: " + inputLine); // Resposta de exemplo
            }
        } catch (IOException e) {
            System.err.println("Erro na conexão com cliente: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket do cliente: " + e.getMessage());
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
