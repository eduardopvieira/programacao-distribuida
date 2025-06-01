package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class User {

    private String ip = "127.0.0.1";
    private int port;

    public User(String locServerIp, int locServerPort) {
        this.connectToLocServer(locServerIp, locServerPort);
    }

    private void connectToLocServer(String datacenterIp, int datacenterPort) {
        try (Socket locServerSocket = new Socket(datacenterIp, datacenterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(locServerSocket.getInputStream()))) {

            String serverInfo = reader.readLine();
            if (serverInfo.startsWith("ERROR")) {
                System.out.println("Nenhum servidor disponível: " + serverInfo);
                return;
            }

            String[] parts = serverInfo.split(":");
            this.ip = parts[0]; // Use o IP retornado pelo LocServer
            this.port = Integer.parseInt(parts[1]);

            System.out.println("Servidor recomendado: " + ip + ":" + port);
            connectToServer();

        } catch (IOException e) {
            System.out.println("Erro ao conectar ao LocServer: " + e.getMessage());
        }
    }

    private void connectToServer() {
        try (Socket socket = new Socket(ip, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream os = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(os, true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Conectado ao Servidor!");

            // Recebe mensagem inicial do Proxy
            System.out.println(reader.readLine());

        } catch (IOException e) {
            System.out.println("Erro ao conectar ao Servidor Proxy: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new User("127.0.0.1", 50000);
    }
}
