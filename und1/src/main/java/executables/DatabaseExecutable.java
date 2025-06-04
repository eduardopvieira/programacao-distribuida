package executables;

import model.Database;

public class DatabaseExecutable {
    public static void main(String[] args) {
        Thread database = new Thread(new Database());
        database.start();
    }
}
