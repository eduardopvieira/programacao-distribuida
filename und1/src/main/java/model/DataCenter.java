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

    // Atributos MQTT para consumir dos drones (já implementado)
    private MqttClient mqttClientDrones;
    private final String BROKER_MQTT_DRONES = "tcp://broker.emqx.io:1883";
    private final String TOPICO_ASSINATURA_DRONES = "drones/#"; // Assina todos os drones

    // --- NOVOS ATRIBUTOS PARA PUBLICAR PARA USUÁRIOS/DASHBOARDS ---

    // Atributos para RabbitMQ (para histórico/dashboard)
    private Connection connectionRabbitMQ;
    private Channel channelRabbitMQ;
    private final String EXCHANGE_RABBITMQ = "dados_climaticos_historico"; // Nome do exchange para RabbitMQ

    // Atributos para MQTT (para tempo real/dashboard dinâmico)
    private MqttClient mqttClientTempoReal;
    private final String BROKER_MQTT_TEMPO_REAL = "tcp://broker.hivemq.com:1883"; // Usando outro broker para diferenciar
    private final String TOPICO_BASE_MQTT_TEMPO_REAL = "dados_climaticos_tempo_real/"; // Tópico base para MQTT tempo real


    @Override
    public void run() {
        try {
            initializeMqttClientDrones();
            initializeRabbitMQ();
            initializeMqttClientTempoReal();

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
                // Extrai a posição do drone do tópico para usar como chave de roteamento
                String[] topicParts = topic.split("/");
                String posicaoDrone = topicParts.length > 1 ? topicParts[1] : "desconhecido"; // ex: "norte" de "drones/norte/dados"

                System.out.println("[DataCenter] Recebeu do tópico '" + topic + "': " + dadosRecebidos);

                String mensagemPadronizada = padronizarMensagem(dadosRecebidos);
                System.out.println("[DataCenter] Mensagem padronizada: " + mensagemPadronizada);

                // --- Publica a mensagem padronizada nos novos endpoints ---
                publicarEmRabbitMQ(posicaoDrone, mensagemPadronizada); // [cite: 39]
                publicarEmMQTTParaTempoReal(posicaoDrone, mensagemPadronizada); // [cite: 47]

                // O envio via Multicast UDP para Servidores será removido em breve
                // enviarMensagemParaServidores(mensagemPadronizada);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Não aplicável para o DataCenter como consumidor
            }
        });

        mqttClientDrones.connect(connectOptions);
        mqttClientDrones.subscribe(TOPICO_ASSINATURA_DRONES, 1);
        System.out.println("DataCenter conectado ao broker MQTT de drones e assinado no tópico: " + TOPICO_ASSINATURA_DRONES);
    }

    private void initializeRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory(); //
        factory.setHost("localhost"); // Usaremos localhost para o RabbitMQ (assumindo que esteja rodando localmente)
        // Você pode configurar usuário/senha se necessário: factory.setUsername("guest"); factory.setPassword("guest");

        connectionRabbitMQ = factory.newConnection(); //
        channelRabbitMQ = connectionRabbitMQ.createChannel(); //
        // Declara o exchange como 'topic' para roteamento flexível
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
        // A chave de roteamento para RabbitMQ pode ser 'regiao.dados', ex: 'norte.dados'
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
        // O tópico para MQTT tempo real pode ser 'dados_climaticos_tempo_real/regiao/dados', ex: 'dados_climaticos_tempo_real/norte/dados'
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
        UnaryOperator<String> replaceHyphen = s -> s.replace("-", "|"); // Alterado para "|"
        UnaryOperator<String> replaceParentheses = s -> s.replace("(", "").replace(")", "").replace(";", "|"); // Alterado para "|"
        UnaryOperator<String> replaceBraces = s -> s.replace("{", "").replace("}", "").replace(",", "|"); // Alterado para "|"
        UnaryOperator<String> replaceHash = s -> s.replace("#", "|"); // Alterado para "|"

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

        valores = limpo.split("\\|"); // Divide por '|'

        if (valores.length == 4) {
            // Reordenar para [temperatura | umidade | pressao | radiacao]
            // A ordem original dos drones é: pressao, radiacao, temperatura, umidade
            String pressao = valores[0];
            String radiacao = valores[1];
            String temperatura = valores[2];
            String umidade = valores[3];

            return String.format("[%s | %s | %s | %s]", temperatura, umidade, pressao, radiacao);
        } else {
            System.err.println("Formato de mensagem inesperado após padronização: " + limpo);
            return limpo; // Retorna o limpo para não quebrar, mas com aviso
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

    // O método 'enviarMensagemParaServidores' e as classes de Servidor/LocServer/User baseados em Multicast/TCP
    // não são mais o caminho principal para a disponibilização dos dados a usuários.
    // Eles serão ajustados para consumir dos brokers RabbitMQ e MQTT.
    // Por isso, este método original será removido no próximo passo ou mantido como esqueleto se houver outro uso.
    // public void enviarMensagemParaServidores(String mensagem) { /* ... */ }
    // Por enquanto, vou comentar a chamada dentro de messageArrived.

    // Removendo este método pois ele será substituído pela publicação em RabbitMQ/MQTT
    private void enviarMensagemParaServidores(String mensagem) {
        // ESTE MÉTODO ESTÁ OBSOLETO E SERÁ REMOVIDO OU REFEITO EM PRÓXIMOS PASSOS
        // Não precisamos mais enviar via UDP Multicast para Servidores genéricos,
        // pois a disponibilização será via RabbitMQ e MQTT.
        // A lógica do Servidor e User será reescrita para consumir diretamente desses brokers.
        System.out.println("[DataCenter] (Aviso) Chamada ao método obsoleto 'enviarMensagemParaServidores': " + mensagem);
    }
}
