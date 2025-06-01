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
    private DatagramSocket responseSocket;

    private ExecutorService threadPool;

    @Override
    public void run() {
        try {
            // Initialize all sockets first
            initializeSockets();

            threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            new Thread(this::listenToDronesMulticast).start();
            startTCPServer();


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

        // Response socket (for receiving server responses)
        responseSocket = new DatagramSocket(5555); // Different port for responses
        responseSocket.setSoTimeout(1000);
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

            String serverInfo = InetAddress.getLocalHost().getHostAddress() + ":" + PORT;
            out.println(serverInfo);

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

    private void requestUserCounts() {
        try {
            InetAddress group = InetAddress.getByName("231.0.0.0");
            String request = "REPORT_USER_COUNT:" + responseSocket.getLocalPort();
            byte[] buf = request.getBytes();

            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, 4447);
            multicastDatacenterServers.send(packet);
            System.out.println("Sent user count request");

        } catch (IOException e) {
            System.err.println("Error requesting user counts: " + e.getMessage());
        }
    }

    private int returnLeastConnectionServer() {
        Map<Integer, Integer> serverConnections = new HashMap<>();
        long startTime = System.currentTimeMillis();
        long timeout = 2000; // waiting 2 seconds for responses

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                responseSocket.receive(packet);

                String response = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received: " + response);

                // splitting "connections!port"
                String[] parts = response.split("!");
                if (parts.length == 2) {
                    int connections = Integer.parseInt(parts[0]);
                    int port = Integer.parseInt(parts[1]);
                    serverConnections.put(port, connections);
                }
            } catch (SocketTimeoutException e) {
                // Expected when no more responses
                if (!serverConnections.isEmpty()) break;
            } catch (IOException | NumberFormatException e) {
                System.err.println("Error processing response: " + e.getMessage());
            }
        }

        return serverConnections.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);
    }
}




