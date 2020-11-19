import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class HeartBeatSender implements Runnable{

    int timeout = 1000;
    int id;
    int port;

    Map<Integer,InetAddress> ipAddress;
    Server server;

    HeartBeatSender(Server s,int id, int port, Map<Integer,InetAddress> ipAdds) {
        this.id = id;
        this.port = port;
        ipAddress = ipAdds;
        this.server = s;
    }

    HeartBeatSender(Server s) {
        this.server = s;
        this.id = 0;
        this.port = s.port;
        this.ipAddress = new HashMap<>();
    }

    public void updateIpAddressList(Map<Integer,InetAddress> ipAdds) {
        //System.out.println("Member list of hb sender updated");
        ipAddress = ipAdds;
    }

    @Override
    public void run() {
        DatagramSocket ds;
        HeartBeat beat = new HeartBeat(id);

        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        ObjectOutput oo = null;
        try {
            oo = new ObjectOutputStream(bStream);
            oo.writeObject(beat);
            oo.close();

            byte[] serializedMessage = bStream.toByteArray();

            ds  = new DatagramSocket();
            while(true) {

               for(Map.Entry<Integer, InetAddress> entry : ipAddress.entrySet()) {
                   DatagramPacket DpSend = new DatagramPacket(serializedMessage, serializedMessage.length,
                           entry.getValue(), port);
                   ds.send(DpSend);
               }
               sleep(timeout);
            }
        } catch (InterruptedException | IOException e) {
//            e.printStackTrace();
        }
    }
}