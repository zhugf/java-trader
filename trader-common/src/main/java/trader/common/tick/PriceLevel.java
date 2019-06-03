package trader.common.tick;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

public class PriceLevel {
    private static final Pattern PATTERN = Pattern.compile("([a-z]+)(\\d*)");
    private static final Map<String, PriceLevel> levels = new HashMap<>();

    public static final String LEVEL_MIN  = "min";
    public static final String LEVEL_VOL  = "vol";
    public static final String LEVEL_AMT  = "amt";
    public static final String LEVEL_TICK = "tick";

    public static final PriceLevel TICKET = new PriceLevel("tick", "tick", -1);
    public static final PriceLevel MIN1 = PriceLevel.valueOf(LEVEL_MIN+1);
    public static final PriceLevel MIN3 = PriceLevel.valueOf(LEVEL_MIN+3);
    public static final PriceLevel MIN5 = PriceLevel.valueOf(LEVEL_MIN+5);
    public static final PriceLevel MIN15 = PriceLevel.valueOf(LEVEL_MIN+15);
    public static final PriceLevel HOUR = PriceLevel.valueOf(LEVEL_MIN+60);

    public static final PriceLevel VOL1K = PriceLevel.valueOf(LEVEL_VOL+"1k");
    public static final PriceLevel VOL3K = PriceLevel.valueOf(LEVEL_VOL+"3k");
    public static final PriceLevel VOL5K = PriceLevel.valueOf(LEVEL_VOL+"5k");
    public static final PriceLevel VOL10K = PriceLevel.valueOf(LEVEL_VOL+"10k");
    public static final PriceLevel VOLDAILY = PriceLevel.valueOf(LEVEL_VOL+"Daily");

    public static final PriceLevel DAY = new PriceLevel("day", "day", -1);

    private String name;
    private String prefix;
    private int value;

    private PriceLevel(String name, String prefix, int levelValue){
        this.name = name;
        this.prefix = prefix;
        this.value = levelValue;
    }

    public String name() {
        return name;
    }

    public String prefix() {
        return prefix;
    }

    public int value(){
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

    public static PriceLevel valueOf(String level){
        level = level.trim().toLowerCase();
    	PriceLevel result = levels.get(level);
    	if ( result==null ) {
            int unit=1;
    	    if ( level.endsWith("k") ) {
                unit = 1000;
                level = level.substring(0, level.length()-1);
            }
    	    Matcher matcher = PATTERN.matcher(level);
    	    if ( matcher.matches() ) {
    	        String prefix = matcher.group(1);
    	        int value = -1;
    	        try {
    	            String vol = matcher.group(2).toLowerCase();
    	            if ( !StringUtil.isEmpty(vol)) {
    	                value = ConversionUtil.toInt(vol)*unit;
    	            }
    	        }catch(Throwable t) {}
    	        result = new PriceLevel(level, prefix, value);
    	        levels.put(level, result);
    	    } else {
    	        throw new RuntimeException("Unsupport price level: "+level);
    	    }
    	}
    	return result;
    }

    /**
     * 从当前持仓量/VolMultiplier构建当日Level
     *
     * @param openInt
     * @param volMultiplier 缺省为500
     * @return
     */
    public static PriceLevel resolveVolDaily(long openInt, int volMultiplier) {
        if ( volMultiplier<=0 ) {
            volMultiplier = 500;
        }
        PriceLevel result = PriceLevel.valueOf(PriceLevel.LEVEL_VOL+(openInt/volMultiplier));
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
