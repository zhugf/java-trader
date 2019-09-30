package trader.service;

import java.time.LocalDateTime;

import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.tradlet.Playbook;

public class ServiceAttribute {

    public static enum AttrType{
        Second
        ,DateTime
        ,Long
        ,Price
        ,String
        ,Boolean
    }

    private String name;
    private AttrType type;
    private String defaultValue;

    public ServiceAttribute(String name, AttrType type, String defaultValue) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public AttrType type() {
        return type;
    }

    public long getSecond(Object value) {
        value = internValue(value);
        String str = ConversionUtil.toString(value);
        if (StringUtil.isEmpty(str)) {
            str = defaultValue;
        }
        return ConversionUtil.str2seconds(str);
    }

    public long getPrice(Object value) {
        value = internValue(value);
        long result = PriceUtil.str2long(defaultValue);
        String str = ConversionUtil.toString(value);
        if (!StringUtil.isEmpty(str)) {
            result = PriceUtil.str2long(str);
        }
        return result;
    }

    public long getLong(Object value) {
        value = internValue(value);
        long result = ConversionUtil.toLong(defaultValue);
        String str = ConversionUtil.toString(value);
        if ( !StringUtil.isEmpty(str) ) {
            result = ConversionUtil.toLong(value);
        }
        return result;
    }

    public String getString(Object value) {
        value = internValue(value);
        return ConversionUtil.toString(value);
    }

    public LocalDateTime getDateTime(Object value) {
        value = internValue(value);
        LocalDateTime result = DateUtil.str2localdatetime(defaultValue);
        if ( value!=null ) {
            result = DateUtil.str2localdatetime(ConversionUtil.toString(value));
        }
        return result;
    }

    private Object internValue(Object value) {
        if ( value==null ) {
            value = null;
        }else if ( value instanceof Playbook) {
            value = ((Playbook)value).getAttr(name);
        }
        return value;
    }

}
