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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConsumidorRabbitMQ implements Runnable {

    private final String BROKER_HOST = "localhost";
    private final String EXCHANGE_NAME = "dados_climaticos_historico";

    private Connection connection;
    private Channel channel;
    private String queueName;
    private List<String> bindingKeys;

    private final int MAX_DASHBOARD_DATA_POINTS = 100;
    private LinkedList<Map<String, String>> recentData = new LinkedList<>();
    private volatile long totalDadosColetadosDashboard = 0;
    private Map<String, Long> totalPorElementoDashboard = new HashMap<>();
    private Map<String, Map<String, Double>> ultimosValoresPorRegiaoEElementoDashboard = new HashMap<>();
    private Map<String, Long> contagemPorRegiaoDashboard = new HashMap<>();
    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");


    public ConsumidorRabbitMQ() {
        try {
            initializeRabbitMQ();
        } catch (Exception e) {
            System.err.println("Erro ao inicializar ConsumidorRabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // configura a conexão e o canal com o broker RabbitMQ.
    private void initializeRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_HOST);

        connection = factory.newConnection();
        channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        String nomeFila = "fila_dados_historicos";
        boolean durable = true;
        boolean exclusive = false;
        boolean autoDelete = false;

        channel.queueDeclare(nomeFila, durable, exclusive, autoDelete, null);
        this.queueName = nomeFila;

        System.out.println("[ConsumidorRabbitMQ] Conectado e usando a fila durável: '" + queueName + "'");
        System.out.println("[ConsumidorRabbitMQ] Aguardando definição de filtros de tópico...");
    }

    // ponto de entrada da thread do consumidor.
    @Override
    public void run() {
        if (channel == null) {
            System.err.println("[ConsumidorRabbitMQ] Canal não inicializado. Encerrando.");
            return;
        }
        try {
            Thread dashboardInterfaceThread = new Thread(this::runDashboardInterface);
            dashboardInterfaceThread.setDaemon(true);
            dashboardInterfaceThread.start();
            setupSubscription();
        } catch (Exception e) {
            System.err.println("[ConsumidorRabbitMQ] Erro ao configurar assinatura ou processar mensagens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    // configura os filtros de tópico e inicia o consumo de mensagens.
    private void setupSubscription() throws Exception {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Consumidor RabbitMQ ---");
        System.out.println("Escolha os dados que deseja receber (filtros de tópico):");
        System.out.println("  1. Todos os dados (#)");
        System.out.println("  2. Dados da região NORTE (norte.*)");
        System.out.println("  3. Dados da região SUL (sul.*)");
        System.out.println("  4. Dados da região LESTE (leste.*)");
        System.out.println("  5. Dados da região OESTE (oeste.*)");
        System.out.println("(Você pode combinar filtros separando por vírgula, ex: 2,4 para Norte e Leste)");
        System.out.print("Sua escolha: ");

        String choice = consoleReader.readLine();
        this.bindingKeys = parseChoices(choice);

        System.out.println("[ConsumidorRabbitMQ] Aplicando os filtros: " + this.bindingKeys);
        for (String bindingKey : this.bindingKeys) {
            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
            System.out.println("[ConsumidorRabbitMQ] Fila '" + queueName + "' ligada ao exchange '" + EXCHANGE_NAME + "' com chave '" + bindingKey + "'");
        }

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();

            boolean filtroParaTodos = this.bindingKeys.contains("#");
            if (!filtroParaTodos) {
                boolean mensagemCorresponde = this.bindingKeys.stream()
                        .anyMatch(key -> routingKeyMatches(routingKey, key));

                if (!mensagemCorresponde) {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    return;
                }
            }
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.printf("[ConsumidorRabbitMQ] Recebeu de '%s': '%s'%n", routingKey, message);

            processAndAggregateMessageForDashboard(routingKey, message);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});

        System.out.println("[ConsumidorRabbitMQ] [*] Esperando mensagens. Para ver o dashboard, pressione ENTER.");
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(1000);
        }
    }

    // verifica se a chave de roteamento corresponde a um filtro de tópico.
    private boolean routingKeyMatches(String routingKey, String bindingKey) {
        if (bindingKey.equals(routingKey)) {
            return true;
        }
        String regex = bindingKey
                .replace(".", "\\.")
                .replace("*", "[^\\.]+")
                .replace("#", ".*");
        return routingKey.matches(regex);
    }

    // encerra a conexão e o canal de forma segura.
    private void closeConnection() {
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

    // converte a escolha do usuário em chaves de roteamento.
    private List<String> parseChoices(String choice) {
        String[] rawChoices = choice.split(",");
        return Arrays.stream(rawChoices)
                .map(String::trim)
                .map(s -> {
                    switch (s) {
                        case "1": return "#";
                        case "2": return "norte.*";
                        case "3": return "sul.*";
                        case "4": return "leste.*";
                        case "5": return "oeste.*";
                        default: return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    // processa e agrega a mensagem recebida para o dashboard.
    private void processAndAggregateMessageForDashboard(String routingKey, String message) {
        String[] keyParts = routingKey.split("\\.");
        String regiao = keyParts.length > 0 ? keyParts[0] : "desconhecido";
        Map<String, String> parsedMessage = new HashMap<>();
        parsedMessage.put("regiao", regiao);
        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            parsedMessage.put("temperatura", matcher.group(1).trim());
            parsedMessage.put("umidade", matcher.group(2).trim());
            parsedMessage.put("pressao", matcher.group(3).trim());
            parsedMessage.put("radiacao", matcher.group(4).trim());
        } else {
            System.err.println("[ConsumidorRabbitMQ-Dashboard] Mensagem não corresponde ao padrão: " + message);
            return;
        }
        synchronized (this) {
            recentData.addLast(parsedMessage);
            if (recentData.size() > MAX_DASHBOARD_DATA_POINTS) {
                recentData.removeFirst();
            }
            recalculateDashboardStatistics();
        }
    }

    // recalcula todas as estatísticas a partir dos dados recentes.
    private void recalculateDashboardStatistics() {
        totalDadosColetadosDashboard = 0;
        totalPorElementoDashboard.clear();
        ultimosValoresPorRegiaoEElementoDashboard.clear();
        contagemPorRegiaoDashboard.clear();
        for (Map<String, String> data : recentData) {
            totalDadosColetadosDashboard++;
            String regiao = data.get("regiao");
            contagemPorRegiaoDashboard.merge(regiao, 1L, Long::sum);
            try {
                double temperatura = Double.parseDouble(data.get("temperatura"));
                double umidade = Double.parseDouble(data.get("umidade"));
                double pressao = Double.parseDouble(data.get("pressao"));
                double radiacao = Double.parseDouble(data.get("radiacao"));
                totalPorElementoDashboard.merge("temperatura", 1L, Long::sum);
                totalPorElementoDashboard.merge("umidade", 1L, Long::sum);
                totalPorElementoDashboard.merge("pressao", 1L, Long::sum);
                totalPorElementoDashboard.merge("radiacao", 1L, Long::sum);
                ultimosValoresPorRegiaoEElementoDashboard.computeIfAbsent(regiao, k -> new HashMap<>()).put("temperatura", temperatura);
                ultimosValoresPorRegiaoEElementoDashboard.computeIfAbsent(regiao, k -> new HashMap<>()).put("umidade", umidade);
                ultimosValoresPorRegiaoEElementoDashboard.computeIfAbsent(regiao, k -> new HashMap<>()).put("pressao", pressao);
                ultimosValoresPorRegiaoEElementoDashboard.computeIfAbsent(regiao, k -> new HashMap<>()).put("radiacao", radiacao);
            } catch (NumberFormatException e) {
                System.err.println("[ConsumidorRabbitMQ-Dashboard] Erro ao parsear valores numéricos de dados recentes: " + data);
            }
        }
    }

    // gerencia a interface de texto do dashboard em uma thread separada.
    private void runDashboardInterface() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Dashboard do Consumidor RabbitMQ ---");
        System.out.println("Pressione ENTER a qualquer momento para ver o menu e as estatísticas atualizadas.");
        System.out.println("Digite '0' para encerrar a aplicação.\n");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                while (!consoleReader.ready()) {
                    Thread.sleep(500);
                }
                String input = consoleReader.readLine();
                if (input != null && (input.equalsIgnoreCase("0"))) {
                    System.out.println("Encerrando Dashboard...");
                    System.exit(0);
                    break;
                }
                displayDashboardData();
                System.out.println("\nPressione ENTER para atualizar ou digite '0' para sair.");
            } catch (IOException e) {
                System.err.println("Erro de leitura do console: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("Dashboard interrompido.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // exibe os dados e estatísticas atuais no console.
    private void displayDashboardData() {
        System.out.println("\n--- DADOS CLIMÁTICOS RECENTES (Últimos " + MAX_DASHBOARD_DATA_POINTS + " pontos) ---");
        synchronized (this) {
            System.out.println("Total de dados coletados (recentes): " + totalDadosColetadosDashboard);
            System.out.println("\nTotal por elemento climático (recentes):");
            if (totalDadosColetadosDashboard > 0) {
                totalPorElementoDashboard.forEach((elemento, count) -> System.out.printf("  %s: %d (%.2f%%)\n", elemento, count, (double) count / totalDadosColetadosDashboard * 100));
            } else {
                System.out.println("  Nenhum dado de elemento coletado recentemente.");
            }
            System.out.println("\nContagem de dados por região (recentes):");
            if (contagemPorRegiaoDashboard.isEmpty()) {
                System.out.println("  Nenhum dado por região coletado recentemente.");
            } else {
                contagemPorRegiaoDashboard.forEach((regiao, count) -> System.out.printf("  %s: %d dados\n", regiao.toUpperCase(), count));
            }
            System.out.println("\nÚltimos valores por região (recentes):");
            if (ultimosValoresPorRegiaoEElementoDashboard.isEmpty()) {
                System.out.println("  Nenhum valor recente disponível.");
            } else {
                System.out.println("\n  Temperaturas por Região:");
                getTemperaturasPorRegiao().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(entry -> System.out.printf("    %s: %.2f°C\n", entry.getKey().toUpperCase(), entry.getValue()));
                System.out.println("\n  Umidades por Região:");
                getUmidadesPorRegiao().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(entry -> System.out.printf("    %s: %.2f%%\n", entry.getKey().toUpperCase(), entry.getValue()));
                System.out.println("\n  Pressões por Região:");
                getPressoesPorRegiao().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(entry -> System.out.printf("    %s: %.2f hPa\n", entry.getKey().toUpperCase(), entry.getValue()));
                System.out.println("\n  Radiação por Região:");
                getRadiacoesPorRegiao().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEach(entry -> System.out.printf("    %s: %.2f W/m²\n", entry.getKey().toUpperCase(), entry.getValue()));
            }
        }
        System.out.println("-----------------------------\n");
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

    // Extrai valores de um elemento específico para todas as regiões.
    private Map<String, Double> getValuesByElement(String elementName) {
        synchronized (this) {
            Map<String, Double> values = new LinkedHashMap<>();
            ultimosValoresPorRegiaoEElementoDashboard.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> Optional.ofNullable(entry.getValue().get(elementName))
                            .ifPresent(val -> values.put(entry.getKey(), val)));
            return values;
        }
    }
}
