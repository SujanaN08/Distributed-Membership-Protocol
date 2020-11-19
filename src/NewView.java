import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NewView implements Serializable {

    int viewId;
    ArrayList<Integer> members;
    int leaderId;

    NewView(int viewId, ArrayList<Integer> members, int leaderId) {
        this.viewId = viewId;
        this.members = members;
        this.leaderId = leaderId;
    }
}
