package model;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

public class DataCenter implements Runnable {

    private MqttClient mqttClientDrones;
    private final String BROKER_MQTT_DRONES = "tcp://broker.emqx.io:1883";
    private final String TOPICO_ASSINATURA_DRONES = "drones/#"; // Assina todos os drones
    private Connection connectionRabbitMQ;
    private Channel channelRabbitMQ;
    private final String EXCHANGE_RABBITMQ = "dados_climaticos_historico"; 
    private MqttClient mqttClientTempoReal;
    private final String BROKER_MQTT_TEMPO_REAL = "tcp://broker.hivemq.com:1883"; 
    private final String TOPICO_BASE_MQTT_TEMPO_REAL = "dados_climaticos_tempo_real/"; 


    @Override
    public void run() {
        try {
            initializeMqttClientDrones();                       // Inicializa o cliente MQTT para consumir dos drones
            initializeRabbitMQ();                               // Inicializa o RabbitMQ como publicador
            initializeMqttClientTempoReal();                    // Inicializa o MQTT para tempo real como publicador

            System.out.println("DataCenter: Gateway em operação. Consumindo drones e publicando em RabbitMQ/MQTT.");
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000); 
            }

        } catch (MqttException e) {
            System.err.println("DataCenter: Erro de MQTT durante a inicialização ou operação: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException | TimeoutException e) { 
            System.err.println("DataCenter: Erro de RabbitMQ/I/O durante a inicialização: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("DataCenter interrompido.");
            Thread.currentThread().interrupt();
        } finally {
            closeConnections(); 
        }
    }

    private void initializeMqttClientDrones() throws MqttException {
        String clientId = MqttClient.generateClientId() + "_DataCenter_Drones";
        this.mqttClientDrones = new MqttClient(BROKER_MQTT_DRONES, clientId, new MemoryPersistence());

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setConnectionTimeout(10);
        connectOptions.setKeepAliveInterval(20);

        mqttClientDrones.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[DataCenter] Conexão MQTT com broker de drones perdida: " + cause.getMessage());
            }
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String dadosRecebidos = new String(message.getPayload(), StandardCharsets.UTF_8);
                String[] topicParts = topic.split("/");
                String posicaoDrone = topicParts.length > 1 ? topicParts[1] : "desconhecido"; 

                System.out.println("[DataCenter] Recebeu do tópico '" + topic + "': " + dadosRecebidos);

                String mensagemPadronizada = padronizarMensagem(dadosRecebidos);
                System.out.println("[DataCenter] Mensagem padronizada: " + mensagemPadronizada);

                // --- Publica a mensagem padronizada nos novos endpoints ---
                publicarEmRabbitMQ(posicaoDrone, mensagemPadronizada); 
                publicarEmMQTTParaTempoReal(posicaoDrone, mensagemPadronizada); 

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        mqttClientDrones.connect(connectOptions);
        mqttClientDrones.subscribe(TOPICO_ASSINATURA_DRONES, 1);
        System.out.println("DataCenter conectado ao broker MQTT de drones e assinado no tópico: " + TOPICO_ASSINATURA_DRONES);
    }

    private void initializeRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory(); 
        factory.setHost("localhost"); 
        connectionRabbitMQ = factory.newConnection(); 
        channelRabbitMQ = connectionRabbitMQ.createChannel(); 
        channelRabbitMQ.exchangeDeclare(EXCHANGE_RABBITMQ, "topic");
        System.out.println("DataCenter conectado ao broker RabbitMQ e exchange '" + EXCHANGE_RABBITMQ + "' declarado.");
    }

    private void initializeMqttClientTempoReal() throws MqttException {
        String clientId = MqttClient.generateClientId() + "_DataCenter_TempoReal";
        this.mqttClientTempoReal = new MqttClient(BROKER_MQTT_TEMPO_REAL, clientId, new MemoryPersistence());

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setConnectionTimeout(10);
        connectOptions.setKeepAliveInterval(20);

        mqttClientTempoReal.connect(connectOptions);
        System.out.println("DataCenter conectado ao broker MQTT para tempo real: " + BROKER_MQTT_TEMPO_REAL);
    }

    private void publicarEmRabbitMQ(String posicaoDrone, String mensagemPadronizada) {
        String routingKey = posicaoDrone.toLowerCase() + ".dados";
        try {
            channelRabbitMQ.basicPublish(EXCHANGE_RABBITMQ, routingKey, null, mensagemPadronizada.getBytes(StandardCharsets.UTF_8));
            System.out.println("[DataCenter-RabbitMQ] Publicou na chave '" + routingKey + "': " + mensagemPadronizada);
        } catch (IOException e) {
            System.err.println("[DataCenter-RabbitMQ] Erro ao publicar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void publicarEmMQTTParaTempoReal(String posicaoDrone, String mensagemPadronizada) {
        String topicoTempoReal = TOPICO_BASE_MQTT_TEMPO_REAL + posicaoDrone.toLowerCase() + "/dados";
        try {
            MqttMessage message = new MqttMessage(mensagemPadronizada.getBytes(StandardCharsets.UTF_8));
            message.setQos(1); // QoS 1 para tempo real
            mqttClientTempoReal.publish(topicoTempoReal, message);
            System.out.println("[DataCenter-MQTT-TempoReal] Publicou no tópico '" + topicoTempoReal + "': " + mensagemPadronizada);
        } catch (MqttException e) {
            System.err.println("[DataCenter-MQTT-TempoReal] Erro ao publicar: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Método padronizarMensagem permanece o mesmo, mas a extração da posição é feita no callback
    public String padronizarMensagem(String msg) {
        UnaryOperator<String> replaceHyphen = s -> s.replace("-", "|"); 
        UnaryOperator<String> replaceParentheses = s -> s.replace("(", "").replace(")", "").replace(";", "|"); 
        UnaryOperator<String> replaceBraces = s -> s.replace("{", "").replace("}", "").replace(",", "|"); 
        UnaryOperator<String> replaceHash = s -> s.replace("#", "|"); 

        String[] valores;
        String limpo = Optional.ofNullable(msg)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    if (s.contains("-")) return replaceHyphen.apply(s);
                    if (s.contains("(")) return replaceParentheses.apply(s);
                    if (s.contains("{")) return replaceBraces.apply(s);
                    if (s.contains("#")) return replaceHash.apply(s);
                    return s;
                })
                .orElseThrow(() -> new IllegalArgumentException("Mensagem inválida: " + msg));

        valores = limpo.split("\\|"); // Divide por '|''

        if (valores.length == 4) {
            // Reordenar para [temperatura | umidade | pressao | radiacao]
            
            String pressao = valores[0];
            String radiacao = valores[1];
            String temperatura = valores[2];
            String umidade = valores[3];

            return String.format("[%s | %s | %s | %s]", temperatura, umidade, pressao, radiacao);
        } else {
            System.err.println("Formato de mensagem inesperado após padronização: " + limpo);
            return limpo; 
        }
    }


    // Método para fechar todas as conexões
    private void closeConnections() {
        try {
            if (mqttClientDrones != null && mqttClientDrones.isConnected()) {
                mqttClientDrones.disconnect();
            }
            if (mqttClientTempoReal != null && mqttClientTempoReal.isConnected()) {
                mqttClientTempoReal.disconnect();
            }
            if (channelRabbitMQ != null && channelRabbitMQ.isOpen()) {
                channelRabbitMQ.close();
            }
            if (connectionRabbitMQ != null && connectionRabbitMQ.isOpen()) {
                connectionRabbitMQ.close();
            }
        } catch (MqttException e) {
            System.err.println("Erro ao desconectar cliente MQTT: " + e.getMessage());
        } catch (IOException | TimeoutException e) {
            System.err.println("Erro ao fechar conexão RabbitMQ: " + e.getMessage());
        }
    }

}