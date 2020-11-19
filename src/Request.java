import java.io.Serializable;

public class Request implements Serializable {

    int requestId;
    int viewId;
    Operation op;
    int peerId;
    int senderId;

    public Request(int reqId, int viewId, Operation op, int peerId, int senderId) {

        this.requestId = reqId;
        this.viewId = viewId;
        this.op = op;
        this.peerId = peerId;
        this.senderId = senderId;
    }
}
