package trader.tool;

import java.util.ArrayList;
import java.util.List;

import trader.common.util.StringUtil;

public class MainHelper {

    public static String[] preprocessArgs(String[] args) {
        List<String> result = new ArrayList<>();
        for(String arg:args) {
            if ( arg.startsWith("-D")) {
                List<String[]> kvs = StringUtil.splitKVs(arg.substring(2));
                for(String[] kv:kvs) {
                    System.setProperty(kv[0], kv[1]);
                }
                continue;
            }
            result.add(arg);
        }
        return result.toArray(new String[result.size()]);
    }

}
