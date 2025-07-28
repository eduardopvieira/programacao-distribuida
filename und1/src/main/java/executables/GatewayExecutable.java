package executables;

import model.Gateway;

public class GatewayExecutable {
    public static void main(String[] args) {
        Thread gateway = new Thread(new Gateway());
        gateway.start();
    }
}
