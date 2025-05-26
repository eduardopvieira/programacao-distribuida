package executables;

import model.Servidor;

public class ServerExecutable {
    public static void main(String[] args) {
        Thread servidor = new Thread(new Servidor());
        servidor.start();
        //oi amanda
    }
}
