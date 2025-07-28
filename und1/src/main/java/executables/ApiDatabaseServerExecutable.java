// Salve este arquivo como src/executables/ExecutaApiDatabase.java
package executables;

import model.ApiDatabaseService;

public class ApiDatabaseServerExecutable {
    public static void main(String[] args) {
        System.out.println("[EXECUTÁVEL] Iniciando o Serviço de Banco de Dados com API HTTP...");
        ApiDatabaseService service = new ApiDatabaseService();
        service.run();
    }
}
