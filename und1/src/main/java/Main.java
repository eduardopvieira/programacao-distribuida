import model.Drone;
import model.Posicao;

public class Main {

    public static void main(String[] args) {
        Thread drone1 = new Thread(new Drone(Posicao.NORTE));
        drone1.start();

    }
}
