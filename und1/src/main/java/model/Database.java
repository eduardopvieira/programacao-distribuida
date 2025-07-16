package model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Database implements Runnable {


    private Connection connectionRabbitMQ;
    private Channel channelRabbitMQ;
    private final String BROKER_HOST = "localhost";
    private final String EXCHANGE_NAME = "dados_climaticos_historico"; 
    private String queueName; 

    private long totalDadosColetadosHistorico = 0;
    private Map<String, Long> totalPorElementoHistorico = new HashMap<>();
    private Map<String, Map<String, Double>> ultimosValoresPorRegiaoEElementoHistorico = new HashMap<>();
    private Map<String, Long> contagemPorRegiaoHistorico = new HashMap<>();

    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");

    @Override
    public void run() {
        try {
            initializeRabbitMQConsumer();
            System.out.println("Database: Conectado ao RabbitMQ e aguardando mensagens de histórico.");

            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }

        } catch (IOException | TimeoutException e) {
            System.err.println("Database: Erro ao inicializar RabbitMQ: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("Database interrompido.");
            Thread.currentThread().interrupt();
        } finally {
            closeConnections();
        }
    }

    private void initializeRabbitMQConsumer() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_HOST);

        connectionRabbitMQ = factory.newConnection();
        channelRabbitMQ = connectionRabbitMQ.createChannel();

        channelRabbitMQ.exchangeDeclare(EXCHANGE_NAME, "topic");

        queueName = "database_queue"; 
        channelRabbitMQ.queueDeclare(queueName, true, false, false, null);

        
        channelRabbitMQ.queueBind(queueName, EXCHANGE_NAME, "#"); 
        System.out.println("Database: Fila '" + queueName + "' ligada ao exchange '" + EXCHANGE_NAME + "' com chave '#'.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println("[Database] Recebeu de '" + routingKey + "': '" + message + "'");

            processAndAggregateHistoricalMessage(routingKey, message); 
            logToFile(routingKey + " -> " + message); 

            channelRabbitMQ.basicAck(delivery.getEnvelope().getDeliveryTag(), false); 
        };

        channelRabbitMQ.basicConsume(queueName, false, deliverCallback, consumerTag -> { });
    }

    private void processAndAggregateHistoricalMessage(String routingKey, String message) {
        String[] keyParts = routingKey.split("\\.");
        String regiao = keyParts.length > 0 ? keyParts[0] : "desconhecido";

        totalDadosColetadosHistorico++;
        contagemPorRegiaoHistorico.merge(regiao, 1L, Long::sum);

        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            try {
                double temperatura = Double.parseDouble(matcher.group(1).trim());
                double umidade = Double.parseDouble(matcher.group(2).trim());
                double pressao = Double.parseDouble(matcher.group(3).trim());
                double radiacao = Double.parseDouble(matcher.group(4).trim());

                totalPorElementoHistorico.merge("temperatura", 1L, Long::sum);
                totalPorElementoHistorico.merge("umidade", 1L, Long::sum);
                totalPorElementoHistorico.merge("pressao", 1L, Long::sum);
                totalPorElementoHistorico.merge("radiacao", 1L, Long::sum);

                ultimosValoresPorRegiaoEElementoHistorico
                    .computeIfAbsent(regiao, k -> new HashMap<>())
                    .put("temperatura", temperatura);
                ultimosValoresPorRegiaoEElementoHistorico
                    .computeIfAbsent(regiao, k -> new HashMap<>())
                    .put("umidade", umidade);
                ultimosValoresPorRegiaoEElementoHistorico
                    .computeIfAbsent(regiao, k -> new HashMap<>())
                    .put("pressao", pressao);
                ultimosValoresPorRegiaoEElementoHistorico
                    .computeIfAbsent(regiao, k -> new HashMap<>())
                    .put("radiacao", radiacao);

            } catch (NumberFormatException e) {
                System.err.println("[Database] Erro ao parsear valores numéricos da mensagem: " + message);
            }
        } else {
            System.err.println("[Database] Mensagem não corresponde ao padrão padronizado: " + message);
        }
    }

    private void logToFile(String message) {
        try (FileWriter fw = new FileWriter("database_log.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Database: Erro ao escrever no arquivo de log: " + e.getMessage());
        }
    }

    private void closeConnections() {
        try {
            if (channelRabbitMQ != null && channelRabbitMQ.isOpen()) {
                channelRabbitMQ.close();
            }
            if (connectionRabbitMQ != null && connectionRabbitMQ.isOpen()) {
                connectionRabbitMQ.close();
            }
        } catch (IOException | TimeoutException e) {
            System.err.println("Database: Erro ao fechar conexão RabbitMQ: " + e.getMessage());
        }
    }

    public long getTotalDadosColetadosHistorico() { return totalDadosColetadosHistorico; }
    public Map<String, Long> getTotalPorElementoHistorico() { return totalPorElementoHistorico; }
    public Map<String, Map<String, Double>> getUltimosValoresPorRegiaoEElementoHistorico() { return ultimosValoresPorRegiaoEElementoHistorico; }
    public Map<String, Long> getContagemPorRegiaoHistorico() { return contagemPorRegiaoHistorico; }
}