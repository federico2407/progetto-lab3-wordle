package wordleserver;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ShutdownListener extends Thread{
    @Override
    public void run(){
        try {
            ServerOps.saveUsers();
            ServerOps.closeSockets();
        } catch (IOException ex) {
            Logger.getLogger(ShutdownListener.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Server stopped");
    }
}
