package trader.common.beans;

import java.util.EventObject;

public class ServiceEvent extends EventObject {
    private static final long serialVersionUID = -1876067635812807352L;

    private String topic;
    private Object[] payloads;
    public ServiceEvent(String topic, Object source, Object[] payloads) {
        super(source);
        this.topic = topic;
        this.payloads = payloads;
    }

    public String getTopic() {
        return topic;
    }

    public Object[] getPayloads() {
        return payloads;
    }

}
