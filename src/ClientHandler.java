import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    final DataInputStream dis;
    final DataOutputStream dos;
    final Socket s;
    final Server server;

    ClientHandler(DataInputStream dis, DataOutputStream dos, Socket s, Server server) {
        this.dis = dis;
        this.dos = dos;
        this.s = s;
        this.server = server;
    }

    @Override
    public void run() {

        while(true) {
            try {
                //DataInputStream dis = new DataInputStream(s.getInputStream());
                ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream objectInput = new ObjectInputStream(s.getInputStream());
                try {
                    Object object = objectInput.readObject();


                    if(object instanceof Ack) {
                        server.collectAck((Ack)object);
                    }
                    else if (object instanceof NewView) {
                        server.newViewMessageReceived((NewView)object);
                    }
                    else if (object instanceof Request) {
                        server.requestMessageReceived((Request)object);
                    }

                } catch (ClassNotFoundException | IOException e) {
//                    e.printStackTrace();
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }
}
