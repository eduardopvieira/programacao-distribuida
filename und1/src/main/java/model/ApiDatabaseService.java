// Salve este arquivo como src/model/ApiDatabaseService.java

package model;

import com.rabbitmq.client.*;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApiDatabaseService implements Runnable {

    // NOVO: Armazenamento principal para o histórico de todas as mensagens
    private final List<Map<String, Object>> historicoDeMensagens = new ArrayList<>();

    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");

    @Override
    public void run() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(this::initializeRabbitMQConsumer);

        try {
            startHttpServer();
        } catch (IOException e) {
            System.err.println("API Service: Falha ao iniciar o servidor HTTP: " + e.getMessage());
        }

        System.out.println("API Service: Servidor HTTP e consumidor RabbitMQ iniciados.");
    }

    // --- SERVIDOR HTTP MODIFICADO ---

    private void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // NOVO: Endpoint único para buscar mensagens
        server.createContext("/api/mensagens", (exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String regiao = params.get("regiao"); // Pode ser null se não for especificado

                List<Map<String, Object>> resultado = getMensagens(regiao);
                String response = listToJson(resultado);
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "Método não permitido");
            }
        }));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("API Service: Servidor HTTP rodando na porta 8080.");
    }

    // Helper para extrair parâmetros da URL (ex: ?regiao=norte)
    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
            } else {
                result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), "");
            }
        }
        return result;
    }

    // Helper para enviar a resposta HTTP
    private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    // --- MÉTODOS DE CONSULTA (GETTERS) E SERIALIZAÇÃO PARA JSON ---

    public synchronized List<Map<String, Object>> getMensagens(String regiao) {
        if (regiao == null || regiao.isBlank()) {
            return new ArrayList<>(historicoDeMensagens); // Retorna uma cópia de toda a lista
        }
        // Retorna uma lista filtrada pela região
        return historicoDeMensagens.stream()
                .filter(msg -> regiao.equalsIgnoreCase((String) msg.get("regiao")))
                .collect(Collectors.toList());
    }

    private String listToJson(List<Map<String, Object>> list) {
        String data = list.stream()
                .map(this::mapToJson)
                .collect(Collectors.joining(",\n    "));
        return "[\n    " + data + "\n]";
    }

    private String mapToJson(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(entry -> String.format("\"%s\": %s", entry.getKey(),
                        entry.getValue() instanceof String ? "\"" + entry.getValue() + "\"" : entry.getValue().toString()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    // --- LÓGICA DO CONSUMIDOR RABBITMQ (adaptada) ---

    private void initializeRabbitMQConsumer() {
        // ... (o código de conexão com RabbitMQ permanece o mesmo) ...
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare("dados_climaticos_historico", "topic");
            String queueName = "api_database_queue_raw"; // novo nome para evitar conflitos
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, "dados_climaticos_historico", "#");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String routingKey = delivery.getEnvelope().getRoutingKey();
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[API Service/RabbitMQ] Recebeu e armazenou: " + message);
                processAndStoreMessage(routingKey, message);
                logToFile(routingKey + " -> " + message);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };

            channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Falha ao inicializar consumidor RabbitMQ", e);
        }
    }

    private synchronized void processAndStoreMessage(String routingKey, String message) {
        String[] keyParts = routingKey.split("\\.");
        String regiao = keyParts.length > 0 ? keyParts[0] : "desconhecido";

        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            try {
                // Cria um mapa para representar o dado estruturado
                Map<String, Object> dados = new HashMap<>();
                dados.put("regiao", regiao);
                dados.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                dados.put("temperatura", Double.parseDouble(matcher.group(1).trim()));
                dados.put("umidade", Double.parseDouble(matcher.group(2).trim()));
                dados.put("pressao", Double.parseDouble(matcher.group(3).trim()));
                dados.put("radiacao", Double.parseDouble(matcher.group(4).trim()));

                // Adiciona o mapa à lista histórica
                historicoDeMensagens.add(dados);

            } catch (NumberFormatException e) {
                System.err.println("[API Service] Erro ao parsear valores: " + message);
            }
        }
    }

    private void logToFile(String message) {
        try (FileWriter fw = new FileWriter("raw_data_log.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[API Service] Erro ao escrever no log: " + e.getMessage());
        }
    }
}
