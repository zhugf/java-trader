package trader.common.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.ConversionUtil;

public class PriceLevel {

    public static final String LEVEL_MIN  = "min";
    public static final String LEVEL_VOL  = "vol";

    public static final PriceLevel TICKET = new PriceLevel("tick", -1);
    public static final PriceLevel MIN1 = new PriceLevel(LEVEL_MIN, 1);
    public static final PriceLevel MIN3 = new PriceLevel(LEVEL_MIN, 3);
    public static final PriceLevel MIN5 = new PriceLevel(LEVEL_MIN, 5);
    public static final PriceLevel MIN15 = new PriceLevel(LEVEL_MIN, 15);
    public static final PriceLevel HOUR = new PriceLevel(LEVEL_MIN, 60);

    public static final PriceLevel VOL1K = new PriceLevel(LEVEL_VOL, 1000);
    public static final PriceLevel VOL3K = new PriceLevel(LEVEL_VOL, 3000);
    public static final PriceLevel VOL5K = new PriceLevel(LEVEL_VOL, 5000);
    public static final PriceLevel VOL10K = new PriceLevel(LEVEL_VOL, 10000);

    public static final PriceLevel DAY = new PriceLevel("day", -1);

    private static final Pattern PATTERN = Pattern.compile("(\\w+)(\\d?|\\d+k)");
    private static final Map<String, PriceLevel> levels = new HashMap<>();

    private String name;
    private int value;

    private PriceLevel(String levelPrefix, int levelValue){
        this.value = levelValue;
        if ( levelValue >0 ) {
            this.name = levelPrefix+levelValue;
        }else {
            this.name = levelPrefix;
        }
    }

    public String name() {
        return name;
    }

    public int getValue(){
        return value;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if ( o==null || !(o instanceof PriceLevel)) {
            return false;
        }
        PriceLevel l = (PriceLevel)o;

        return name.equals(l.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static PriceLevel valueOf(String str){
        str = str.trim().toLowerCase();
    	PriceLevel result = levels.get(str);
    	if ( result==null ) {
    	    Matcher matcher = PATTERN.matcher(str);
    	    if ( matcher.matches() ) {
    	        String prefix = matcher.group(1);
    	        int value = -1;
    	        try {
    	            int unit = 1;
    	            String vol = matcher.group(2).toLowerCase();
    	            if ( vol.endsWith("k") ) {
    	                unit = 1000;
    	                vol = vol.substring(0, vol.length()-1);
    	            }
    	            value = ConversionUtil.toInt(vol)*unit;
    	        }catch(Throwable t) {}
    	        result = new PriceLevel(prefix, value);
    	        levels.put(str, result);
    	    }
    	}
    	return result;
    }

    public static PriceLevel minute2level(int minute){
        switch(minute){
        case 1:
            return PriceLevel.MIN1;
        case 3:
            return PriceLevel.MIN3;
        case 5:
            return PriceLevel.MIN5;
        case 15:
            return PriceLevel.MIN15;
        case 60:
            return PriceLevel.HOUR;
        default:
            throw new RuntimeException("Unsupported minute level: "+minute);
        }
    }

}
