package trader.service.stats;

import java.util.ArrayList;
import java.util.List;

import trader.common.util.StringUtil;
import trader.service.ServiceConstants;

public class StatsItem implements Comparable<StatsItem>{
    private String node;
    private String application;
    private String service;
    private String component;
    private String item;

    /**
     * 统计项的类型:实时or累积
     */
    private StatsItemType type = StatsItemType.Cumulative;

    /**
     * 持久统计项目.
     * <BR>非持久统计项目,意味着10次统计周期后, 统计历史将会被清除.
     */
    private boolean persistent;

    /**
     * 重启动后继续累积.
     * <BR>某些统计指标需要, 例如: 累积处理事件数量, flume重启后,不会从零开始计算,而是继续累积计算.
     */
    private boolean cumulativeOnRestart;

    private StatsItemValueGetter valueGetter;

    private transient String key;

    public StatsItem(String service, String item) {
        this(System.getProperty(ServiceConstants.SYSPROP_APPLICATION_NAME), service, null, item);
    }

    public StatsItem(String service, String component, String item) {
        this(System.getProperty(ServiceConstants.SYSPROP_APPLICATION_NAME), service, component, item);
    }

    public StatsItem(String application, String service, String component, String item) {
        this.application = application;
        this.service = service;
        this.component = component;
        this.item = item;
    }

    public String getKey() {
        if ( key==null ) {
            List<String> parts = new ArrayList<>();
            if ( !StringUtil.isEmpty(node) ) {
                parts.add(node);
            }
            if ( !StringUtil.isEmpty(application) ) {
                parts.add(application);
            }
            parts.add(service);
            if ( !StringUtil.isEmpty(component)) {
                parts.add(component);
            }
            parts.add(item);
            StringBuilder result = new StringBuilder(128);
            for(int i=0;i<parts.size();i++) {
                if ( i>0 ) {
                    result.append(".");
                }
                result.append(parts.get(i));
            }
            key = result.toString();
        }
        return key;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getComponent() {
        return component;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public StatsItemType getType() {
        return type;
    }

    public void setType(StatsItemType type) {
        this.type = type;
    }

    public StatsItemValueGetter getValueGetter() {
        return valueGetter;
    }

    public void setValueGetter(StatsItemValueGetter valueGetter) {
        this.valueGetter = valueGetter;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isCumulativeOnRestart() {
        return cumulativeOnRestart;
    }

    public void setCumulativeOnRestart(boolean cumulativeOnRestart) {
        this.cumulativeOnRestart = cumulativeOnRestart;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof StatsItem)) {
            return false;
        }

        StatsItem i = (StatsItem) o;
        return StringUtil.equals(node, i.node)
                && StringUtil.equals(application, i.application)
                && StringUtil.equals(service, i.service)
                && StringUtil.equals(component, i.component)
                && StringUtil.equals(item, i.item);
    }

    @Override
    public int hashCode() {
        int nodeHash = node.hashCode() * 10000;
        int appHash = 0;
        int compHash = 0;
        if ( !StringUtil.isEmpty(application)) {
            appHash = application.hashCode()*1000;
        }
        if ( !StringUtil.isEmpty(component)) {
            compHash = component.hashCode()*10;
        }
        return nodeHash + appHash + service.hashCode() * 100 + compHash + item.hashCode();
    }

    @Override
    public int compareTo(StatsItem o) {
        return getKey().compareTo(o.getKey());
    }

}
