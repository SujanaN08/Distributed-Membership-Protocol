import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.*;

import static java.lang.Thread.sleep;


public class Server {

    Map<Integer, InetAddress> members;
    List<String> hosts;
    int id = 0;
    int intialPort = 8080;
    int port = 8080;
    int delay = 0;
    InetAddress myAddress;

    final int timeout = 40;
    boolean leader = false;
    int viewID = 1;
    int reqID = 1;
    int leaderID = 1;

    HeartBeatHandler hbHandler;
    HeartBeatSender hbSender;


    List<Socket> socket = null;
    List<Socket> allSockets = null;

    Socket addSocket = null;

    final int MAXCLIENT = 10;

    Request currentReq = null;
    List<Integer> countAck;

    boolean newLeaderInProgress = false;
    List<Request> newLeaderRequests = new ArrayList<>();
    int requestCounter = 0;

    Server() throws UnknownHostException {
        myAddress = InetAddress.getLocalHost();
        members = new HashMap<>();
        hosts = new ArrayList<>();
        hbHandler = new HeartBeatHandler(this);
        hbSender = new HeartBeatSender(this);
        socket = new ArrayList<>();
        allSockets = new ArrayList<>();
        countAck = new ArrayList<>();
    }

    void readFromFile(String hostname, String file) throws FileNotFoundException, UnknownHostException {
        File f = new File(file);
        Scanner sc = new Scanner(f);

        boolean first = true;
        boolean found = false;
        int count = 1;

        while (sc.hasNextLine()) {
            String host = sc.nextLine();
            System.out.println(host);

            if(host.equals(hostname)) {
                this.id = count;
            }
            if(!host.equals(hostname) && !found) {
                InetAddress address = InetAddress.getByName(host);
                System.out.println(address);
                members.put(count,address);
            } else {
                found = true;
                if(first) {
                    leader = true;
                    leaderID = id;
                    System.out.println("I am leader");
                }
            }
            hosts.add(host);
            first = false;
            count++;
        }
    }

    void newProcessFound(int processID) throws IOException, InterruptedException {
        if(leader) {
            sendJoinMessages(processID);
        }
    }

    private void sendJoinMessages(int processID) throws IOException, InterruptedException {

        if(members.keySet().contains(processID) || (currentReq != null && currentReq.peerId == processID)) {
            return;
        }
        if(members.size() == 0) {
            System.out.println("First member joined");
            members.put(processID, InetAddress.getByName(hosts.get(processID - 1)));
            try {
                sendNewVewMessage();
            } catch (UnknownHostException | InterruptedException e) {

            }
        }
        else{
            Request req = new Request(reqID,viewID,Operation.ADD,processID,id);
            reqID++;
            currentReq = req;
            for(InetAddress ip : members.values()) {
                sendMessageOverUDP(ip, req);
            }
            System.out.println("New Add request sent");
        }
    }

    void requestMessageReceived(Request request) throws IOException, InterruptedException {
        System.out.println("New request received from ID: " + request.senderId);
        if(newLeaderInProgress) {
            System.out.println("New Leader operations handled");
            for(Request r : newLeaderRequests) {
                if(r.requestId == request.requestId && r.op == request.op && r.peerId == request.peerId) {
                    return;
                }
            }
            newLeaderRequests.add(request);
            if(request.op == Operation.ADD) {
                sendJoinMessages(request.peerId);
            }
            else if (request.op == Operation.DELETE) {
                failureDetected(request.peerId);
            }
            else if (request.op == Operation.NOTHING) {
                newLeaderInProgress = false;
            }
            if(requestCounter == members.size()-1) {
                if (newLeaderRequests.size() == 0) {
                    sendNewVewMessage();
                    newLeaderRequests = new ArrayList<>();
                    newLeaderInProgress = false;
                    requestCounter = 0;
                }
            }
            return;
        }

        if(request.viewId == viewID) {
            currentReq = request;
            reqID = request.requestId;
            if(request.op == Operation.ADD) {
                sendAckMessages(request);
            }
            else if (request.op == Operation.DELETE){
                sendAckMessages(request);
            }
        }
    }

    void sendAckMessages(Request request) throws IOException, InterruptedException {
        System.out.println("sending ack message");
        Ack ack = new Ack(request.requestId,request.viewId);
        sendMessageOverUDP(members.get(request.senderId),ack);
    }

    void collectAck(Ack ack) throws IOException, InterruptedException {
        System.out.println("receiving ack message");
        if (ack.requestId == currentReq.requestId && ack.viewId == viewID) {
            countAck.add(ack.requestId);
        }
        int membersSize = members.size();
        if(newLeaderInProgress) {
            membersSize = membersSize - 1;
        }
        if(countAck.size() == membersSize) {
            if(currentReq.op == Operation.ADD) {
                int newId = currentReq.peerId;
                members.put(newId, InetAddress.getByName(hosts.get(newId - 1)));
                hbSender.updateIpAddressList(members);
                List<Integer> m = new ArrayList<>();
                for(Map.Entry<Integer, InetAddress> entry : members.entrySet()) {
                    m.add(entry.getKey());
                }
                hbHandler.setMemberList(m);
                if(addSocket != null) {
                    socket.add(addSocket);
                }
                addSocket = null;
                currentReq = null;
                countAck = new ArrayList<>();
                sendNewVewMessage();
                if(newLeaderInProgress) {
                    newLeaderInProgress = false;
                    requestCounter = 0;
                    newLeaderRequests = new ArrayList<>();
                }
            }
        }
        else if(countAck.size() == (membersSize - 1)) {
            if (currentReq.op == Operation.DELETE) {
                int newId = currentReq.peerId;
                members.remove(newId);
                hbSender.updateIpAddressList(members);
                List<Integer> m = new ArrayList<>();
                for(Map.Entry<Integer, InetAddress> entry : members.entrySet()) {
                    m.add(entry.getKey());
                }
                hbHandler.setMemberList(m);
                currentReq = null;
                countAck = new ArrayList<>();
                sendNewVewMessage();
                if(newLeaderInProgress) {
                    System.out.println("New leader process is over");
                    newLeaderInProgress = false;
                    requestCounter = 0;
                    newLeaderRequests = new ArrayList<>();
                }
            }
        }
    }

    void receiveMessagesOverUDP() {

        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(port+1);
        } catch (SocketException e) {
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
                    Object object = iStream.readObject();
                    iStream.close();

                    if(object instanceof Ack) {
                        Ack a = (Ack)object;
                        System.out.println(a);
                        this.collectAck(a);
                    }
                    else if (object instanceof NewView) {
                        this.newViewMessageReceived((NewView)object);
                    }
                    else if (object instanceof Request) {
                        this.requestMessageReceived((Request)object);
                        if(newLeaderInProgress == true) {
                            requestCounter++;
                        }
                    }
                    else if (object instanceof NewLeader) {
                        this.newLeaderMessageReceived((NewLeader)object);
                    }
                }
                catch(Exception e)
                {

                }

                receive = new byte[65535];
            } catch (IOException e) {

            }
        }
    }

    void sendNewVewMessage() throws IOException, InterruptedException {
        System.out.println("sending new view messages");
        //sleep(delay);
        viewID = viewID + 1;
        ArrayList<Integer> newMem = new ArrayList<>(members.keySet());
        newMem.add(id);
        NewView msg = new NewView(viewID,newMem,id);
        for(InetAddress ip : members.values()) {
            sendMessageOverUDP(ip, msg);
        }
    }

    void failureDetected(int processId) throws InterruptedException {

        if(leader) {
            if(members.size() == 1) {
                members.remove(processId);
                viewID = viewID + 1;
                System.out.println("Process " + processId + "deleted and new View ID is " + viewID);
                hbSender.updateIpAddressList(members);
                List<Integer> m = new ArrayList<>();
                for(Map.Entry<Integer, InetAddress> entry : members.entrySet()) {
                    m.add(entry.getKey());
                }
                hbHandler.setMemberList(m);
            }
            else {
                Request req = new Request(reqID,viewID,Operation.DELETE,processId,id);
                reqID++;
                currentReq = req;
                for(InetAddress ip : members.values()) {
                    sendMessageOverUDP(ip, req);
                }
                System.out.println("New Delete request sent");
            }
        }
        else if (processId == leaderID){
            int nextMemberID = 100;
            for(int val : members.keySet()) {
                if(val < nextMemberID && val != processId) {
                    nextMemberID = val;
                }
            }
            if(nextMemberID > id) {
                members.remove(leaderID);
                System.out.println("I am assumed leader now and I am starting Leader election");
                leader = true;
                newLeaderInProgress = true;
                sendNewLeaderMessages();
            }
        }
    }

    void sendMessageOverUDP(InetAddress ip, Object msg) throws InterruptedException {

        DatagramSocket ds;
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        ObjectOutput oo = null;
        try {
            oo = new ObjectOutputStream(bStream);
            oo.writeObject(msg);
            oo.close();

            byte[] serializedMessage = bStream.toByteArray();

            ds  = new DatagramSocket();

            DatagramPacket DpSend = new DatagramPacket(serializedMessage, serializedMessage.length, ip, port + 1);
            ds.send(DpSend);
        } catch (IOException e) {
        }
    }

    void newViewMessageReceived(NewView newView) throws UnknownHostException {
        System.out.println("New view" + newView.viewId + " with size " + newView.members.size());
        System.out.println("The following are in the membership list");
        for(int i : newView.members) {
            System.out.println(i);
        }
        viewID = newView.viewId;
        leaderID = newView.leaderId;
        for (Integer i : newView.members) {
            if(i != id) {
                members.put(i,InetAddress.getByName(hosts.get(i - 1)));
            }
        }
        hbSender.updateIpAddressList(members);
        List<Integer> m = new ArrayList<>();
        for(Map.Entry<Integer, InetAddress> entry : members.entrySet()) {
            m.add(entry.getKey());
        }
        hbHandler.setMemberList(m);
        currentReq = null;
    }

    void sendNewLeaderMessages() throws InterruptedException {
        System.out.println("sending new leader messages");
        reqID = reqID + 1;
        NewLeader msg = new NewLeader(viewID, reqID, Operation.PENDING,id);
        for(InetAddress ip : members.values()) {
            sendMessageOverUDP(ip, msg);
        }
    }

    void newLeaderMessageReceived(NewLeader msg) throws InterruptedException {
        Request req;
        System.out.println("received new leader messages");
        if(currentReq != null) {
            req = new Request(currentReq.requestId, viewID, currentReq.op, currentReq.peerId, id);
        } else {
            req = new Request(reqID, viewID, Operation.NOTHING, 0, id);
        }
        sendMessageOverUDP(members.get(msg.senderId), req);
    }

    void receiveHeartBeats() {
        List<Integer> m = new ArrayList<>();
        for(Map.Entry<Integer, InetAddress> entry : members.entrySet()) {
            m.add(entry.getKey());
        }
        hbHandler.setMemberList(m);
        Thread rec =  new Thread(hbHandler);
        rec.start();
    }

    void sendHeartBeats() {
        hbSender.updateIpAddressList(members);
        hbSender.port = port;
        hbSender.id = id;
        Thread sender = new Thread(hbSender);
        sender.start();
    }


    public static void main(String args[]) throws Exception {

        int port = 0;
        String filename = "";
        int delay = 0;
        int count = args.length;
        for(int i = 0; i < count; i++) {
            String arg = args[i];
            if(arg.equals("-p")) {
                port = Integer.parseInt(args[i + 1]);
            } else if(arg.equals("-h")) {
                filename = args[i+1];
            }
            else if(arg.equals("-d")) {
                delay = Integer.parseInt(args[i+1]);
            }
            i = i+1;
        }

        System.out.println(filename);
        String hostname = InetAddress.getLocalHost().getHostName();
        System.out.println("Hostname:" + hostname);
        System.out.println("IPAddress: " + InetAddress.getLocalHost());
        Server s = new Server();

        sleep(1000);
        try {
            s.intialPort = port;
            s.port = port;
            s.delay = delay;
            s.readFromFile(hostname, filename);
            System.out.println("My ID:" + s.id);
            s.receiveHeartBeats();
            s.sendHeartBeats();
            s.receiveMessagesOverUDP();
        }
        catch (Exception e) {
        }
    }
}
