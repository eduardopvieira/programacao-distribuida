package model;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ConsumidorMQTTTempoReal implements Runnable {

    private final String BROKER_HOST = "tcp://broker.hivemq.com:1883"; // Mesmo broker do DataCenter para tempo real
    private final String TOPICO_BASE = "dados_climaticos_tempo_real/"; // Mesmo tópico base do DataCenter

    private MqttClient mqttClient;

    public ConsumidorMQTTTempoReal() {
        try {
            initializeMqttClient();
        } catch (MqttException e) {
            System.err.println("Erro ao inicializar ConsumidorMQTTTempoReal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeMqttClient() throws MqttException {
        String clientId = MqttClient.generateClientId() + "_ConsumidorTempoReal";
        mqttClient = new MqttClient(BROKER_HOST, clientId, new MemoryPersistence());

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setConnectionTimeout(10);
        connectOptions.setKeepAliveInterval(20);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[ConsumidorMQTTTempoReal] Conexão perdida: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println(String.format("[ConsumidorMQTTTempoReal] Recebeu do tópico '%s' (QoS %d): '%s'",
                        topic, message.getQos(), payload));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                
            }
        });

        mqttClient.connect(connectOptions);
        System.out.println("[ConsumidorMQTTTempoReal] Conectado ao broker MQTT: " + BROKER_HOST);
    }

    @Override
    public void run() {
        if (!mqttClient.isConnected()) {
            System.err.println("[ConsumidorMQTTTempoReal] Cliente MQTT não conectado. Encerrando.");
            return;
        }

        try {
            setupSubscription(); 
        } catch (MqttException | InterruptedException e) {
            System.err.println("[ConsumidorMQTTTempoReal] Erro ao configurar assinatura ou processar mensagens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    System.out.println("[ConsumidorMQTTTempoReal] Desconectado do broker MQTT.");
                }
            } catch (MqttException e) {
                System.err.println("[ConsumidorMQTTTempoReal] Erro ao desconectar: " + e.getMessage());
            }
        }
    }

    private void setupSubscription() throws MqttException, InterruptedException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Consumidor MQTT Tempo Real ---");
        System.out.println("Escolha os dados que deseja receber (filtros de tópico):");
        System.out.println("  1. Todos os dados (" + TOPICO_BASE + "#)");
        System.out.println("  2. Dados da região NORTE (" + TOPICO_BASE + "norte/dados)");
        System.out.println("  3. Dados da região SUL (" + TOPICO_BASE + "sul/dados)");
        System.out.println("  4. Dados da região LESTE (" + TOPICO_BASE + "leste/dados)");
        System.out.println("  5. Dados da região OESTE (" + TOPICO_BASE + "oeste/dados)");
        System.out.println("  6. Dados de TEMPERATURA de todas as regiões (" + TOPICO_BASE + "+/temperatura)"); // Ajuste aqui se o formato mudar
        System.out.println("  7. Dados de UMIDADE de todas as regiões (" + TOPICO_BASE + "+/umidade)");
        System.out.println("  8. Dados de PRESSAO de todas as regiões (" + TOPICO_BASE + "+/pressao)");
        System.out.println("  9. Dados de RADIACAO de todas as regiões (" + TOPICO_BASE + "+/radiacao)");
        System.out.println("  ( Pode combinar filtros separando por virgula, (2,4 = dados de Norte e Leste)");
        System.out.print("Sua escolha: ");

        String choice = null;
        try {
            choice = consoleReader.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<String> topicFilters = parseChoices(choice);

        if (!topicFilters.isEmpty()) {
            
            String[] topicsArray = topicFilters.toArray(new String[0]);
            int[] qosArray = new int[topicsArray.length];
            Arrays.fill(qosArray, 1); // QoS 1 para todas as assinaturas

            mqttClient.subscribe(topicsArray, qosArray);
            System.out.println("[ConsumidorMQTTTempoReal] Assinado nos tópicos: " + topicFilters);
        } else {
            System.out.println("[ConsumidorMQTTTempoReal] Nenhuma assinatura válida selecionada.");
        }

        System.out.println("[ConsumidorMQTTTempoReal] [*] Esperando mensagens. Para sair, feche a aplicação.");
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(1000);
        }
    }

    private List<String> parseChoices(String choice) {
        String[] rawChoices = choice.split(",");
        return Arrays.stream(rawChoices)
                .map(String::trim)
                .map(s -> {
                    switch (s) {
                        case "1": return TOPICO_BASE + "#"; // Todos os dados
                        case "2": return TOPICO_BASE + "norte/dados";
                        case "3": return TOPICO_BASE + "sul/dados";
                        case "4": return TOPICO_BASE + "leste/dados";
                        case "5": return TOPICO_BASE + "oeste/dados";
                        case "6": return TOPICO_BASE + "+/temperatura"; 
                        case "7": return TOPICO_BASE + "+/umidade";
                        case "8": return TOPICO_BASE + "+/pressao";
                        case "9": return TOPICO_BASE + "+/radiacao";
                        default: return ""; 
                    }
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList(); 
    }
}