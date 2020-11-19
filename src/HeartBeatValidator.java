import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class HeartBeatValidator implements Runnable {

    Map<Integer, Timestamp> ticks;
    final int timeout = 1000;
    HeartBeatHandler handler;

    public HeartBeatValidator(HeartBeatHandler handler) {
        ticks = new HashMap<>();
        this.handler = handler;
    }

    public void updateValues(int processID, Timestamp time) {
        ticks.put(processID,time);
    }

    @Override
    public void run() {

        while(true) {
            try {
                sleep(100);
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                for(Map.Entry<Integer,Timestamp> entry : ticks.entrySet()) {
                    if(Math.abs(timestamp.getTime() - entry.getValue().getTime()) > 2*timeout) {
                        handler.failureDetected(entry.getKey());
                        ticks.remove(entry.getKey());
                    }
                }
            } catch (Exception e) {
//                e.printStackTrace();
            }
        }
    }
}
