package executables;

import model.ConsumidorMQTTTempoReal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsumidorMQTTTempoRealExecutable {
    public static void main(String[] args) {
        // Cria um agendador para iniciar o consumidor após 10 segundos
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> {
            System.out.println("\n--- Iniciando Consumidor MQTT Tempo Real ---");
            ConsumidorMQTTTempoReal consumidor = new ConsumidorMQTTTempoReal();
            new Thread(consumidor).start();
        }, 10, TimeUnit.SECONDS); // Inicia após 10 segundos [cite: 59]

        // Mantém o scheduler ativo por um tempo, se necessário, ou até a aplicação ser fechada manualmente
        // ou por outro scheduler que derrube o executor principal.
        try {
            // Deixa o main thread ativa por um tempo para que o consumidor possa rodar
            Thread.sleep(TimeUnit.MINUTES.toMillis(5)); // Ex: mantém por 5 minutos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Consumidor MQTT Tempo Real Executable interrompido.");
        } finally {
            scheduler.shutdownNow();
        }
    }
}