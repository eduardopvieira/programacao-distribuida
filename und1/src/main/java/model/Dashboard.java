package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dashboard {

    private final int MAX_DATA_POINTS = 100;
    private final LinkedList<Map<String, String>> recentData = new LinkedList<>();
    private volatile long totalDadosColetados = 0;
    private final Map<String, Long> totalPorElemento = new HashMap<>();
    private final Map<String, Map<String, Double>> ultimosValoresPorRegiaoEElemento = new HashMap<>();
    private final Map<String, Long> contagemPorRegiao = new HashMap<>();
    private static final Pattern PADRONIZADO_PATTERN = Pattern.compile("\\[(.*?) \\| (.*?) \\| (.*?) \\| (.*?)\\]");

    public synchronized void processAndAggregateMessage(String routingKey, String message) {
        String[] keyParts = routingKey.split("[./]");
        String regiao = keyParts.length > 1 ? keyParts[keyParts.length - 2] : "desconhecido";

        Map<String, String> parsedMessage = new HashMap<>();
        parsedMessage.put("regiao", regiao);

        Matcher matcher = PADRONIZADO_PATTERN.matcher(message);
        if (matcher.matches()) {
            parsedMessage.put("temperatura", matcher.group(1).trim());
            parsedMessage.put("umidade", matcher.group(2).trim());
            parsedMessage.put("pressao", matcher.group(3).trim());
            parsedMessage.put("radiacao", matcher.group(4).trim());
        } else {
            System.err.println("[Dashboard] Mensagem não corresponde ao padrão: " + message);
            return;
        }

        recentData.addLast(parsedMessage);
        if (recentData.size() > MAX_DATA_POINTS) {
            recentData.removeFirst();
        }
        recalculateStatistics();
    }

    private synchronized void recalculateStatistics() {
        totalDadosColetados = recentData.size();
        totalPorElemento.clear();
        ultimosValoresPorRegiaoEElemento.clear();
        contagemPorRegiao.clear();

        for (Map<String, String> data : recentData) {
            String regiao = data.get("regiao");
            contagemPorRegiao.merge(regiao, 1L, Long::sum);

            try {
                ultimosValoresPorRegiaoEElemento.computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("temperatura", Double.parseDouble(data.get("temperatura")));
                ultimosValoresPorRegiaoEElemento.computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("umidade", Double.parseDouble(data.get("umidade")));
                ultimosValoresPorRegiaoEElemento.computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("pressao", Double.parseDouble(data.get("pressao")));
                ultimosValoresPorRegiaoEElemento.computeIfAbsent(regiao, k -> new HashMap<>())
                        .put("radiacao", Double.parseDouble(data.get("radiacao")));

                totalPorElemento.merge("temperatura", 1L, Long::sum);
                totalPorElemento.merge("umidade", 1L, Long::sum);
                totalPorElemento.merge("pressao", 1L, Long::sum);
                totalPorElemento.merge("radiacao", 1L, Long::sum);
            } catch (NumberFormatException e) {
                System.err.println("[Dashboard] Erro ao parsear valores: " + data);
            }
        }
    }

    public void runDashboardInterface() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n--- Dashboard de Dados Históricos (RabbitMQ) ---");
        System.out.println("Pressione ENTER para ver as estatísticas atualizadas.");
        System.out.println("Digite 'sair' para encerrar a visualização.\n");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (consoleReader.ready() && "sair".equalsIgnoreCase(consoleReader.readLine())) {
                    break;
                }
                displayDashboardData();
                Thread.sleep(5000);
            } catch (IOException e) {
                System.err.println("Erro de leitura do console: " + e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private synchronized void displayDashboardData() {
        System.out.print("\033[H\033[2J");
        System.out.flush();

        System.out.println("\n--- DADOS CLIMÁTICOS (Últimos " + MAX_DATA_POINTS + " pontos) ---");
        System.out.println("Total de dados coletados: " + totalDadosColetados);

        System.out.println("\nDistribuição Percentual por Elemento:");
        if (totalDadosColetados > 0) {
            long totalElementos = totalPorElemento.values().stream().mapToLong(Long::longValue).sum();
            totalPorElemento.forEach((elemento, count) ->
                    System.out.printf("  - %-12s: %d (%.2f%%)\n", elemento, count, (double) count / totalElementos * 100)
            );
        } else {
            System.out.println("  Nenhum dado coletado.");
        }

        System.out.println("\n--- RANKING DE REGIÕES (valores mais recentes) ---");
        System.out.println("\n  Temperaturas (do maior para o menor):");
        getValuesByElement("temperatura").entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> System.out.printf("    - %-7s: %.2f°C\n", e.getKey().toUpperCase(), e.getValue()));

        System.out.println("\n  Umidade (do maior para o menor):");
        getValuesByElement("umidade").entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> System.out.printf("    - %-7s: %.2f%%\n", e.getKey().toUpperCase(), e.getValue()));

        System.out.println("------------------------------------------------------\n");
    }

    private Map<String, Double> getValuesByElement(String elementName) {
        Map<String, Double> values = new LinkedHashMap<>();
        ultimosValoresPorRegiaoEElemento.forEach((regiao, data) ->
                Optional.ofNullable(data.get(elementName)).ifPresent(val -> values.put(regiao, val))
        );
        return values;
    }
}
