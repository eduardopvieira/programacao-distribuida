package executables;

import model.DataCenter;

public class ServerExecutable {
    public static void main(String[] args) {
        Thread servidor = new Thread(new DataCenter());
        servidor.start();
    }
}
