package executables;

import model.Drone;
import model.auxiliar.Posicao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DroneExecutables {
    public static void main(String[] args) {

        ExecutorService executor = null;

        try {

            executor = Executors.newFixedThreadPool(4);

            executor.execute(new Drone(Posicao.NORTE));
            executor.execute(new Drone(Posicao.SUL));
            executor.execute(new Drone(Posicao.LESTE));
            executor.execute(new Drone(Posicao.OESTE));

            executor.shutdown();

            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("Forçando encerramento após 30 segundos");
                executor.shutdownNow();
            }

        } catch (InterruptedException e) {
            System.out.println("Main thread interrompida");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
