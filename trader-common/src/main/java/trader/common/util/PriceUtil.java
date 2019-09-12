package trader.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceUtil {
    public static final String MAX_STR = "N/A";

    public static final long PRICE_SCALE = 10000;

    /**
     * 转换价格表示字符串到long值, 支持 4t, 3t格式.
     */
    public static long config2long(String str, long priceTick) {
        long result = 0;
        if ( str.toLowerCase().endsWith("t") ) {
            result = ConversionUtil.toInt(str.substring(0, str.length() - 1)) * priceTick;
        } else {
            result = price2long(ConversionUtil.toDouble(str));
        }
        return result;
    }

    /**
     * 是否合适的价格范围
     */
    public static boolean isValidPrice(long price) {
        return price>0 && price<Long.MAX_VALUE;
    }

    /**
     * 是否合适的价格范围
     */
    public static boolean isValidPrice(double price) {
        return price!=0 && price!=Double.MAX_VALUE;
    }

    public static double double2price(double value) {
        return long2price(price2long(value));
    }

    /**
     * 转换double类型价格为4位小数的long
     */
    public static long price2long(double price){
        if ( Double.MAX_VALUE == price ){
            return Long.MAX_VALUE;
        }
        long l = (long)(price*100000);
        if ( l%10 == 9) {
            l +=1;
        }
        l = l/10;

        return l;
    }

    public static double long2price(long l){
        if ( l==Long.MAX_VALUE ) {
            return Double.MAX_VALUE;
        }
        return ((double)l)/PRICE_SCALE;
    }

    public static long str2long(String price){
        if (price==null||price.length()==0 ) {
            return 0;
        }
        if ( MAX_STR.equals(price)) {
            return Long.MAX_VALUE;
        }
        return price2long(Double.parseDouble(price));
    }

    public static double str2price(String priceStr){
        if ( MAX_STR.equals(priceStr)) {
            return Double.MAX_VALUE;
        }
        long l = str2long(priceStr);
        return Double.parseDouble(priceStr);
    }

    public static String price2str(double price){
        return long2str(price2long(price));
    }

    public static String long2str(long pl){
        if ( pl == Long.MAX_VALUE ) {
            return MAX_STR;
        }
        StringBuilder builder = new StringBuilder(24);
        if ( pl<0 ){
            builder.append("-");
            pl = -1*pl;
        }
        builder.append( pl/PRICE_SCALE );
        builder.append(".");
        int ps = (int)Math.abs(pl%PRICE_SCALE);
        if ( ps==0 ){
            builder.append("0000");
        }else if ( ps<10 ){
            builder.append("000");
            builder.append((char)('0'+ps));
        }else if ( ps<100 ){
            builder.append("00");
            builder.append(ps);
        }else if ( ps<1000 ){
            builder.append("0");
            builder.append(ps);
        }else{
            assert(ps>=1000);
            builder.append(Integer.toString(ps));
        }
        {
            int strlen = builder.length();
            if ( builder.charAt(strlen-1)=='0' ){
                builder.setLength(strlen-1);
                strlen--;
            }
            if ( builder.charAt(strlen-1)=='0' ){
                builder.setLength(strlen-1);
                strlen--;
            }
        }
        return builder.toString();
    }


    public static String long2str(long p, int scale){
        if ( p==Long.MAX_VALUE ) {
            return "N/A";
        }
        /*
        StringBuilder text = new StringBuilder(32);
        if ( p<0 ){
            text.append("-");
            p = -1*p;
        }
        text.append(p/10000);
        long f = p%10000;
        if ( f!=0 ){
            String s = ""+f;
            int slen = s.length();
            switch(scale){
            case 0:
                break;
            case 1:
                text.append(".").append(s.substring(0, Math.min(1, s.length())));
                break;
            case 2:
                text.append(".").append(s.substring(0, Math.min(2, s.length())));
                break;
            case 3:
                text.append(".").append(s.substring(0, Math.min(3, s.length())));
                break;
            case 4:
                text.append(".").append(s.substring(0, Math.min(4, s.length())));
                break;
            default:
                throw new RuntimeException("Invalid scale for price "+p);
            }
        }
        return text.toString();
         */
        BigDecimal bd = new BigDecimal(p);
        bd = bd.divide(new BigDecimal(PRICE_SCALE)).setScale(scale,RoundingMode.HALF_UP);
        return bd.toString();
    }

    /**
     * 价格四舍五入到分
     */
    public static long round(long price) {
        return ((price+50)/100)*100;
    }

}
