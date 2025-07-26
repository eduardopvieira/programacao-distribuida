package model;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ConsumidorMQTTTempoReal implements Runnable {

    private final String BROKER_HOST = "tcp://broker.hivemq.com:1883";
    private final String TOPICO_BASE = "dados_climaticos_tempo_real/";
    private MqttClient mqttClient;

    public ConsumidorMQTTTempoReal() {
        try {
            String clientId = MqttClient.generateClientId();
            mqttClient = new MqttClient(BROKER_HOST, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("[TempoReal-MQTT] Conexão perdida: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    System.out.printf("[TempoReal-MQTT] << %s : %s\n", topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            mqttClient.connect(options);
        } catch (MqttException e) {
            System.err.println("Erro ao inicializar ConsumidorMQTTTempoReal: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            setupSubscription();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("[TempoReal-MQTT] Erro: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            closeConnection();
        }
    }

    private void setupSubscription() throws Exception {
        System.out.println("\n--- Consumidor de Tempo Real (MQTT) ---");
        System.out.println("Escolha os dados para acompanhar (separados por vírgula):");
        System.out.println("  1. Todos (#) | 2. Norte (norte/dados) | 3. Sul (sul/dados) | 4. Leste (leste/dados) | 5. Oeste (oeste/dados)");
        System.out.print("Sua escolha: ");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String[] choices = reader.readLine().split(",");

        List<String> topicFilters = Arrays.stream(choices)
                .map(String::trim)
                .map(s -> switch (s) {
                    case "1" -> TOPICO_BASE + "#";
                    case "2" -> TOPICO_BASE + "norte/dados";
                    case "3" -> TOPICO_BASE + "sul/dados";
                    case "4" -> TOPICO_BASE + "leste/dados";
                    case "5" -> TOPICO_BASE + "oeste/dados";
                    default -> "";
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (!topicFilters.isEmpty()) {
            for (String filter : topicFilters) {
                mqttClient.subscribe(filter, 1);
                System.out.println("[TempoReal-MQTT] Assinado no tópico: " + filter);
            }
        }
    }

    private void closeConnection() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            System.err.println("[TempoReal-MQTT] Erro ao desconectar: " + e.getMessage());
        }
    }
}
