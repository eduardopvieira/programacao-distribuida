package executables;

import model.LocServer;

public class LocServerExecutable {
    public static void main(String[] args) {
        Thread locserverConsistentHash = new Thread(new LocServer(false));
        locserverConsistentHash.start();

        //usando round robin

        //Thread locserverRoundRobin = new Thread(new LocServer(true));
        //locserverRoundRobin.start();
    }
}
