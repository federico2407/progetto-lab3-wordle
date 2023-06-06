package wordleclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.LinkedBlockingQueue;


public class NotificationsThread extends Thread{
    
    private final MulticastSocket socket;
    private final InetAddress group;
    private final LinkedBlockingQueue<DatagramPacket> notifications;
    private final String username;
    private volatile boolean executing;
    
    public NotificationsThread(String username, String ip, int port) throws IOException{
        socket = new MulticastSocket(port);
        group = InetAddress.getByName(ip);
        socket.joinGroup(group);
        
        notifications = new LinkedBlockingQueue<>();
        this.username = username;
    }
    
    public void printNotifications(){
        boolean noNotifications = true;
        
        byte[] buf;
        String name;
        
        while(!notifications.isEmpty()){
            
            buf = notifications.poll().getData();
            name = new String(buf, 120, 392).trim(); //512 - 120 = 392
            if(name.equals(this.username)) continue;
            if(noNotifications){
                noNotifications = false;
                System.out.println("Notifications:");
            }
            
            System.out.println(name + ":");
            for(int i = 0; i<12; i++){
                if(buf[i*10] == 0) break;
                System.out.println((i+1) + ":\t" + new String(buf, i*10, 10));
            }
            System.out.println();
        }
        if(noNotifications)
            System.out.println("You have no notifications");
        
    }
    
    public void safeStop() throws IOException{
        this.executing = false;
        socket.leaveGroup(group);
        this.socket.close();
    }
    
    @Override
    public void run(){
        this.executing = true;
        while (this.executing) {
            try {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                notifications.add(packet);
            } catch (IOException ex) {}
        }
    }
}
