// Salve este arquivo como src/executables/ClienteHttp.java

package executables;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class ClienteHTTP {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:8080/api/mensagens";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Cliente HTTP - Consulta ao Histórico de Mensagens ---");
            System.out.println("1. Buscar TODAS as mensagens");
            System.out.println("2. Buscar mensagens da Região NORTE");
            System.out.println("3. Buscar mensagens da Região SUL");
            System.out.println("4. Buscar mensagens da Região LESTE");
            System.out.println("5. Buscar mensagens da Região OESTE");
            System.out.println("0. Sair");
            System.out.print("Sua escolha: ");

            String choice = scanner.nextLine();
            String endpoint = "";

            switch (choice) {
                case "1": endpoint = ""; break; // Sem filtro
                case "2": endpoint = "?regiao=norte"; break;
                case "3": endpoint = "?regiao=sul"; break;
                case "4": endpoint = "?regiao=leste"; break;
                case "5": endpoint = "?regiao=oeste"; break;
                case "0": System.out.println("Saindo..."); return;
                default: System.out.println("Opção inválida."); continue;
            }

            fetchAndPrintData(endpoint);
        }
    }

    private static void fetchAndPrintData(String query) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + query))
                    .GET()
                    .build();

            System.out.println("-> Fazendo requisição para: " + request.uri());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("\n--- Resposta da API (Status: " + response.statusCode() + ") ---");
            System.out.println(response.body());
            System.out.println("-------------------------------------\n");

        } catch (IOException | InterruptedException e) {
            System.err.println("Erro ao conectar à API: " + e.getMessage());
            System.err.println("Verifique se o 'ApiDatabaseService' está em execução.");
        }
    }
}
