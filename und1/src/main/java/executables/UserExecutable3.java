package executables;

import model.User;

public class UserExecutable3 {
    public static void main(String[] args) {
        User usu1 = new User();

        Thread userThread = new Thread(usu1);
        userThread.start();
    }
}
