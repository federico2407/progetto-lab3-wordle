package wordleserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ServerOps {
    private static ConcurrentHashMap<String, User> users;
    private static Set<User> loggedUsers;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static char[] secret;
    private static long lastSecret;
    private static ServerSocket socket;
    private static DatagramSocket notificationsSocket;
    private static InetAddress group;
    private static Properties properties;
    
    
    public static void loadUsers() throws IOException{
        try (FileReader file = new FileReader("users.json")){
            Type usermap = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType(); //mi salvo il tipo di ConcurrentHashMap<String, User> per la deserializzazione con reflection del file json
            users = gson.fromJson(new JsonReader(file), usermap);
        } catch (FileNotFoundException ex) {
        }
        if(users == null) users = new ConcurrentHashMap<>();
        loggedUsers = Collections.synchronizedSet(new HashSet<>());
    }
    
    public static synchronized void saveUsers() throws IOException{
        try (FileWriter file = new FileWriter("users.json")) {
            file.write(gson.toJson(users));
        }
    }
    
    public static boolean checkIfUserExistsAndCreate(String username, String password) throws IOException{
        return users.putIfAbsent(username, new User(username, password)) == null;
    }
    
    public static User getUser(String username){
        return (User) users.get(username);
    }
    
    public static void userLoggedIn(User user){
        loggedUsers.add(user);
    }
    
    public static void userLoggedOut(User user){
        loggedUsers.remove(user);
    }
    
    public static boolean isUserLoggedIn(User user){
        return loggedUsers.contains(user);
    }
    
    public static ServerSocket generateServerSocket() throws IOException{
        return socket = new ServerSocket(Integer.parseInt(properties.getProperty("TCPPort")));
    }
    
    public static boolean wordValid(String word) throws FileNotFoundException, IOException { //ricerca binaria nel file words.txt di word
        try (RandomAccessFile file = new RandomAccessFile("words.txt", "r")) {
            long start = 0;
            long end = file.length();
            long mid;
            int res;
            
            while(start <= end){
                mid = (start + end) / 2;
                mid = mid - mid % 11;
                file.seek(mid);
                res = word.compareTo(file.readLine());
                
                if(res < 0)
                    end = mid-11;
                else if(res > 0)
                    start = mid+11;
                else
                    return true;
            }
            return false;
        }
        
    }
    
    public static long getSeed(){
        return Long.parseLong(properties.getProperty("seed"));
    }
    
    
    public synchronized static void newSecret(long currentTime) throws FileNotFoundException, IOException{
        RandomAccessFile file = new RandomAccessFile("words.txt", "r");
        long pos = Math.abs(new Random(currentTime ^ getSeed()).nextLong())%file.length(); //prendo un punto casuale all'interno del file
        pos = pos - pos % 11; //lo arrotondo all'inizio di una parola (parole da 10 lettere + \n)
        file.seek(pos); //vado a quella posizione
        secret = file.readLine().toCharArray();
        lastSecret = currentTime;
        System.out.println("New Secret generated: "+ new String(secret));
    }
    
    public static boolean canStartNewMatch(User user){ //confronta le date dell'ultima partita dell'utente e della partita in corso
        return user.getLastMatch()<lastSecret;
    }
    
    public synchronized static long getSecret(char[] s){
        System.arraycopy(secret, 0, s, 0, 10);
        return lastSecret;
    }
    
    public static void generateNotificationsSocket() throws IOException{
        notificationsSocket = new DatagramSocket();
        group = InetAddress.getByName(getUDPIP());
    }
    
    
    public static void share(User user, byte[][] matches) throws IOException{
        byte[] buf = new byte[120+user.getUsername().length()]; //il buffer contiene il risultato del match (12 tentativi * 10 lettere per parola) + username
        for(int i = 0; i<12; i++)
            System.arraycopy(new String(matches[i]).getBytes(), 0, buf, i*10, 10); //metto nel buffer risultato del match
        System.arraycopy(user.getUsername().getBytes(), 0, buf, 120, user.getUsername().length()); //metto nel buffer l'username
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, getUDPPort());
        notificationsSocket.send(packet);
    }
    
    
    public static void loadProperties() throws FileNotFoundException, IOException{
        properties = new Properties();
        properties.load(new FileInputStream("server.properties"));
        System.out.println("Properties generated");
    }
    
    public static long getDayLength(){ //restituisce la lunghezza di un giorno (in ms) salvata in server.properties in secondi
        return Long.parseLong(properties.getProperty("dayLength"))*1000;
    }
    
    public static String getUDPIP(){ //ip multicast
        return properties.getProperty("UDPIP");
    }
    
    public static int getUDPPort(){ //porta multicast
        return Integer.parseInt(properties.getProperty("UDPPort"));
    }
    
    public static void closeSockets() throws IOException{
        socket.close();
        notificationsSocket.close();
    }
    
    public static byte[] byteArray(int n){
        return new byte[]{(byte)(n>>>24), (byte)(n>>>16), (byte)(n>>>8), (byte)n};
    }
    
}
