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
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            //passo a passo pra eu n enloqueucer

            // 1 - manda informaçoes basicas do datacenter
            String serverInfo = InetAddress.getLocalHost().getHostAddress() + ":" + PORT;
            out.println(serverInfo);

            // 2 - cria um map pras server connections
            Map<Integer, Integer> serverConnections = new ConcurrentHashMap<>();

            // 3 - manda uma request pros servidores querendo o numero de conexoes
            requestUserCounts();

            // 4. Collect responses with timeout
            int leastConnectionServerPort = serverConnectionAmountListener(serverConnections);
            System.out.println("Selected server port: " + leastConnectionServerPort);

            // 5. Return result to client
            if (leastConnectionServerPort != -1) {
                out.println("CONNECT_TO:" + leastConnectionServerPort);
            } else {
                out.println("ERROR:No available servers");
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
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
            // LIMPANDO O BUFFER PRA EVITAR REQUISIÇOES DUPLICADAS (ja aconteceu)
            byte[] clearBuffer = new byte[1024];
            while (true) {
                DatagramPacket clearPacket = new DatagramPacket(clearBuffer, clearBuffer.length);
                responseSocket.setSoTimeout(100);
                try {
                    responseSocket.receive(clearPacket);
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

            // agr sim to mandando a request
            String request = "REPORT_USER_COUNT:" + responseSocket.getLocalPort();
            DatagramPacket packet = new DatagramPacket(
                    request.getBytes(),
                    request.getBytes().length,
                    InetAddress.getByName("231.0.0.0"),
                    4447
            );
            multicastDatacenterServers.send(packet);
            System.out.println("Sent single request: " + request);

        } catch (IOException e) {
            System.err.println("Error in request: " + e.getMessage());
        }
    }


    private int serverConnectionAmountListener(Map<Integer, Integer> serverConnections) {
        long startTime = System.currentTimeMillis();
        final long TIMEOUT_MS = 2000; // Total maximum wait time
        final long RESPONSE_WINDOW_MS = 1500; // Active collection window

        try {
            byte[] buffer = new byte[1024];
            boolean receivedAnyResponse = false;

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                try {
                    // Set timeout for each receive attempt
                    responseSocket.setSoTimeout((int)(TIMEOUT_MS - (System.currentTimeMillis() - startTime)));

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    responseSocket.receive(packet);

                    String response = new String(packet.getData(), 0, packet.getLength()).trim();
                    System.out.println("Received server response: " + response);

                    // Parse "connections!port" format
                    String[] parts = response.split("!");
                    if (parts.length == 2) {
                        int connections = Integer.parseInt(parts[0]);
                        int port = Integer.parseInt(parts[1]);
                        serverConnections.put(port, connections);
                        receivedAnyResponse = true;
                    }

                    // Stop collecting if we're past the active window
                    if (System.currentTimeMillis() - startTime > RESPONSE_WINDOW_MS) {
                        System.out.println("Response window closed");
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    if (receivedAnyResponse) {
                        break; // Got at least one response and now timed out
                    }
                    continue; // Keep waiting for first response
                } catch (IOException | NumberFormatException e) {
                    System.err.println("Error processing response: " + e.getMessage());
                }
            }

            return returnLeastConnectionPort(serverConnections);

        } catch (Exception e) {
            System.err.println("Error in response listener: " + e.getMessage());
            return -1;
        }
    }


    private int returnLeastConnectionPort(Map<Integer, Integer> serverConnections) {
        int minConnections = Collections.min(serverConnections.values());

        List<Integer> bestServers = serverConnections.entrySet().stream()
                .filter(entry -> entry.getValue() == minConnections)
                .map(Map.Entry::getKey)
                .toList();

        return bestServers.getFirst();
    }
}





