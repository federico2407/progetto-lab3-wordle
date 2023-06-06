package wordleserver;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SecretGenerator extends Thread{
    
    
    
    public SecretGenerator() throws IOException{
        long currentTime = new Date().getTime();
        long secretTime = currentTime - currentTime % ServerOps.getDayLength();
        ServerOps.newSecret(secretTime);
    }
    
    @Override
    public void run(){
        long currentTime;
        long secretTime;
        long timeToNextSecret;
        while(true){
            try {
                currentTime = new Date().getTime(); //prendo il tempo corrente
                secretTime = currentTime + ServerOps.getDayLength() - currentTime % ServerOps.getDayLength(); //arrotondo alla lunghezza di un giorno
                timeToNextSecret = secretTime - currentTime; //calcolo il tempo che manca al prossimo giorno
                Thread.sleep(timeToNextSecret);
                ServerOps.newSecret(secretTime); //genero un nuovo segreto
            } catch (InterruptedException | IOException ex) {
                Logger.getLogger(SecretGenerator.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
        }
    }
}
