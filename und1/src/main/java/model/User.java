package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class User implements Runnable {

    private String ip = "127.0.0.1";
    private int port;

    private Socket serverConnection;

    public User(String locServerIp, int locServerPort) {
        this.connectToLocServer(locServerIp, locServerPort);
    }

    @Override
    public void run() {
        if (serverConnection == null || serverConnection.isClosed()) {
            System.out.println("Conexão com o servidor não estabelecida.");
            return;
        }

        try {
            userInterface();
        } catch (Exception e) {
            System.out.println("Erro na interface do usuário: " + e.getMessage());
        }
    }

    private void connectToLocServer(String locServerIp, int locServerPort) {
        try (Socket locServerSocket = new Socket(locServerIp, locServerPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(locServerSocket.getInputStream()))) {

            String serverInfo = reader.readLine();
            if (serverInfo.startsWith("ERROR")) {
                System.out.println("Nenhum servidor disponível: " + serverInfo);
                return;
            }

            String[] parts = serverInfo.split(":");
            this.port = Integer.parseInt(parts[1]);

            System.out.println("Servidor recomendado: " + port);
            connectToServer(port);

        } catch (IOException e) {
            System.out.println("Erro ao conectar ao LocServer: " + e.getMessage());
        }
    }

    private void connectToServer(int portServer) {
        try (Socket socket = new Socket(ip, portServer);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            this.serverConnection = socket;

            System.out.println("Conectado ao Servidor!");

            System.out.println(reader.readLine());

            userInterface();

        } catch (IOException e) {
            System.out.println("Erro ao conectar ao Servidor Proxy: " + e.getMessage());
        }
    }

    private void userInterface() {
        System.out.println("Digite '0' para encerrar a conexão.");

        Thread messageReceiver = new Thread(() -> {
            try (BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(serverConnection.getInputStream()))) {

                String serverMessage;
                while ((serverMessage = serverReader.readLine()) != null) {
                    System.out.println("[Servidor]: " + serverMessage);
                }

            } catch (IOException e) {
                if (!serverConnection.isClosed()) {
                    System.out.println("Erro ao receber mensagem do servidor: " + e.getMessage());
                }
            }
        });
        messageReceiver.start();

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(serverConnection.getOutputStream(), true)) {

            String input;
            while (!(input = consoleReader.readLine()).equalsIgnoreCase("0")) {
                writer.println(input);
            }

        } catch (IOException e) {
            System.out.println("Erro na interface do usuário: " + e.getMessage());
        } finally {
            try {
                serverConnection.close();
                System.out.println("Conexão encerrada.");
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }

}
