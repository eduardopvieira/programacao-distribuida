package executables;

import model.*;
import model.auxiliar.Posicao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class Simulacao {

    public static void main(String[] args) {
        // Executor para gerenciar todas as threads da simulação
        ExecutorService executor = Executors.newCachedThreadPool();
        // Agendador para encerrar a simulação após um tempo determinado
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        System.out.println(">>> INICIANDO A SIMULAÇÃO DE COLETA DE DADOS CLIMÁTICOS <<<");

        try {
            // Componentes da simulação
            DataCenter dataCenter = new DataCenter();
            List<Drone> drones = List.of(
                    new Drone(Posicao.NORTE),
                    new Drone(Posicao.SUL),
                    new Drone(Posicao.LESTE),
                    new Drone(Posicao.OESTE)
            );
            ConsumidorRabbitMQ consumidorRabbitMQ = new ConsumidorRabbitMQ();
            ConsumidorMQTTTempoReal consumidorTempoReal = new ConsumidorMQTTTempoReal();

            // 1. Inicia os Drones e o DataCenter
            System.out.println("[Simulação] Iniciando os Drones e o DataCenter...");
            drones.forEach(executor::submit);
            executor.submit(dataCenter);

            // 2. Aguarda 10 segundos antes de iniciar os consumidores
            int delayConsumidores = 10;
            System.out.printf("[Simulação] Aguardando %d segundos para iniciar os consumidores...\n", delayConsumidores);
            Thread.sleep(delayConsumidores * 1000);

            // 3. Inicia os Consumidores
            System.out.println("[Simulação] Iniciando os consumidores...");
            executor.submit(consumidorRabbitMQ);
            executor.submit(consumidorTempoReal);

            // 4. Agenda o encerramento da simulação
            long tempoDeSimulacaoMinutos = 3;
            System.out.printf("[Simulação] A simulação será encerrada em %d minutos.\n", tempoDeSimulacaoMinutos);

            Runnable shutdownTask = () -> {
                System.out.println("\n>>> TEMPO DE SIMULAÇÃO ESGOTADO. ENCERRANDO... <<<");
                // Interrompe todas as threads
                executor.shutdownNow();
                // Limpeza final dos drones
                drones.forEach(Drone::cleanup);
                System.out.println(">>> SIMULAÇÃO FINALIZADA. <<<");
            };

            scheduler.schedule(shutdownTask, tempoDeSimulacaoMinutos, TimeUnit.MINUTES);

            // Aguarda o encerramento do executor principal
            executor.awaitTermination(tempoDeSimulacaoMinutos + 1, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            System.err.println("[Simulação] A thread principal foi interrompida.");
            Thread.currentThread().interrupt();
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
            scheduler.shutdown();
            System.out.println("[Simulação] Recursos de execução liberados.");
        }
    }
}
