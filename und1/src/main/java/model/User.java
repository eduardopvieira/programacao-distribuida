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

    public User(String datacenterIp, int datacenterPort) {
        this.connectToDatacenter(datacenterIp, datacenterPort);
    }

    private void connectToDatacenter(String datacenterIp, int datacenterPort) {
        try (Socket socket = new Socket(datacenterIp, datacenterPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String serverInfo = reader.readLine();
            String[] parts = serverInfo.split(":");
            this.port = Integer.parseInt(parts[1]);

            System.out.println("Servidor encontrado: " + ip + ":" + port);
            connectToServer();
        } catch (IOException e) {
            System.out.println("Erro ao conectar ao Servidor de Localização: " + e.getMessage());
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
