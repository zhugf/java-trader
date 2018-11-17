package trader.simulator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import trader.common.beans.BeansContainer;

public class SimBeansContainer implements BeansContainer {

    private Map<Class, Object> beans = new HashMap<>();

    @Override
    public <T> T getBean(Class<T> clazz) {
        T t = (T)beans.get(clazz);
        if ( t==null ) {
            for(Object bean:beans.values()) {
                if ( bean.getClass()==clazz || clazz.isAssignableFrom(bean.getClass()) ) {
                    return (T)bean;
                }
            }
        }
        return t;
    }

    @Override
    public <T> T getBean(Class<T> clazz, String purposeOrId) {
        T t = (T)beans.get(clazz);
        if ( t==null ) {
            for(Object bean:beans.values()) {
                if ( bean.getClass()==clazz || clazz.isAssignableFrom(bean.getClass()) ) {
                    return (T)bean;
                }
            }
        }
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return Collections.emptyMap();
    }

    public void addBean(Class interfaceClass, Object bean) {
        beans.put(interfaceClass, bean);
    }

}
