package executables;

//import model.DataCenter;
import model.DataCenter;

public class DataCenterExecutable {
    public static void main(String[] args) {
        //Thread datacenter = new Thread(new DataCenter());
        Thread datacenter = new Thread(new DataCenter());
        datacenter.start();
    }
}
