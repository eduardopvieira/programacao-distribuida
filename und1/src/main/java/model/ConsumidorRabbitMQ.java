package model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class ConsumidorRabbitMQ implements Runnable {

    private final String EXCHANGE_NAME = "dados_climaticos_historico";
    private Connection connection;
    private Channel channel;
    private final Dashboard dashboard;

    public ConsumidorRabbitMQ() {
        this.dashboard = new Dashboard();
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        } catch (Exception e) {
            System.err.println("Erro ao inicializar ConsumidorRabbitMQ: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            setupSubscription();
            dashboard.runDashboardInterface();
        } catch (Exception e) {
            System.err.println("[ConsumidorRabbitMQ] Erro: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            closeConnection();
        }
    }

    private void setupSubscription() throws Exception {
        String queueName = channel.queueDeclare("fila_dashboard_historico", true, false, false, null).getQueue();

        System.out.println("\n--- Consumidor de Histórico (RabbitMQ) ---");
        List<String> bindingKeys = askForTopicFilters();

        for (String bindingKey : bindingKeys) {
            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
            System.out.println("[ConsumidorRabbitMQ] Assinado no filtro: " + bindingKey);
        }

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String routingKey = delivery.getEnvelope().getRoutingKey();
            dashboard.processAndAggregateMessage(routingKey, message);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    private List<String> askForTopicFilters() throws Exception {
        System.out.println("Escolha os dados para o dashboard (separados por vírgula):");
        System.out.println("  1. Todos (#) | 2. Norte (norte.*) | 3. Sul (sul.*) | 4. Leste (leste.*) | 5. Oeste (oeste.*)");
        System.out.print("Sua escolha: ");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String[] choices = reader.readLine().split(",");

        return Arrays.stream(choices)
                .map(String::trim)
                .map(s -> switch (s) {
                    case "1" -> "#";
                    case "2" -> "norte.*";
                    case "3" -> "sul.*";
                    case "4" -> "leste.*";
                    case "5" -> "oeste.*";
                    default -> "";
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private void closeConnection() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            System.err.println("[ConsumidorRabbitMQ] Erro ao fechar conexão: " + e.getMessage());
        }
    }
}
