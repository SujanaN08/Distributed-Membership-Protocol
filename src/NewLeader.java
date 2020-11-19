import java.io.Serializable;
import java.net.InetAddress;
import java.util.Map;

public class NewLeader implements Serializable {

    int viewId;
    int reqId;
    Operation op;
    int senderId;

    public NewLeader(int viewId, int reqId, Operation op, int senderId) {
        this.viewId = viewId;
        this.reqId = reqId;
        this.op = op;
        this.senderId = senderId;
    }
}