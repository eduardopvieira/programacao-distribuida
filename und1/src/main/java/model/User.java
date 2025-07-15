package model;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class User implements Runnable {

    // Conecta-se diretamente ao broker que o DataCenter usa para distribuir dados em tempo real
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String TOPICO_ASSINATURA = "dados_climaticos_tempo_real/#";

    @Override
    public void run() {
        try {
            String clientId = MqttClient.generateClientId();
            MqttClient client = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            // Define o callback para lidar com as mensagens recebidas e perdas de conexão
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("❌ Conexão com o broker perdida! Tentando reconectar... Causa: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String dadosRecebidos = new String(message.getPayload(), StandardCharsets.UTF_8);
                    System.out.println("[DADO EM TEMPO REAL] Tópico: " + topic + " | Dados: " + dadosRecebidos);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Não aplicável para um cliente que apenas assina
                }
            });

            System.out.println("✅ [Usuário] Conectando ao broker MQTT em " + BROKER_URL);
            client.connect(options);
            System.out.println("✅ [Usuário] Conectado! Assinando o tópico: " + TOPICO_ASSINATURA);

            // Assina o tópico com Qualidade de Serviço 1 (pelo menos uma vez)
            client.subscribe(TOPICO_ASSINATURA, 1);

            System.out.println("✅ [Usuário] Aguardando dados em tempo real... Pressione Enter para sair.");

            // Mantém a aplicação rodando até que o usuário pressione Enter
            System.in.read();

            client.disconnect();
            System.out.println("✅ [Usuário] Desconectado.");

        } catch (MqttException | IOException e) {
            System.err.println("X [Usuário] Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
