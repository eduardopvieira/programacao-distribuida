package executables;

import model.LocServer;

public class LocServerExecutable {
    public static void main(String[] args) {
        Thread locserver = new Thread(new LocServer());
        locserver.start();
    }
}
