package model;

import datastructures.HashAdaptado;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Database implements Runnable {

    private HashAdaptado bd = new HashAdaptado();

    @Override
    public void run() {

        int MULTICAST_PORT = 4448;
        String MULTICAST_GROUP = "232.0.0.0";

        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);

            System.out.println("Database conectada ao grupo multicast " + MULTICAST_GROUP
                    + ":" + MULTICAST_PORT);

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                processMessage(message);
            }

        } catch (IOException e) {
            System.err.println("Erro ao conectar ao grupo multicast: " + e.getMessage());
        }

    }

    private void processMessage(String message) {
         bd.add(message);
        System.out.println("Mensagem adicionada ao Database: " + message);
        logToFile(message);
    }

    private void logToFile(String message) {
        try (FileWriter fw = new FileWriter("database_log.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Erro ao escrever no arquivo de log: " + e.getMessage());
        }
    }
}
