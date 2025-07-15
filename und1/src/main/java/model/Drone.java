package model;

import model.auxiliar.Posicao;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Locale;
import java.util.Random;

public class Drone implements Runnable {
    private Posicao posicao;
    private final int[] tempos = {2000, 3000, 4000, 5000};// Intervalo de 2 a 5 segundos

    // Atributos MQTT
    private MqttClient mqttClient;
    private final String BROKER = "tcp://broker.emqx.io:1883"; // Usando um broker público
    private final String TOPICO_BASE = "drones/"; // Tópico base para os drones

    public Drone(Posicao posicao) {
        this.posicao = posicao;
        try {
            // Inicializa o cliente MQTT com um ID único e persistência em memória
            String clientId = MqttClient.generateClientId(); // Gera um ID único para o cliente
            this.mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            // Configura opções de conexão
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); // Sessão limpa
            // Conecta ao broker
            mqttClient.connect(connectOptions);
            System.out.println("Drone " + posicao + " conectado ao broker MQTT: " + BROKER);
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
        while (!Thread.currentThread().isInterrupted()) { // Loop roda até a thread ser interrompida
            int tempoAleatorio = tempos[new Random().nextInt(tempos.length)];
            String dados = gerarDados();
            enviarPorMQTT(dados); // Chama o novo método de envio MQTT
            try {
                Thread.sleep(tempoAleatorio);
            } catch (InterruptedException e) {
                System.out.println("Drone " + posicao + " interrompido.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
                System.out.println("Drone " + posicao + " desconectado do broker MQTT.");
            }
        } catch (MqttException e) {
            System.err.println("Erro ao desconectar o Drone " + posicao + ": " + e.getMessage());
        }
    }

    public String gerarDados() {
        // Gera números aleatórios dentro de uma faixa aceitável para cada elemento climático [cite: 56]
        double temperatura = 10 + new Random().nextDouble() * (45 - 10); // ex: 10 a 45 °C
        double pressao = 950 + new Random().nextDouble() * (1050 - 950); // ex: 950 a 1050 hPa
        double radiacao = 0 + new Random().nextDouble() * (1000 - 0); // ex: 0 a 1000 W/m²
        double umidade = 30 + new Random().nextDouble() * (80 - 30); // ex: 30 a 80 %

        return formatarDados(pressao, radiacao, temperatura, umidade);
    }

    public String formatarDados(double pressao, double radiacao, double temperatura, double umidade) {
        // Formata os dados de acordo com a posição do drone [cite: 8, 9, 10, 11, 12]
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
        String topicoDrone = TOPICO_BASE + posicao.name().toLowerCase() + "/dados"; // Ex: "drones/norte/dados"
        try {
            MqttMessage mqttMessage = new MqttMessage(msg.getBytes());
            mqttMessage.setQos(1); // Usando QoS 1 para garantir entrega "pelo menos uma vez"
            mqttClient.publish(topicoDrone, mqttMessage);
            System.out.println("Drone " + this.posicao + " publicou no tópico '" + topicoDrone + "': " + msg);
        } catch (MqttException e) {
            System.err.println("Erro ao publicar mensagem do Drone " + posicao + ": " + e.getMessage());
            // Aqui você pode adicionar lógica para tentar reenviar, logar, etc.
        }
    }
}