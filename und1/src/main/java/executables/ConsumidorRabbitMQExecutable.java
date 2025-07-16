package executables;

import model.ConsumidorRabbitMQ;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsumidorRabbitMQExecutable {
    public static void main(String[] args) {
        // Cria um agendador para iniciar o consumidor após 10 segundos
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> {
            System.out.println("\n--- Iniciando Consumidor RabbitMQ ---");
            ConsumidorRabbitMQ consumidor = new ConsumidorRabbitMQ();
            new Thread(consumidor).start();
        }, 10, TimeUnit.SECONDS); 

        try {
            
            Thread.sleep(TimeUnit.MINUTES.toMillis(5)); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Consumidor RabbitMQ parou.");
        } finally {
            scheduler.shutdownNow();
        }
    }
}