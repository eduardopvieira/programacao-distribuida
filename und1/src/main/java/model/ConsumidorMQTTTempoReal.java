package model;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConsumidorMQTTTempoReal implements Runnable {

    private final String BROKER_HOST = "tcp://broker.hivemq.com:1883";
    private final String TOPICO_BASE = "dados_climaticos_tempo_real/";
    private MqttClient mqttClient;
    private final int MAX_DASHBOARD_DATA_POINTS = 100;
    private LinkedList<Map<String, String>> recentData = new LinkedList<>();
    private volatile long totalDadosColetadosDashboard = 0;
    private Map<String, Long> totalPorElementoDashboard = new HashMap<>();
    private Map<String, Map<String, Double>> ultimosValoresPorRegiaoEElementoDashboard = new HashMap<>();
    private Map<String, Long> contagemPorRegiaoDashboard = new HashMap<>();
    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");


    public ConsumidorMQTTTempoReal() {
        try {
            initializeMqttClient();
        } catch (MqttException e) {
            System.err.println("Erro ao inicializar ConsumidorMQTTTempoReal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // configura e conecta o cliente ao broker mqtt.
    private void initializeMqttClient() throws MqttException {
        String clientId = MqttClient.generateClientId() + "_ConsumidorTempoReal";
        mqttClient = new MqttClient(BROKER_HOST, clientId, new MemoryPersistence());

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setConnectionTimeout(10);
        connectOptions.setKeepAliveInterval(20);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[ConsumidorMQTTTempoReal] Conexão perdida: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println(String.format("[ConsumidorMQTTTempoReal] Recebeu do tópico '%s' (QoS %d): '%s'",
                        topic, message.getQos(), payload));

                processAndAggregateMessageForDashboard(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        mqttClient.connect(connectOptions);
        System.out.println("[ConsumidorMQTTTempoReal] Conectado ao broker MQTT: " + BROKER_HOST);
    }

    // ponto de entrada da thread do consumidor.
    @Override
    public void run() {
        if (!mqttClient.isConnected()) {
            System.err.println("[ConsumidorMQTTTempoReal] Cliente MQTT não conectado. Encerrando.");
            return;
        }
        try {
            Thread dashboardInterfaceThread = new Thread(this::runDashboardInterface);
            dashboardInterfaceThread.setDaemon(true);
            dashboardInterfaceThread.start();

            setupSubscription();
        } catch (MqttException | InterruptedException e) {
            System.err.println("[ConsumidorMQTTTempoReal] Erro ao configurar assinatura ou processar mensagens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    System.out.println("[ConsumidorMQTTTempoReal] Desconectado do broker MQTT.");
                }
            } catch (MqttException e) {
                System.err.println("[ConsumidorMQTTTempoReal] Erro ao desconectar: " + e.getMessage());
            }
        }
    }

    // define as assinaturas de tópico com base na escolha do usuário.
    private void setupSubscription() throws MqttException, InterruptedException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Consumidor MQTT Tempo Real ---");
        System.out.println("Escolha os dados que deseja receber (filtros de tópico):");
        System.out.println("  1. Todos os dados (" + TOPICO_BASE + "#)");
        System.out.println("  2. Dados da região NORTE (" + TOPICO_BASE + "norte/dados)");
        System.out.println("  3. Dados da região SUL (" + TOPICO_BASE + "sul/dados)");
        System.out.println("  4. Dados da região LESTE (" + TOPICO_BASE + "leste/dados)");
        System.out.println("  5. Dados da região OESTE (" + TOPICO_BASE + "oeste/dados)");
        System.out.println("  6. Dados de TEMPERATURA de todas as regiões (" + TOPICO_BASE + "+/dados)");
        System.out.println("  7. Dados de UMIDADE de todas as regiões (" + TOPICO_BASE + "+/dados)");
        System.out.println("  8. Dados de PRESSAO de todas as regiões (" + TOPICO_BASE + "+/dados)");
        System.out.println("  9. Dados de RADIACAO de todas as regiões (" + TOPICO_BASE + "+/dados)");
        System.out.println("  (Você pode combinar filtros separando por vírgula, ex: 2,4 para Norte e Leste)");
        System.out.print("Sua escolha: ");

        String choice = null;
        try {
            choice = consoleReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> topicFilters = parseChoices(choice);

        if (!topicFilters.isEmpty()) {
            String[] topicsArray = topicFilters.toArray(new String[0]);
            int[] qosArray = new int[topicsArray.length];
            Arrays.fill(qosArray, 1);

            mqttClient.subscribe(topicsArray, qosArray);
            System.out.println("[ConsumidorMQTTTempoReal] Assinado nos tópicos: " + topicFilters);
        } else {
            System.out.println("[ConsumidorMQTTTempoReal] Nenhuma assinatura válida selecionada.");
        }

        System.out.println("[ConsumidorMQTTTempoReal] [*] Esperando mensagens. Para ver o dashboard, pressione ENTER.");
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(1000);
        }
    }

    // traduz as escolhas numéricas do usuário em filtros de tópico.
    private List<String> parseChoices(String choice) {
        String[] rawChoices = choice.split(",");
        return Arrays.stream(rawChoices)
                .map(String::trim)
                .map(s -> {
                    switch (s) {
                        case "1": return TOPICO_BASE + "#";
                        case "2": return TOPICO_BASE + "norte/dados";
                        case "3": return TOPICO_BASE + "sul/dados";
                        case "4": return TOPICO_BASE + "leste/dados";
                        case "5": return TOPICO_BASE + "oeste/dados";
                        case "6": return TOPICO_BASE + "+/dados";
                        case "7": return TOPICO_BASE + "+/dados";
                        case "8": return TOPICO_BASE + "+/dados";
                        case "9": return TOPICO_BASE + "+/dados";
                        default: return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    // processa uma mensagem recebida para o dashboard.
    private void processAndAggregateMessageForDashboard(String topic, String message) {
        String[] topicParts = topic.split("/");
        String regiao = topicParts.length > 1 ? topicParts[1] : "desconhecido";

        Map<String, String> parsedMessage = new HashMap<>();
        parsedMessage.put("regiao", regiao);

        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            parsedMessage.put("temperatura", matcher.group(1).trim());
            parsedMessage.put("umidade", matcher.group(2).trim());
            parsedMessage.put("pressao", matcher.group(3).trim());
            parsedMessage.put("radiacao", matcher.group(4).trim());
        } else {
            System.err.println("[ConsumidorMQTTTempoReal-Dashboard] Mensagem não corresponde ao padrão: " + message);
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

                ultimosValoresPorRegiaoEElementoDashboard
                        .computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("temperatura", temperatura);
                ultimosValoresPorRegiaoEElementoDashboard
                        .computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("umidade", umidade);
                ultimosValoresPorRegiaoEElementoDashboard
                        .computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("pressao", pressao);
                ultimosValoresPorRegiaoEElementoDashboard
                        .computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("radiacao", radiacao);

            } catch (NumberFormatException e) {
                System.err.println("[ConsumidorMQTTTempoReal-Dashboard] Erro ao parsear valores numéricos de dados recentes: " + data);
            }
        }
    }

    // gerencia a interface de texto do dashboard em uma thread separada.
    private void runDashboardInterface() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Dashboard do Consumidor MQTT Tempo Real ---");
        System.out.println("Pressione ENTER a qualquer momento para ver o menu e as estatísticas atualizadas.");
        System.out.println("Digite '0' para encerrar a aplicação.\n");

        while (!Thread.currentThread().isInterrupted()) {
            try {

                while (!consoleReader.ready()) {
                    Thread.sleep(500);
                }
                String input = consoleReader.readLine();
                if (input != null && (input.equalsIgnoreCase("0") )) {
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
            System.out.println("Total de dados coletados: " + totalDadosColetadosDashboard);

            System.out.println("\nTotal por elemento climático (recentes):");
            if (totalDadosColetadosDashboard > 0) {
                totalPorElementoDashboard.forEach((elemento, count) ->
                        System.out.printf("  %s: %d dados\n", elemento, count, (double) count / totalDadosColetadosDashboard * 100)
                );
            } else {
                System.out.println("  Nenhum dado de elemento coletado recentemente.");
            }

            System.out.println("\nContagem de dados por região (recentes):");
            if (contagemPorRegiaoDashboard.isEmpty()) {
                System.out.println("  Nenhum dado por região coletado recentemente.");
            } else {
                contagemPorRegiaoDashboard.forEach((regiao, count) ->
                        System.out.printf("  %s: %d dados\n", regiao.toUpperCase(), count)
                );
            }

            System.out.println("\nValores recentes por região:");
            if (ultimosValoresPorRegiaoEElementoDashboard.isEmpty()) {
                System.out.println("  Nenhum valor recente disponível.");
            } else {
                System.out.println("\n  Temperaturas por Região:");
                getTemperaturasPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f°C\n", entry.getKey().toUpperCase(), entry.getValue()));

                System.out.println("\n  Umidades por Região:");
                getUmidadesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f%%\n", entry.getKey().toUpperCase(), entry.getValue()));

                System.out.println("\n  Pressões por Região:");
                getPressoesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f hPa\n", entry.getKey().toUpperCase(), entry.getValue()));

                System.out.println("\n  Radiação por Região:");
                getRadiacoesPorRegiao().entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> System.out.printf("    %s: %.2f W/m²\n", entry.getKey().toUpperCase(), entry.getValue()));
            }
        }
        System.out.println("-----------------------------\n");
    }

    public long getTotalDadosColetados() {
        synchronized (this) { return totalDadosColetadosDashboard; }
    }

    public Map<String, Long> getTotalPorElemento() {
        synchronized (this) { return new HashMap<>(totalPorElementoDashboard); }
    }

    public Map<String, Double> getPercentualPorElemento() {
        synchronized (this) {
            Map<String, Double> percentuais = new LinkedHashMap<>();
            if (totalDadosColetadosDashboard == 0) return percentuais;

            totalPorElementoDashboard.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry ->
                            percentuais.put(entry.getKey(), (double) entry.getValue() / totalDadosColetadosDashboard * 100)
                    );
            return percentuais;
        }
    }

    public Map<String, Map<String, Double>> getUltimosValoresPorRegiaoEElemento() {
        synchronized (this) {
            return ultimosValoresPorRegiaoEElementoDashboard.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new HashMap<>(e.getValue())));
        }
    }

    public Map<String, Long> getContagemPorRegiao() {
        synchronized (this) { return new HashMap<>(contagemPorRegiaoDashboard); }
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

    // extrai valores de um elemento específico para todas as regiões.
    private Map<String, Double> getValuesByElement(String elementName) {
        synchronized (this) {
            Map<String, Double> values = new LinkedHashMap<>();
            ultimosValoresPorRegiaoEElementoDashboard.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry ->
                            Optional.ofNullable(entry.getValue().get(elementName))
                                    .ifPresent(val -> values.put(entry.getKey(), val))
                    );
            return values;
        }
    }
}
