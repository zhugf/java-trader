package trader.common.exchangeable;

import trader.common.util.StringUtil;

public class Option extends Exchangeable {

    private Exchangeable target;
    private OptionType optionType;
    private String optionValue;

    public Option(Exchange exchange, String id, String name) {
        super(exchange, id, name);
        String[] parts=StringUtil.split(id, "-");
        target = Exchangeable.fromString(parts[0]);
        if ( StringUtil.equalsIgnoreCase("p", parts[1])) {
            optionType = OptionType.Put;
        } else if ( StringUtil.equalsIgnoreCase("c", parts[1])) {
            optionType = OptionType.Call;
        }
        optionValue = parts[2];
    }

    public Exchangeable OptionTarget() {
        return target;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public String getOptionValue() {
        return optionValue;
    }

    static Exchange detectExchange(String optionId) {
        int minusIdx = optionId.indexOf('-');
        String instrument = optionId.substring(0, minusIdx);
        return Future.detectExchange(instrument);
    }

    public static boolean acceptId(String optionId) {
        boolean result = false;
        if ( (optionId.indexOf("-P-")>0 || optionId.indexOf("-C-")>0) && optionId.indexOf("&")<0 ) {
            result = true;
        }
        return result;
    }
}
