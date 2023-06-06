package wordleserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import static java.util.concurrent.Executors.newCachedThreadPool;


public class ServerMain {

    public static void main(String[] args) throws IOException{
        
        ExecutorService threadPool = newCachedThreadPool(); //threadpool delle connessioni
        ServerOps.loadProperties(); //carico il file server.properties
        ServerOps.loadUsers(); //carico il file users.json
        ServerSocket socket = ServerOps.generateServerSocket();
        ServerOps.generateNotificationsSocket(); //creo il socket che manda le notifiche multicast
        Runtime.getRuntime().addShutdownHook(new ShutdownListener()); //creo il listener per il SIGINT
        
        Thread secretGenerator = new SecretGenerator(); //creo e avvio il generatore di secret words
        secretGenerator.start();
        
        System.out.println("Wordle server started...");
        try {
            while(true)
                threadPool.execute(new ConnectedUser(socket.accept()));
        } catch (IOException ex) { }
    }
}