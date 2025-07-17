package executables;

import model.ConsumidorMQTTTempoReal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsumidorMQTTTempoRealExecutable {
    public static void main(String[] args) {

        // inicia o consumidor apos 10 segundos
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> {
            System.out.println("\n--- Iniciando Consumidor MQTT Tempo Real ---");
            ConsumidorMQTTTempoReal consumidor = new ConsumidorMQTTTempoReal();
            new Thread(consumidor).start();
        }, 10, TimeUnit.SECONDS); 

        try {

            Thread.sleep(TimeUnit.MINUTES.toMillis(5)); //  mantem por 5 minutos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Consumidor MQTT Tempo Real parou.");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
