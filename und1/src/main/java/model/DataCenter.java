package model;

import model.auxiliar.Posicao;
import model.auxiliar.RetornoDrone;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataCenter implements Runnable {
    private final int PORT = 50000;
    private final int THREAD_POOL_SIZE = 10;

    private final int[] serverPorts = {50001, 50002};

    private MulticastSocket multicastDroneDatacenter;

    private MulticastSocket multicastDatacenterServers;


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

            this.multicastDroneDatacenter = socket;

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

            while (!Thread.currentThread().isInterrupted()) {
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
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            // Send initial server info to client
            String serverInfo = InetAddress.getLocalHost().getHostAddress() + ":" + PORT;
            out.println(serverInfo);

            //i want to request my servers the amount of connections on them and connect the user to the
            //server with the least current connections
            requestUserCounts();

        } catch (IOException e) {
            System.err.println("Erro na conexão com cliente: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
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

            this.multicastDatacenterServers = socket;

            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, grupo, portaServidores);
            socket.send(pacote);
            System.out.println("Mensagem enviada para o grupo de servidores: " + mensagem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestUserCounts() {
        try {
            InetAddress group = InetAddress.getByName("231.0.0.0");
            String request = "REPORT_USER_COUNT";
            byte[] buf = request.getBytes();

            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, 4447);
            multicastDatacenterServers.send(packet);
            System.out.println("Requisitando contagem de conexões ao grupo multicast.");

            int returnPort = returnLeastConnectionServer();
            System.out.println("Porta do servidor com menos conexoes: " + returnPort);
        } catch (IOException e) {
            System.err.println("Error sending multicast request: " + e.getMessage());
        }
    }

    private int returnLeastConnectionServer() {

        long startTime = System.currentTimeMillis();
        long responseTimeoutMs = 5000; // 3 segundos até o timeout

        Map<Integer, Integer> serverConnections = new HashMap<>();

        while (System.currentTimeMillis() - startTime < responseTimeoutMs) {
            try {

                byte[] receiveData = new byte[1024];

                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                multicastDatacenterServers.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Received server response: " + response);

                // Parse the response (format: "userConnections!Port")
                String[] parts = response.split("!");
                if (parts.length == 2) {
                    int connections = Integer.parseInt(parts[0]);
                    int port = Integer.parseInt(parts[1]);
                    serverConnections.put(port, connections);
                }

                return serverConnections.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(-1);


            } catch (SocketTimeoutException e) {
                System.err.println("Timeout waiting for server responses: " + e.getMessage());
                break;
            } catch (IOException | NumberFormatException e) {
                System.err.println("Error processing server response: " + e.getMessage());
            }
        }

        // Find server with least connections
        return serverConnections.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1); // Return -1 if no servers responded
    }
}




