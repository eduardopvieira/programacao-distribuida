package executables;

import model.Servidor;

public class ServerExecutable {
    public static void main(String[] args) {
        Thread servidor1 = new Thread(new Servidor(true));
        Thread servidor2 = new Thread(new Servidor());

        servidor1.start();
        servidor2.start();
    }
}
