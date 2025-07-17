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
    private final int[] tempos = {2000, 3000, 4000, 5000};
    private MqttClient mqttClient;
    private final String BROKER = "tcp://broker.emqx.io:1883";
    private final String TOPICO_BASE = "drones/";
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> disconnectionTask;
    private volatile boolean simulatedDisconnected = false;

    public Drone(Posicao posicao) {
        this.posicao = posicao;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            String clientId = MqttClient.generateClientId();
            this.mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setConnectionTimeout(10);
            connectOptions.setKeepAliveInterval(20);

            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("!!! Drone " + posicao + " PERDEU CONEXÃO: " + cause.getMessage() + " !!!");
                    simulatedDisconnected = true;
                }

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println("+++ Drone " + posicao + " CONEXÃO RESTABELECIDA (reconnect=" + reconnect + ") +++");
                    simulatedDisconnected = false;
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // é um produtor, nao usa isso
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // é um produtor, nao usa isso
                }
            });

            mqttClient.connect(connectOptions);
            System.out.println("Drone " + posicao + " conectado ao broker MQTT: " + BROKER);

            scheduleSimulatedDisconnection();

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
            if (!simulatedDisconnected) { //nao publica se estiver desconectado
                enviarPorMQTT(dados);
            } else {
                System.out.println("Drone " + posicao + ": Em período de desconexão/reconexão. Não publicando.");
            }
            try {
                Thread.sleep(tempoAleatorio);
            } catch (InterruptedException e) {
                System.out.println("Drone " + posicao + " interrompido. Finalizando tarefas.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        cleanup();
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
        long initialDelay = 50 + rand.nextInt(20); // DESCONEXAO
        long period = 30 + rand.nextInt(30);      // REPETIR A CADA 30-60 SEGUNDOS

        System.out.println("Drone " + posicao + ": Agendando desconexão inicial em " + initialDelay + "s, repetindo a cada " + period + "s.");

        disconnectionTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (mqttClient.isConnected()) {
                    System.out.println("--- SIMULANDO FALHA --- Drone " + posicao + " desconectando-se do broker.");
                    mqttClient.disconnectForcibly(100); //desconecta o drone

                    System.out.println("--- SIMULANDO FALHA --- Drone " + posicao + " desconectado. Aguardando reconexão automática...");
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
