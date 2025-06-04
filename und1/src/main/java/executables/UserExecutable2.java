package executables;

import model.User;

public class UserExecutable2 {
    public static void main(String[] args) {
        User usu2 = new User("127.0.0.1", 50000);

        Thread userThread = new Thread(usu2);
        userThread.start();

    }
}
