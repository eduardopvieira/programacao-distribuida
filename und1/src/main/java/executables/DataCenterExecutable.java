package executables;

//import model.DataCenter;
import model.DataCenter2;

public class DataCenterExecutable {
    public static void main(String[] args) {
        //Thread datacenter = new Thread(new DataCenter());
        Thread datacenter = new Thread(new DataCenter2());
        datacenter.start();
    }
}
