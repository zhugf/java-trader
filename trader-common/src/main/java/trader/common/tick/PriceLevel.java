package trader.common.tick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

/**
 * KBar的级别, 格式为: Prefix+Value+Postfix.
 * <BR>Prefix有: MIN 分钟/DAY天/AMT 金额/VOL 量
 * <BR>Value是数量, 可以带K后缀, 如: 500, 1K
 * <BR>Prefix是后缀, 格式为: NVNVNV, NV可取范围有:
 * <LI>P(Percent): V,I
 * <LI>D(过去天数): [INT]
 * <P>样例:
 * <LI>MIN1,MIN5,MIN60
 * <LI>DAY1
 * <LI>VOL800, VOL1K
 * <LI>VOL1000PD20
 */
public class PriceLevel {
    private static final Pattern VALUE_PATTERN = Pattern.compile("(\\d+k?)(.*)");
    private static final Map<String, PriceLevel> levelByNames = new HashMap<>();

    public static final String LEVEL_MIN  = "min";
    public static final String LEVEL_VOL  = "vol";
    public static final String LEVEL_DAY  = "day";
    public static final String LEVEL_AMT  = "amt";
    public static final String LEVEL_TICK = "tick";
    private static final List<String> PREFIXS = Arrays.asList(new String[] {LEVEL_MIN, LEVEL_VOL, LEVEL_DAY, LEVEL_AMT, LEVEL_TICK});

    public static final PriceLevel MIN1 = PriceLevel.valueOf(LEVEL_MIN+1);
    public static final PriceLevel MIN3 = PriceLevel.valueOf(LEVEL_MIN+3);
    public static final PriceLevel MIN5 = PriceLevel.valueOf(LEVEL_MIN+5);
    public static final PriceLevel MIN15 = PriceLevel.valueOf(LEVEL_MIN+15);
    public static final PriceLevel MIN30 = PriceLevel.valueOf(LEVEL_MIN+30);
    public static final PriceLevel MIN60 = PriceLevel.valueOf(LEVEL_MIN+60);

    /**
     * 量K线: 每KBar绝对数值1K
     */
    public static final PriceLevel VOL1K = PriceLevel.valueOf(LEVEL_VOL+"1k");
    /**
     * 量K线: 每KBar绝对数值3K
     */
    public static final PriceLevel VOL3K = PriceLevel.valueOf(LEVEL_VOL+"3k");
    /**
     * 量K线: 每KBar绝对数值5K
     */
    public static final PriceLevel VOL5K = PriceLevel.valueOf(LEVEL_VOL+"5k");
    /**
     * 量K线: 每KBar绝对数值10K
     */
    public static final PriceLevel VOL10K = PriceLevel.valueOf(LEVEL_VOL+"10k");
    /**
     * 量K线: 每KBar当天成交量的1/1000
     */
    public static final PriceLevel VOLDAILY = new PriceLevel(LEVEL_VOL+"Daily", LEVEL_VOL, -1, null);
    /**
     * 量K线: 每KBar是过去主连20天的成交量的1/70
     */
    public static final PriceLevel VOL70PD20 = PriceLevel.valueOf(LEVEL_VOL+"70PVD20");

    /**
     * 值从百分比来
     */
    public static final String POSTFIX_PRIMARY = "p";

    /**
     * 天数
     */
    public static final String POSTFIX_DAY = "d";

    public static final String BY_VOL = "v";
    public static final String BY_OPENINT = "i";

    public static final PriceLevel TICKET = new PriceLevel("tick", "tick", -1, null);
    public static final PriceLevel DAY = new PriceLevel("day", "day", -1, null);

    @Deprecated
    public static final PriceLevel STROKE = new PriceLevel("stroke", "stroke", -1, null);
    @Deprecated
    public static final PriceLevel SECTION = new PriceLevel("section", "section", -1, null);

    private String name;
    private String prefix;
    private int value;
    private Map<String, String> postfixes = Collections.emptyMap();

    private PriceLevel(String name, String prefix, int levelValue, List<String> postfixs){
        this.name = name;
        this.prefix = prefix;
        this.value = levelValue;
        if ( null!=postfixs) {
            Map<String, String> map = new HashMap<>();
            int idx=0;
            while(idx<postfixs.size()) {
                String k = postfixs.get(idx++);
                String v = postfixs.get(idx++);
                map.put(k, v);
            }
            this.postfixes = Collections.unmodifiableMap(map);
        }
        if (name!=null) {
            levelByNames.put(name.toLowerCase(), this);
        }
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

    public Map<String, String> postfixes(){
        return postfixes;
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
        PriceLevel result = null;
        level = level.trim().toLowerCase();
        result = levelByNames.get(level.trim().toLowerCase());
    	if ( result!=null ) {
    	    return result;
    	}
        String level0 = level;
        int value = -1;
        List<String> kvs = new ArrayList<>();
        //PREFIX
        String prefix = null;
        for(String prefix0:PREFIXS) {
            if (level0.startsWith(prefix0)) {
                prefix = prefix0;
                level0 = level0.substring(prefix0.length());
                break;
            }
        }
        if ( prefix==null ) {
            throw new RuntimeException("Unknown prefix for price level "+level);
        }
        //VALUE
	    Matcher matcher = VALUE_PATTERN.matcher(level0);
	    if ( matcher.matches() ) {
	        String value0 = matcher.group(1);
	        int unit=1;
	        if ( value0.endsWith("k")) {
                unit = 1000;
                value0 = value0.substring(0, value0.length()-1);
	        }
	        value = ConversionUtil.toInt(value0)*unit;

	        String postfix = matcher.group(2);
	        for(int i=0;i<postfix.length();i++) {
	            char c = postfix.charAt(i);
	            if ( c>='0' && c<='9') {
	                StringBuilder digits = new StringBuilder();
	                digits.append(c);
	                i++;
	                while(i<postfix.length()) {
	                    char c2 = postfix.charAt(i);
	                    if ( c2>='0' && c2<='9') {
	                        digits.append(c2);
	                        i++;
	                        continue;
	                    }
	                    break;
	                }
	                kvs.add(digits.toString());
	            } else {
	                kvs.add(""+c);
	            }
	        }
	    } else {
	        throw new RuntimeException("Unknown value and postfix part for price level "+level);
	    }
	    result = new PriceLevel(level, prefix, value, kvs);
    	return result;
    }

    /**
     * 从当前持仓量/VolMultiplier构建当日Level
     *
     * @param openInt
     * @param volMultiplier 缺省为500
     * @return
     */
    public static PriceLevel resolveVolDaily(long volume, int volMultiplier) {
        if ( volMultiplier<=0 ) {
            volMultiplier = 500;
        }
        PriceLevel result = PriceLevel.valueOf(PriceLevel.LEVEL_VOL+(volume/volMultiplier));
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
        case 30:
            return PriceLevel.MIN30;
        case 60:
            return PriceLevel.MIN60;
        default:
            throw new RuntimeException("Unsupported minute level: "+minute);
        }
    }

}
