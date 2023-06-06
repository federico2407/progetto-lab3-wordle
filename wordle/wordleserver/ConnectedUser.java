package wordleserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConnectedUser implements Runnable{
    
    private final InputStream is;
    private final OutputStream os;
    
    
    private User user;
    private boolean isPlaying;
    private short nTrial;
    private byte[][] matches;
    
    private char[] secret;
    
    public ConnectedUser(Socket s) throws IOException{
        this.is = new BufferedInputStream(s.getInputStream());
        this.os = new BufferedOutputStream(s.getOutputStream());
        this.user = null;
        this.isPlaying = false;
        this.secret = new char[10];
    }
    
    private byte[] readText(int n) throws IOException{ //metodo readNBytes non disponibile in java 8
        byte[] res = new byte[n];
        int pos = 0;
        while(pos < n){
            int r = is.read(res, pos, n - pos);
            if(r == -1) break;
            pos+=r;
        }
        return res;
    }
    
    private byte[] readText() throws IOException{
        return readText(is.read());
    }
    
    private String readString(int n) throws IOException{
        return new String(readText(n));
    }
    
    private String readString() throws IOException{
        return new String(readText());
    }
    
    private void register() throws IOException{
        
        String username = readString();
        String password = readString();
        
        if(password.isEmpty()) //Errore password vuota 
            os.write(2);
        else if(ServerOps.checkIfUserExistsAndCreate(username, password)){ //success
            os.write(0);
            System.out.println("Registration successful");
        }
        else
            os.write(1); //error: username already taken
    }
    
    private void login() throws IOException{
        if(user != null){ //error: already logged in
            return;
        }
        
        String username = readString();
        String password = readString();
        User user = ServerOps.getUser(username);
        if(user == null) //controlla che username sia registrato
            os.write(1); //errore username non registrato
        else if(!user.passwordMatches(password)) //controlla che la password corrisponda
            os.write(2); //errore password non corretta
        else synchronized(user){
            if(ServerOps.isUserLoggedIn(user))
                os.write(3); //errore user ha gia' fatto il login
            else {
                this.user = user;
                ServerOps.userLoggedIn(user);
                os.write(0); //success
                System.out.println("Login successful");
            }
        }
    }
    
    private void logout() throws IOException{
        stopPlaying(); //partita terminata
        ServerOps.userLoggedOut(user); //segnala al server che user ha fatto il logout in modo che possa rifare login
        this.user = null;
    }
    
    private void playWordle() throws IOException{
        if(this.user == null) return;
        if(!ServerOps.canStartNewMatch(user)){
            os.write(1);
            return;
        }
        os.write(0);
        this.user.setLastMatch(ServerOps.getSecret(secret));
        
        System.out.println("User started playing");
        
        this.isPlaying = true;
        this.nTrial=0;
        this.matches = new byte[12][10];
    }
    
    private boolean matchSecret(String word){ //controlla la corrispondenza di word con la secret word
        
        Map<Character, Byte> alphabet = new HashMap<>();
        boolean won = true;
        byte[] match = matches[nTrial];
        word = word.toLowerCase();
        
        for(int i = 0; i<10; i++){ //controlla le lettere uguali alla secret word
            char wordChar = word.charAt(i);
            if(wordChar == secret[i])
                match[i] = '+';
            else
                alphabet.put(wordChar, (byte)(alphabet.getOrDefault(wordChar, (byte)0) + 1)); //se la lettera e' diversa la memorizzo per controllare se e' uguale a un'altra lettera in word
        }
        
        for(int i = 0; i<10; i++){ //controlla le lettere presenti in word ma non alla posizione giusta
            char wordChar = word.charAt(i);
            if(match[i] == '+') continue;
            won = false;
            if(alphabet.getOrDefault(wordChar, (byte)0)>0){
                match[i] = '?';
                alphabet.put(wordChar, (byte)(alphabet.getOrDefault(wordChar, (byte)0) - 1));
            }
            else
                match[i] = 'X';
        }
        return won;
    }

    private void receiveWord() throws IOException{
        
        if(!this.isPlaying) return;
        
        System.out.println("Waiting for word...");
        String word = readString(10);
        if(!ServerOps.wordValid(word)){
            os.write(1); //errore parola non presente nel dizionario
        }
        else{
            boolean won = matchSecret(word);
            if(won){
                this.user.addWonMatch(nTrial);
                System.out.println("User won");
                os.write(2); //l'utente ha vinto
                this.isPlaying = false;
            }
            else if(nTrial>=11){ //sono all'ultimo tentativo
                this.user.addLostMatch();
                System.out.println("User lost");
                os.write(3); //l'utente ha perso
                this.isPlaying = false;
            }
            else
                os.write(0); //l'utente puo' continuare a giocare
            os.write(matches[nTrial]);
            nTrial++;
        }
        os.flush();
    }
    
    private void stopPlaying() throws IOException{
        if(!this.isPlaying) return;
        this.user.addLostMatch();
        this.isPlaying = false;
    }
    
    private void share() throws IOException{
        if(this.isPlaying || this.matches == null){
            os.write(1);
            return;
        }
        ServerOps.share(this.user, this.matches);
        this.matches = null;
        os.write(0);
    }
    
    private void statistics() throws IOException{
        if(this.user == null) return;
        os.write(ServerOps.byteArray(user.getPlayedMatches()));
        os.write(ServerOps.byteArray(user.getWonMatches()));
        os.write(ServerOps.byteArray(user.getLastStreak()));
        os.write(ServerOps.byteArray(user.getLongestStreak()));
        for(int i = 0; i<12; i++)
                os.write(ServerOps.byteArray(user.getGuessDistribution(i)));
        
    }

    @Override
    public void run() {
        System.out.println("Connection with new client established");
        boolean connected = true;
        try {
            while(connected){
                switch(is.read()){
                    case -1:
                    case 0: //disconnect
                        connected = false;
                        System.out.println("User disconnected");
                        break;
                    case 1: //register
                        System.out.println("Received registration request");
                        this.register();
                        System.out.println("Registration process ended");
                        break;
                    case 2: //login
                        System.out.println("Received login request");
                        this.login();
                        System.out.println("Login process ended");
                        break;
                    case 3: //logout
                        System.out.println("User logged out");
                        this.logout();
                        break;
                    case 4: //playWORDLE
                        System.out.println("Received playing request");
                        this.playWordle();
                        break;
                    case 5: //sendWord
                        System.out.println("Received send word request");
                        this.receiveWord();
                        break;
                    case 6: //stop playing
                        System.out.println("Received stop playing request");
                        this.stopPlaying();
                        break;
                    case 7: //send statistics
                        System.out.println("Received statistics request");
                        this.statistics();
                        break;
                    case 8: //share
                        System.out.println("Received share request");
                        this.share();
                        break;
                    default: System.out.println("Wrong client message");
                }
                os.flush();
            }
        } catch (IOException ex) {
            Logger.getLogger(ConnectedUser.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        try {
            logout();
            ServerOps.saveUsers();
        } catch (IOException ex) {
            Logger.getLogger(ConnectedUser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
