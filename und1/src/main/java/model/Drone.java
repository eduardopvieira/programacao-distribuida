package model;

import model.auxiliar.Posicao;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Drone implements Runnable {
    private Posicao posicao;
    private final int[] tempos = {2000, 3000, 4000, 5000};// Intervalo de 2 a 5 segundos
    private MqttClient mqttClient;
    private final String BROKER = "tcp://broker.emqx.io:1883"; // Usando um broker público
    private final String TOPICO_BASE = "drones/";  // Tópico base para os drones
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> disconnectionTask;
    private volatile boolean simulatedDisconnected = false; // Flag para controlar publicações durante desconexão simulada

    public Drone(Posicao posicao) {
        this.posicao = posicao;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            String clientId = MqttClient.generateClientId(); // Gera um ID único para o cliente
            this.mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); //
            connectOptions.setAutomaticReconnect(true); // Habilita reconexão automática
            connectOptions.setConnectionTimeout(10); //
            connectOptions.setKeepAliveInterval(20); //

            // Adicionar MqttCallbackExtended para monitorar a conexão
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("!!! Drone " + posicao + " PERDEU CONEXÃO: " + cause.getMessage() + " !!!");
                    simulatedDisconnected = true; // Parar publicações quando a conexão for perdida
                }

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println("+++ Drone " + posicao + " CONEXÃO RESTABELECIDA (reconnect=" + reconnect + ") +++");
                    simulatedDisconnected = false; // Retomar publicações quando a conexão for restabelecida
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // Este é um produtor, então messageArrived não é usado.
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Este é um produtor, e deliveryComplete confirma o envio de mensagens QoS > 0.
                    // Não é estritamente necessário para este exercício, mas pode ser usado para depuração.
                }
            });

            mqttClient.connect(connectOptions); // Conexão inicial
            System.out.println("Drone " + posicao + " conectado ao broker MQTT: " + BROKER);

            scheduleSimulatedDisconnection(); // Agendando desconexão simulada

        } catch (MqttException e) {
            System.err.println("Erro ao conectar o Drone " + posicao + " ao broker MQTT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        if (!mqttClient.isConnected()) {
            System.err.println("Drone " + posicao + ": Cliente MQTT não conectado. Encerrando.");
            return;
        }

        System.out.println("Drone iniciado na posição " + posicao);
        while (!Thread.currentThread().isInterrupted()) {
            int tempoAleatorio = tempos[new Random().nextInt(tempos.length)];
            String dados = gerarDados();
            if (!simulatedDisconnected) { // Publica apenas se não estiver na fase de "desconectado simulado"
                enviarPorMQTT(dados);
            } else {
                System.out.println("Drone " + posicao + ": Em período de desconexão/reconexão. Não publicando."); // Mensagem ajustada
            }
            try {
                Thread.sleep(tempoAleatorio);
            } catch (InterruptedException e) {
                System.out.println("Drone " + posicao + " interrompido. Finalizando tarefas.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        cleanup(); // Chama o cleanup
    }

    public String gerarDados() {
        double temperatura = 10 + new Random().nextDouble() * (45 - 10);
        double pressao = 950 + new Random().nextDouble() * (1050 - 950);
        double radiacao = 0 + new Random().nextDouble() * (1000 - 0);
        double umidade = 30 + new Random().nextDouble() * (80 - 30);

        return formatarDados(pressao, radiacao, temperatura, umidade);
    }

    public String formatarDados(double pressao, double radiacao, double temperatura, double umidade) {
        switch (posicao) {
            case NORTE:
                return String.format(Locale.US, "%.2f-%.2f-%.2f-%.2f", pressao, radiacao, temperatura, umidade); 
            case SUL:
                return String.format(Locale.US, "(%.2f;%.2f;%.2f;%.2f)", pressao, radiacao, temperatura, umidade); 
            case LESTE:
                return String.format(Locale.US, "{%.2f,%.2f,%.2f,%.2f}", pressao, radiacao, temperatura, umidade);
            case OESTE:
                return String.format(Locale.US, "%.2f#%.2f#%.2f#%.2f", pressao, radiacao, temperatura, umidade); 
            default:
                throw new IllegalArgumentException("Posição do drone desconhecida: " + posicao);
        }
    }

    private void enviarPorMQTT(String msg) {
        String topicoDrone = TOPICO_BASE + posicao.name().toLowerCase() + "/dados";
        try {
            if (mqttClient.isConnected()) { // Verifica se está conectado antes de tentar publicar
                MqttMessage mqttMessage = new MqttMessage(msg.getBytes());
                mqttMessage.setQos(1);
                mqttClient.publish(topicoDrone, mqttMessage);
                System.out.println("Drone " + this.posicao + " publicou no tópico '" + topicoDrone + "': " + msg);
            } else {
                System.out.println("Drone " + this.posicao + " não conectado. Mensagem não publicada: " + msg); //
            }
        } catch (MqttException e) {
            System.err.println("Erro ao publicar mensagem do Drone " + posicao + ": " + e.getMessage());
        }
    }

    private void scheduleSimulatedDisconnection() {
        Random rand = new Random();
        long initialDelay = 20 + rand.nextInt(20); // Primeira desconexão entre 20 e 40 segundos
        long period = 30 + rand.nextInt(30);      // Repetir a cada 30 a 60 segundos

        System.out.println("Drone " + posicao + ": Agendando desconexão inicial em " + initialDelay + "s, repetindo a cada " + period + "s.");

        disconnectionTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (mqttClient.isConnected()) {
                    System.out.println("--- SIMULANDO FALHA --- Drone " + posicao + " desconectando-se do broker.");
                    // simulatedDisconnected = true; // Esta flag agora será controlada pelo connectionLost
                    mqttClient.disconnectForcibly(100); // Desconecta forçadamente com um pequeno tempo de espera
                    System.out.println("--- SIMULANDO FALHA --- Drone " + posicao + " desconectado. Aguardando reconexão automática..."); // Mensagem ajustada
                    // A reconexão é automática devido ao setAutomaticReconnect(true)
                }
            } catch (MqttException e) {
                System.err.println("Erro ao simular desconexão do Drone " + posicao + ": " + e.getMessage());
            }
        }, initialDelay, period, TimeUnit.SECONDS);
    }

    public void cleanup() {
        if (disconnectionTask != null) {
            disconnectionTask.cancel(false); 
        }
        scheduler.shutdownNow(); 
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                System.out.println("Drone " + posicao + " desconectado do broker MQTT.");
            }
        } catch (MqttException e) {
            System.err.println("Erro ao desconectar o Drone " + posicao + ": " + e.getMessage());
        }
    }
}