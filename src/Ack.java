import java.io.Serializable;

public class Ack implements Serializable {

    int requestId;
    int viewId;

    public Ack(int requestId, int viewId) {
        this.requestId = requestId;
        this.viewId = viewId;
    }
}
