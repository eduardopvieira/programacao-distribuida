package model;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import datastructures.HashAdaptado; // Mantido para compatibilidade, mas pode ser substituído
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap; // Para manter a ordem das chaves na impressão
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Database implements Runnable {

    // (bd está comentado pois a agregação está em Maps agora)
    // private HashAdaptado bd = new HashAdaptado();

    // Atributos RabbitMQ para consumir do DataCenter (histórico)
    private Connection connectionRabbitMQ;
    private Channel channelRabbitMQ;
    private final String BROKER_HOST = "localhost";
    private final String EXCHANGE_NAME = "dados_climaticos_historico"; // Mesmo nome do DataCenter
    private String queueName; // Fila para o Database

    // --- Atributos para Agregação de Dados para Dashboard ---
    private volatile long totalDadosColetados = 0; // volatile para garantir visibilidade entre threads
    private Map<String, Long> totalPorElemento = new HashMap<>(); // Ex: "temperatura" -> count
    private Map<String, Map<String, Double>> ultimosValoresPorRegiaoEElemento = new HashMap<>(); // Último valor para dashboard
    private Map<String, Long> contagemPorRegiao = new HashMap<>(); // Total de dados por região

    // Padrão para parsear a mensagem padronizada: [temperatura | umidade | pressao | radiacao]
    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");

    @Override
    public void run() {
        try {
            initializeRabbitMQConsumer();
            System.out.println("Database: Conectado ao RabbitMQ e aguardando mensagens de histórico.");

            // Thread separada para a interface do usuário
            Thread dashboardInterfaceThread = new Thread(this::runDashboardInterface);
            dashboardInterfaceThread.setDaemon(true); // Garante que a thread não impeça o encerramento do JVM
            dashboardInterfaceThread.start();

            // Mantém a thread do Database viva para receber mensagens
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

        queueName = "database_queue"; // Um nome fixo para a fila do Database
        channelRabbitMQ.queueDeclare(queueName, true, false, false, null); // Durável = true

        channelRabbitMQ.queueBind(queueName, EXCHANGE_NAME, "#"); // Assina tudo do topic exchange
        System.out.println("Database: Fila '" + queueName + "' ligada ao exchange '" + EXCHANGE_NAME + "' com chave '#'.");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
             System.out.println("[Database] Recebeu de '" + routingKey + "': '" + message + "'"); // Descomente para ver cada mensagem

            processAndAggregateMessage(routingKey, message); // Processa e agrega
            logToFile(routingKey + " -> " + message); // Loga a mensagem recebida e a routingKey

            channelRabbitMQ.basicAck(delivery.getEnvelope().getDeliveryTag(), false); // Acknowledge manual
        };

        channelRabbitMQ.basicConsume(queueName, false, deliverCallback, consumerTag -> { });
    }

    private void processAndAggregateMessage(String routingKey, String message) {
        // Extrai a posição da routingKey (ex: "norte.dados" -> "norte")
        String[] keyParts = routingKey.split("\\.");
        String regiao = keyParts.length > 0 ? keyParts[0] : "desconhecido";

        synchronized (this) { // Sincroniza o acesso aos contadores compartilhados
            totalDadosColetados++;
            contagemPorRegiao.merge(regiao, 1L, Long::sum);
        }


        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            try {
                // Ordem esperada: [temperatura | umidade | pressao | radiacao]
                double temperatura = Double.parseDouble(matcher.group(1).trim());
                double umidade = Double.parseDouble(matcher.group(2).trim());
                double pressao = Double.parseDouble(matcher.group(3).trim());
                double radiacao = Double.parseDouble(matcher.group(4).trim());

                synchronized (this) { // Sincroniza o acesso aos contadores compartilhados
                    // Atualizar contadores por elemento
                    totalPorElemento.merge("temperatura", 1L, Long::sum);
                    totalPorElemento.merge("umidade", 1L, Long::sum);
                    totalPorElemento.merge("pressao", 1L, Long::sum);
                    totalPorElemento.merge("radiacao", 1L, Long::sum);

                    // Armazenar últimos valores por região e elemento (para listagens)
                    ultimosValoresPorRegiaoEElemento
                            .computeIfAbsent(regiao, k -> new HashMap<>())
                            .put("temperatura", temperatura);
                    ultimosValoresPorRegiaoEElemento
                            .computeIfAbsent(regiao, k -> new HashMap<>())
                            .put("umidade", umidade);
                    ultimosValoresPorRegiaoEElemento
                            .computeIfAbsent(regiao, k -> new HashMap<>())
                            .put("pressao", pressao);
                    ultimosValoresPorRegiaoEElemento
                            .computeIfAbsent(regiao, k -> new HashMap<>())
                            .put("radiacao", radiacao);
                }
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

    // --- MÉTODOS PARA DASHBOARD NO CONSOLE ---

    private void runDashboardInterface() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Dashboard do Sistema Climático ---");
        System.out.println("Pressione ENTER a qualquer momento para ver o menu e as estatísticas atualizadas.");
        System.out.println("Digite '0' ou 'sair' para encerrar a aplicação.\n");

        while (!Thread.currentThread().isInterrupted()) {
            try {

                while (!consoleReader.ready()) {
                    Thread.sleep(100);  
                }
                String input = consoleReader.readLine();
                if (input != null && (input.equalsIgnoreCase("0") || input.equalsIgnoreCase("sair"))) {
                    System.out.println("Encerrando Dashboard...");
                    System.exit(0); 
                    break;
                }
                displayDashboardData();
                System.out.println("\nPressione ENTER para atualizar ou digite '0'/'sair' para sair.");

            } catch (IOException e) {
                System.err.println("Erro de leitura do console: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("Dashboard interrompido.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void displayDashboardData() {
        System.out.println("\n--- DADOS CLIMÁTICOS ATUAIS ---");
        synchronized (this) { // Sincroniza para garantir consistência dos dados agregados
            System.out.println("Total de dados coletados: " + totalDadosColetados);

            System.out.println("\nTotal por elemento climático:");
            if (totalDadosColetados > 0) {
                totalPorElemento.forEach((elemento, count) ->
                        System.out.printf("  %s: %d dados \n", elemento, count, (double) count / totalDadosColetados * 100));
            } else {
                System.out.println("  Nenhum dado de elemento coletado ainda.");
            }

            System.out.println("\nContagem de dados por região:");
            if (contagemPorRegiao.isEmpty()) {
                System.out.println("  Nenhum dado por região coletado ainda.");
            } else {
                contagemPorRegiao.forEach((regiao, count) ->
                        System.out.printf("  %s: %d dados\n", regiao.toUpperCase(), count)
                );
            }


            System.out.println("\nÚltimos valores por região:");
            if (ultimosValoresPorRegiaoEElemento.isEmpty()) {
                System.out.println("  Nenhum valor disponível.");
            } else {
                // Listar regiões por temperatura
                System.out.println("\n  Temperaturas por Região:");
                getTemperaturasPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())) // Opcional: ordenar
                        .forEach(entry -> System.out.printf("    %s: %.2f°C\n", entry.getKey().toUpperCase(), entry.getValue()));

                // Listar regiões por umidade
                System.out.println("\n  Umidades por Região:");
                getUmidadesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f%%\n", entry.getKey().toUpperCase(), entry.getValue()));

                // Listar regiões por pressão
                System.out.println("\n  Pressões por Região:");
                getPressoesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f hPa\n", entry.getKey().toUpperCase(), entry.getValue()));

                // Listar regiões por radiação
                System.out.println("\n  Radiação por Região:");
                getRadiacoesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f W/m²\n", entry.getKey().toUpperCase(), entry.getValue()));
            }
        }
        System.out.println("-----------------------------\n");
    }

    // --- Métodos para obter dados para Dashboard (permanecem os mesmos, mas agora chamados internamente) ---
    public long getTotalDadosColetados() {
        synchronized (this) { return totalDadosColetados; }
    }

    public Map<String, Long> getTotalPorElemento() {
        synchronized (this) { return new HashMap<>(totalPorElemento); }
    }

    public Map<String, Double> getPercentualPorElemento() {
        synchronized (this) {
            Map<String, Double> percentuais = new LinkedHashMap<>(); // LinkedHashMap para manter ordem
            if (totalDadosColetados == 0) return percentuais;

            // Ordena os elementos para uma exibição consistente
            totalPorElemento.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry ->
                            percentuais.put(entry.getKey(), (double) entry.getValue() / totalDadosColetados * 100)
                    );
            return percentuais;
        }
    }

    public Map<String, Map<String, Double>> getUltimosValoresPorRegiaoEElemento() {
        synchronized (this) {
            // Retorna uma cópia defensiva
            return ultimosValoresPorRegiaoEElemento.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new HashMap<>(e.getValue())));
        }
    }

    public Map<String, Long> getContagemPorRegiao() {
        synchronized (this) { return new HashMap<>(contagemPorRegiao); }
    }

    public Map<String, Double> getTemperaturasPorRegiao() {
        return getValuesByElement("temperatura");
    }

    public Map<String, Double> getUmidadesPorRegiao() {
        return getValuesByElement("umidade");
    }

    public Map<String, Double> getPressoesPorRegiao() {
        return getValuesByElement("pressao");
    }

    public Map<String, Double> getRadiacoesPorRegiao() {
        return getValuesByElement("radiacao");
    }

    private Map<String, Double> getValuesByElement(String elementName) {
        synchronized (this) {
            Map<String, Double> values = new LinkedHashMap<>();
            // Ordena as regiões para uma exibição consistente
            ultimosValoresPorRegiaoEElemento.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry ->
                            Optional.ofNullable(entry.getValue().get(elementName))
                                    .ifPresent(val -> values.put(entry.getKey(), val))
                    );
            return values;
        }
    }
}