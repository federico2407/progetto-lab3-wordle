package wordleserver;

import java.io.IOException;


public class User {
    private final String username;
    private final String password;
    private int playedMatches;
    private int wonMatches;
    private int lastStreak;
    private int longestStreak;
    private final int[] guessDistribution;
    private long lastMatch; //l'ultima partita dell'utente (come ms dal 1970) per evitare che l'utente possa rigiocare lo stesso giorno
    
    public User(String username, String password){
        System.out.println(username.length());
        this.username = username;
        this.password = password;
        this.playedMatches = 0;
        this.wonMatches = 0;
        this.lastStreak = 0;
        this.longestStreak = 0;
        this.guessDistribution = new int[12]; //automaticamente inizializzato a 0 da java
        this.lastMatch = 0;
    }
    
    public String getUsername(){
        return username;
    }
    
    public boolean passwordMatches(String password){
        return this.password.equals(password);
    }
    
    public void addLostMatch() throws IOException{
        this.playedMatches++;
        this.lastStreak = 0;
        ServerOps.saveUsers();
    }
    public void addWonMatch(int nAttempt) throws IOException{
        this.playedMatches++;
        this.wonMatches++;
        this.lastStreak++;
        if(this.longestStreak<this.lastStreak) this.longestStreak = this.lastStreak;
        this.guessDistribution[nAttempt]++;
        ServerOps.saveUsers();
    }
    
    public int getPlayedMatches(){
        return this.playedMatches;
    }
    public int getWonMatches(){
        return this.wonMatches;
    }
    public int getLastStreak(){
        return this.lastStreak;
    }
    public int getLongestStreak(){
        return this.longestStreak;
    }
    public int getGuessDistribution(int pos){
        return this.guessDistribution[pos];
    }
    
    public void setLastMatch(long lastMatch) throws IOException{
        this.lastMatch = lastMatch;
        ServerOps.saveUsers();
    }
    
    public long getLastMatch(){
        return this.lastMatch;
    }
}
