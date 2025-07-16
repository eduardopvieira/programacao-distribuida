package executables;

import model.DataCenter;

public class DataCenterExecutable {
    public static void main(String[] args) {
        Thread datacenter = new Thread(new DataCenter());
        datacenter.start();
    }
}
