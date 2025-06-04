package executables;

import model.User;

public class UserExecutable {
    public static void main(String[] args) {
        User usu1 = new User("127.0.0.1", 50000);

        Thread userThread = new Thread(usu1);
        userThread.start();

    }
}
