package executables;

import model.User;

public class UserExecutable3 {
    public static void main(String[] args) {
        User usu3 = new User("127.0.0.1", 50000);

        Thread userThread = new Thread(usu3);
        userThread.start();

    }
}
