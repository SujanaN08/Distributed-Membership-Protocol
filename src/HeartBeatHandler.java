import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class HeartBeatHandler implements Runnable {

    private List<Integer> members;
    private int timeout = 1000;
    private HeartBeatValidator validator;

    private Server server;

    HeartBeatHandler(Server s) {
        this.server = s;
        this.members = new ArrayList<>();
        validator = new HeartBeatValidator(this);
    }

    public void setMemberList(List<Integer> members) {
        //System.out.println("Member list of handler updated");
        this.members = members;
    }

    void failureDetected(int processID) throws InterruptedException {
        if(members.contains(processID)) {
            System.out.println("Peer " + processID + " not reachable");
            server.failureDetected(processID);
        }
    }

    @Override
    public void run() {

        boolean receivedHeartBeat = false;
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(server.port);
        } catch (SocketException e) {
//            e.printStackTrace();
        }
        byte[] receive = new byte[65536];

        DatagramPacket DpReceive = null;
        while (true)
        {
            DpReceive = new DatagramPacket(receive, receive.length);

            try {
                ds.receive(DpReceive);

                try
                {
                    ObjectInputStream iStream = new ObjectInputStream(new ByteArrayInputStream(receive));
                    HeartBeat obj = (HeartBeat) iStream.readObject();
                   // System.out.println("Received");
                    iStream.close();

                    if(!members.contains(obj.id)) {
                        //System.out.println("New process " + obj.id + " found");
                        server.newProcessFound(obj.id);
                    } else {
                        validator.updateValues(obj.id, new Timestamp(System.currentTimeMillis()));
                    }
                    if(!receivedHeartBeat) {
                        receivedHeartBeat = true;
                        Thread t = new Thread(validator);
                        t.start();
                    }
                }
                catch(Exception e)
                {
//                    e.printStackTrace();
                }

                receive = new byte[65535];
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }
}
