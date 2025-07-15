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

public class ConsumidorRabbitMQ implements Runnable {

    private final String BROKER_HOST = "localhost";
    private final String EXCHANGE_NAME = "dados_climaticos_historico"; // Mesmo nome do DataCenter

    private Connection connection;
    private Channel channel;
    private String queueName;

    public ConsumidorRabbitMQ() {
        try {
            initializeRabbitMQ();
        } catch (Exception e) {
            System.err.println("Erro ao inicializar ConsumidorRabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_HOST);

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Declara o exchange como 'topic' (precisa ser o mesmo do produtor)
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        // Cria uma fila temporária, exclusiva e auto-deletável
        // Esta fila não é durável, então as mensagens não persistem se o consumidor for reiniciado
        queueName = channel.queueDeclare().getQueue();

        System.out.println("[ConsumidorRabbitMQ] Conectado e aguardando definição de filtros.");
        System.out.println("[ConsumidorRabbitMQ] Nome da fila temporária: " + queueName);
    }

    @Override
    public void run() {
        if (channel == null) {
            System.err.println("[ConsumidorRabbitMQ] Canal não inicializado. Encerrando.");
            return;
        }

        try {
            setupSubscription(); // Configura a assinatura com base na entrada do usuário
        } catch (Exception e) {
            System.err.println("[ConsumidorRabbitMQ] Erro ao configurar assinatura ou processar mensagens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Garante que as conexões sejam fechadas quando a thread termina
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
                System.out.println("[ConsumidorRabbitMQ] Conexão encerrada.");
            } catch (IOException | TimeoutException e) {
                System.err.println("[ConsumidorRabbitMQ] Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }

    private void setupSubscription() throws Exception {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Consumidor RabbitMQ ---");
        System.out.println("Escolha os dados que deseja receber (filtros de tópico):");
        System.out.println("  1. Todos os dados (#)");
        System.out.println("  2. Dados da região NORTE (norte.*)");
        System.out.println("  3. Dados da região SUL (sul.*)");
        System.out.println("  4. Dados da região LESTE (leste.*)");
        System.out.println("  5. Dados da região OESTE (oeste.*)");
        System.out.println("  6. Dados de TEMPERATURA de todas as regiões (*.temperatura)");
        System.out.println("  7. Dados de UMIDADE de todas as regiões (*.umidade)");
        System.out.println("  8. Dados de PRESSAO de todas as regiões (*.pressao)");
        System.out.println("  9. Dados de RADIACAO de todas as regiões (*.radiacao)");
        System.out.println("  (Você pode combinar filtros separando por vírgula, ex: 2,4 para Norte e Leste)");
        System.out.print("Sua escolha: ");

        String choice = consoleReader.readLine();
        List<String> bindingKeys = parseChoices(choice);

        // Desfaz qualquer binding anterior antes de criar novos
        // (Opcional, mas útil se o mesmo consumidor for reconfigurado)
        // Por ser uma fila temporária, ao reiniciar a aplicação, um novo binding é feito
        // Para uma fila durável, seria importante gerenciar os bindings.

        System.out.println("[ConsumidorRabbitMQ] Assinando com os filtros: " + bindingKeys);
        for (String bindingKey : bindingKeys) {
            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
            System.out.println("[ConsumidorRabbitMQ] Fila '" + queueName + "' ligada ao exchange '" + EXCHANGE_NAME + "' com chave '" + bindingKey + "'");
        }

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(String.format("[ConsumidorRabbitMQ] Recebeu de '%s': '%s'",
                    delivery.getEnvelope().getRoutingKey(), message));
            // Implementar lógica de armazenamento em "base de dados específica" aqui
            // Por enquanto, apenas imprime.
        };

        // autoAck = true (reconhecimento automático)
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });

        // Mantém a thread do consumidor viva para receber mensagens
        System.out.println("[ConsumidorRabbitMQ] [*] Esperando mensagens. Para sair, feche a aplicação.");
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
                        case "1": return "#"; // Todos os dados
                        case "2": return "norte.*";
                        case "3": return "sul.*";
                        case "4": return "leste.*";
                        case "5": return "oeste.*";
                        case "6": return "*.temperatura";
                        case "7": return "*.umidade";
                        case "8": return "*.pressao";
                        case "9": return "*.radiacao";
                        default: return ""; // Filtro inválido
                    }
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList(); // Usa toList() para Java 16+, para versões anteriores use .collect(Collectors.toList())
    }
}