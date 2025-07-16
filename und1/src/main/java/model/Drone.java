package model;

import model.auxiliar.Posicao;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Locale;
import java.util.Random;

public class Drone implements Runnable {
    private Posicao posicao;
    private final int[] tempos = {2000, 3000, 4000, 5000}; // Intervalo de 2 a 5 segundos

    
    private MqttClient mqttClient;
    private final String BROKER = "tcp://broker.emqx.io:1883"; 
    private final String TOPICO_BASE = "drones/"; 

    public Drone(Posicao posicao) {
        this.posicao = posicao;
        try {
            
            String clientId = MqttClient.generateClientId(); 
            this.mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true); // Sessão limpa
            
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
        while (!Thread.currentThread().isInterrupted()) { 
            int tempoAleatorio = tempos[new Random().nextInt(tempos.length)];
            String dados = gerarDados();
            enviarPorMQTT(dados); 
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
            MqttMessage mqttMessage = new MqttMessage(msg.getBytes());
            mqttMessage.setQos(1); // Usando QoS 1 para garantir entrega 
            mqttClient.publish(topicoDrone, mqttMessage);
            System.out.println("Drone " + this.posicao + " publicou no tópico '" + topicoDrone + "': " + msg);
        } catch (MqttException e) {
            System.err.println("Erro ao publicar mensagem do Drone " + posicao + ": " + e.getMessage());
        }
    }
}