package trader.common.util;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CollectionUtil {

    public static Properties map2props(Map map){
        if ( map==null ){
            return null;
        }
        Properties props = new Properties();
        for(Object key: map.keySet()){
            props.setProperty(key.toString(), map.get(key).toString());
        }
        return props;
    }

    public static <T extends Object> void moveAllAfter(List<T> list1,T t, boolean includeT, List<T> list2){
        int idx = list1.lastIndexOf(t);
        if ( idx<0 ) {
            return;
        }
        List<T> sublist = null;
        if ( includeT ){
            sublist = list1.subList(idx, list1.size());
        }else{
            sublist = list1.subList(idx+1, list1.size());
        }
        list2.addAll(sublist);
        sublist.clear();
    }

    public static <T extends Object> void moveAllAfter(List<T> list1,T t, List<T> list2){
        moveAllAfter(list1,t,false,list2);
    }

}
