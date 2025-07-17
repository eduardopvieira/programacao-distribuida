package executables;

import model.Drone;
import model.auxiliar.Posicao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DroneExecutables {

    private static final long SIMULATION_DURATION_MINUTES = 3;                                  // duração total da simulação

    public static void main(String[] args) {

        ExecutorService droneExecutor = Executors.newFixedThreadPool(4); 
        ScheduledExecutorService mainScheduler = Executors.newSingleThreadScheduledExecutor();  //  controla o tempo de simulação

        List<Drone> drones = new ArrayList<>(); 

        try {
            Drone droneNorte = new Drone(Posicao.NORTE);
            Drone droneSul = new Drone(Posicao.SUL);
            Drone droneLeste = new Drone(Posicao.LESTE);
            Drone droneOeste = new Drone(Posicao.OESTE);

            drones.add(droneNorte);
            drones.add(droneSul);
            drones.add(droneLeste);
            drones.add(droneOeste);

            droneExecutor.execute(droneNorte);
            droneExecutor.execute(droneSul);
            droneExecutor.execute(droneLeste);
            droneExecutor.execute(droneOeste);

            System.out.println("Iniciando simulação de Drones por " + SIMULATION_DURATION_MINUTES + " minutos.");

            mainScheduler.schedule(() -> {
                System.out.println("\n--- Fim da Duração da Simulação (" + SIMULATION_DURATION_MINUTES + " minutos) ---");
                droneExecutor.shutdownNow(); 
                drones.forEach(Drone::cleanup); 

                System.out.println("Encerrando todos os processos da simulação...");
                System.exit(0); 
            }, SIMULATION_DURATION_MINUTES, TimeUnit.MINUTES);

            droneExecutor.awaitTermination(SIMULATION_DURATION_MINUTES + 1, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            System.out.println("Main thread interrompida durante a espera.");
            Thread.currentThread().interrupt();
        } finally {
            droneExecutor.shutdownNow();
            drones.forEach(Drone::cleanup);
            mainScheduler.shutdownNow();
            System.out.println("Execução de Drones finalizada.");
        }
    }
}
