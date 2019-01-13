package trader.service.beans;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.beans.Lifecycle;

@SuppressWarnings("rawtypes")
public class DiscoverableRegistry {
    private static final Logger logger = LoggerFactory.getLogger(DiscoverableRegistry.class);

    /**
     * Key: 接口
     * Value: 实现类, Key, Purpose, Value: 实现类
     */
    private static final Map<Class, Map<String, Class>> allConcreteClasses = scanDiscoverableClasses();

    public static<T> Class<T> getConcreteClass(Class<T> interfaceClass, String purpose){
        Map<String, Class> concreteClasses = allConcreteClasses.get(interfaceClass);
        if ( concreteClasses==null ) {
            return null;
        }
        Class clazz = concreteClasses.get(purpose);
        return clazz;
    }

    public static<T> Map<String, Class<T>>  getConcreteClasses(Class<T> interfaceClass){
        Map<String, Class> concreteClasses = allConcreteClasses.get(interfaceClass);
        return (Map)concreteClasses;
    }

    /**
     * 返回某个接口的所有实现类
     */
    public static<T> T getConcreteInstance(BeansContainer beansContainer, Class<T> interfaceClass, String purpose){
        Class clazz = getConcreteClass(interfaceClass, purpose);
        if ( clazz==null ) {
            return null;
        }
        try {
            T t = (T)clazz.newInstance();
            if (t instanceof Lifecycle) {
                ((Lifecycle)t).init(beansContainer);
            }
            return t;
        }catch(Throwable t) {
            logger.warn("Create new instance for discoverable class "+clazz+" failed", t);
        }
        return null;
    }

    /**
     * 返回某个接口的所有实现类.
     * @return Map: string: purpose, value: instances
     */
    public static<T> Map<String, T> getConcreteInstances(BeansContainer beansContainer, Class<T> interfaceClass){
        Map<String, Class> concreteClasses = null;
        for(Map.Entry<Class, Map<String, Class>> entry:allConcreteClasses.entrySet()) {
            if ( interfaceClass==(entry.getKey()) ) {
                concreteClasses = (entry.getValue());
                break;
            }
        }
        if ( concreteClasses==null) {
            return Collections.EMPTY_MAP;
        }
        Map<String, T> result = new HashMap<>(concreteClasses.size());
        for(Map.Entry<String, Class> e: concreteClasses.entrySet()) {
            try {
                T t = (T)e.getValue().newInstance();
                result.put(e.getKey(), t);
                if (t instanceof Lifecycle) {
                    ((Lifecycle)t).init(beansContainer);
                }
            }catch(Throwable t) {
                logger.warn("Create new instance for discoverable class "+e.getValue()+" failed", t);
            }
        }
        return result;
    }

    private static Map<Class, Map<String, Class>> scanDiscoverableClasses() {
        Map<Class, Map<String, Class>> concreteInstances = new HashMap<>();
        //LogServiceImpl.setLogLevel("org.reflections.Reflections", "ERROR");
        List<Object> params = new ArrayList<>();
        ClassLoader cl = DiscoverableRegistry.class.getClassLoader();
        params.add(cl);
        if ( cl instanceof URLClassLoader ) {
            URL urls[] = ((URLClassLoader)cl).getURLs();
            params.addAll(Arrays.asList(urls));
        } else {
            try{
                Field ucpField = cl.getClass().getDeclaredField("ucp");
                Object ucp = ucpField.get(cl);

            }catch(Throwable t) {
                t.printStackTrace();
            }
        }
        Reflections reflections = new Reflections(params.toArray(new Object[params.size()]));
        Set<Class<?>> allClasses = reflections.getTypesAnnotatedWith(Discoverable.class);
        for(Class clazz:allClasses) {
            Discoverable d = getDiscoverableClass(clazz);
            if ( d==null ) {
                continue;
            }
            Map<String, Class> list = concreteInstances.get( d.interfaceClass() );
            if (list==null) {
                list = new HashMap<>();
                concreteInstances.put(d.interfaceClass(), list);
            }
            list.put(d.purpose(), clazz);
        }
        return concreteInstances;
    }

    private static Discoverable getDiscoverableClass(Class clazz) {
        if ( clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || clazz.isAnnotation() ){
            return null;
        }
        boolean hasDefaultConstructor = false;
        for(Constructor c: clazz.getConstructors()){
            if ( c.getParameterCount()==0 ){
                hasDefaultConstructor = true;
            }
        }
        if ( !hasDefaultConstructor ){
            return null;
        }
        return (Discoverable)clazz.getAnnotation(Discoverable.class);
    }

}
