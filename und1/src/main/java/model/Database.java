package model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import datastructures.HashAdaptado; // Assumindo que esta classe ainda seja usada para armazenamento em memória

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * VERSÃO REFATORADA
 * Esta classe agora atua como um consumidor do RabbitMQ.
 * Ela se conecta ao exchange 'dados_climaticos_historico' para receber
 * e persistir todos os dados enviados pelo DataCenter.
 *
 * Dependência necessária: com.rabbitmq:amqp-client
 */
public class Database implements Runnable {

    private final HashAdaptado bd = new HashAdaptado();
    private final static String EXCHANGE_NAME = "dados_climaticos_historico";

    @Override
    public void run() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // Garanta que o RabbitMQ está rodando localmente

        try {
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // Garante que o exchange existe e é do tipo 'topic'
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");

            // Cria uma fila temporária, exclusiva e que será auto-deletada
            String queueName = channel.queueDeclare().getQueue();

            // Vincula a fila ao exchange. A routing key "#" significa "receber todas as mensagens"
            channel.queueBind(queueName, EXCHANGE_NAME, "#");

            System.out.println("✅ [Database] Aguardando mensagens do RabbitMQ. Para sair, pressione CTRL+C");

            // Define a ação a ser tomada quando uma mensagem chegar
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                processMessage(message);
            };

            // Inicia o consumo da fila
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});

        } catch (IOException | TimeoutException e) {
            System.err.println("X [Database] Erro ao conectar ou consumir do RabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMessage(String message) {
        // A lógica interna de processamento e log permanece a mesma
        bd.add(message);
        System.out.println("[Database] Mensagem recebida e salva: " + message);
        logToFile(message);
    }

    private void logToFile(String message) {
        try (FileWriter fw = new FileWriter("database_log.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("X [Database] Erro ao escrever no arquivo de log: " + e.getMessage());
        }
    }
}
