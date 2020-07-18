package trader.simulator;

import java.util.Map;

import org.eclipse.jetty.util.StringUtil;

import trader.service.repository.AbsBOEntity;

public class SimBOEntity extends AbsBOEntity {

    private Map<String, String> data;

    public SimBOEntity(BOEntityType type) {
        super(type);
    }

    public String get(String id) {
        return data.get(id);
    }

    public void put(String id, String value) {
        if ( StringUtil.isEmpty(value)) {
            data.remove(id);
        } else {
            data.put(id, value);
        }
    }

    Map<String, String> getData(){
        return data;
    }

}
