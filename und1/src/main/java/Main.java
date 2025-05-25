import model.Drone;
import model.Posicao;

public class Main {

    public static void main(String[] args) {
        Thread droneNorte = new Thread(new Drone(Posicao.NORTE));
        Thread droneSul = new Thread(new Drone(Posicao.SUL));
        Thread droneLeste = new Thread(new Drone(Posicao.LESTE));
        Thread droneOeste = new Thread(new Drone(Posicao.OESTE));

        droneNorte.start();
        droneSul.start();
        droneLeste.start();
        droneOeste.start();
    }
}
