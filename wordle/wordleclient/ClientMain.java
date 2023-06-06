package wordleclient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;


public class ClientMain {
    
    private static Scanner scanner;
    private static OutputStream os;
    private static InputStream is;
    private static NotificationsThread notificationsThread = null;
    private static Properties properties;
    
    private static boolean isPlaying = false;
    private static boolean isLoggedIn = false;
    
    private static byte[] readText(int n) throws IOException{ //metodo readNBytes non disponibile in java 8
        byte[] res = new byte[n];
        int pos = 0;
        while(pos < n){
            int r = is.read(res, pos, n - pos);
            if(r == -1) break;
            pos+=r;
        }
        return res;
    }
    
    private static int readInt() throws IOException{
        return ((int)is.read()<<24) + ((int)is.read()<<16) + ((int)is.read()<<8) + ((int)is.read());
    }
    
    private static void sendString(String s) throws IOException{
        os.write(s.length());
        os.write(s.getBytes());
    }
    
    private static void register() throws IOException{
        if(isLoggedIn) return;
        os.write(1);
        //leggo e mando username e password
        System.out.print("Username: ");
        sendString(scanner.next());
        System.out.print("Password: ");
        sendString(scanner.next());
        os.flush(); //dico al BufferedOutputStream di mandare tutto

        switch(is.read()){ //risposta del server
            case 0:
                System.out.println("Registration successful");
                break;
            case 1:
                System.out.println("error: username taken");
                break;
            case 2:
                System.out.println("Errore: empty password");
                break;
        }
    }
    
    private static void login() throws IOException{
        if(isLoggedIn) return;
        os.write(2);
        //leggo e mando username e password
        System.out.print("Username: ");
        String username = scanner.next();
        sendString(username);
        System.out.print("Password: ");
        sendString(scanner.next());
        os.flush();
        
        switch (is.read()) { //risposta del server
            case 0: //success
                System.out.println("Login successful");
                notificationsThread = new NotificationsThread(username, properties.getProperty("UDPIP"), Integer.parseInt(properties.getProperty("UDPPort"))); //creo e avvio il thread che riceve notifiche multicast
                notificationsThread.start();
                isLoggedIn = true;
                break;
            case 1: //username non registrato
                System.out.println("Error: user not registered");
                break;
            case 2://password non corrisponde
                System.out.println("Error: wrong password");
                break;
            case 3:
                System.out.println("Error: user already logged in");
                break;
        }
    }
    
    private static void logout() throws IOException{
        if(!isLoggedIn) return;
        os.write(3);
        os.flush();
        System.out.println("User logged out");
        notificationsThread.safeStop(); //ferma il thread delle notifiche
        notificationsThread = null;
        isLoggedIn = false;
        isPlaying = false;
    }
    
    private static void playWordle() throws IOException{
        if(isPlaying||!isLoggedIn) return;
        os.write(4);
        os.flush();
        if(is.read()!=0){ //risposta dal server
            System.out.println("You already played for this day");
            return;
        }
        isPlaying = true;
        System.out.println("Game started");
    }
    
    private static void sendWord() throws IOException{
        if(!isPlaying) return;
        int input;
        
        System.out.println("Word: ");
        String word = scanner.next();

        if(word.length()!=10){
            System.out.println("Word must be 10 characters long");
            return;
        }
        
        os.write(5);
        os.write(word.getBytes()); //mando la parola al server
        os.flush();
        
        input = is.read(); //server dice se parola e' nel dizionario, ho vinto, perso o posso continuare a giocare
        if(input == 1){
            System.out.println("error: word not in dictionary");
            return;
        }
        
        System.out.println(new String(readText(10))); //stringa contenente 10 caratteri che indicano la corrispondenza tra parola e secret
        
        if(input == 2){
            System.out.println("You won");
            isPlaying = false;
        }
        else if(input == 3){
            System.out.println("You lost");
            isPlaying = false;
        }
    }
    
    private static void stopPlaying() throws IOException{
        if(!(isPlaying && isLoggedIn)) return;
        os.write(6);
        os.flush();
        System.out.println("Match ended");
        isPlaying = false;
    }
    
    private static void sendMeStatistics() throws IOException{
        if(!isLoggedIn) return;
        os.write(7);
        os.flush();
        System.out.println("Played Matches: " + readInt());
        System.out.println("Won Matches: " + readInt());
        System.out.println("Last streak: " + readInt());
        System.out.println("Longest streak: " + readInt());
        
        System.out.println("Guess distribution:");
        for(int i = 1; i<=12; i++)
            System.out.print(i+"\t");
        System.out.println();
        for(int i = 0; i<12; i++)
            System.out.print(readInt() + "\t");
        System.out.println();
    }
    
    private static void share() throws IOException{
        if(isPlaying || !isLoggedIn) return;
        os.write(8);
        os.flush();
        if(is.read() != 0)
            System.out.println("You don't have anything to share");
        else
            System.out.println("Score shared");
    }

    public static void main(String[] args) throws IOException{
        boolean loop = true;
        
        scanner = new Scanner(System.in);
        
        properties = new Properties();
        properties.load(new FileInputStream("client.properties"));
        System.out.println("Properties generated");
        
        Socket socket = new Socket(properties.getProperty("IP"), Integer.parseInt(properties.getProperty("port")));
        System.out.println("Connection established");
        
        os = new BufferedOutputStream(socket.getOutputStream());
        is = new BufferedInputStream(socket.getInputStream());
        
        while(loop){
            System.out.println("0 - Exit");
            if(!isLoggedIn){
                System.out.println("1 - Register");
                System.out.println("2 - Login");
            }
            else{
                
                if(!isPlaying){
                    System.out.println("3 - Logout");
                    System.out.println("4 - Play Wordle");
                }
                    
                else{
                    System.out.println("5 - Send word");
                    System.out.println("6 - Stop playing");
                }
                System.out.println("7 - My statistics");
                if(!isPlaying){
                    System.out.println("8 - Share");
                }
                System.out.println("9 - Receive notifications");
            }
            
            while(!scanner.hasNext()) {}
            if(!scanner.hasNextInt()){
                System.out.println("Input " + scanner.nextLine() + " non riconosciuto");
                continue;
            }
            switch(scanner.nextInt()){
                case 0:
                    logout();
                    os.write(0);
                    os.flush();
                    loop = false;
                    socket.close();
                    break;
                case 1: //register
                    register();
                    break;
                case 2: //login
                    login();
                    break;
                case 3: //logout
                    logout();
                    break;
                case 4: //playWordle
                    playWordle();
                    break;
                case 5: //sendWord
                    sendWord();
                    break;
                case 6: //stop playing
                    stopPlaying();
                    break;
                case 7: //statistiche dell'utente
                    sendMeStatistics();
                    break;
                case 8: //share
                    share();
                    break;
                case 9: //stampa notifiche sulla cli
                    if(!isLoggedIn) break;
                    notificationsThread.printNotifications();
                    break;
            }
        } 
    }
}
