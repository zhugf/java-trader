package trader.common.exchangeable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.StringUtil;

public class Option extends Exchangeable {
    public static final Pattern PATTERN = Pattern.compile("([a-zA-Z]+)(\\d{3,4})(-?[P|C]-?)(\\d{2,})");

    private Exchangeable target;
    private OptionType optionType;
    private String optionValue;

    public Option(Exchange exchange, String id, String name) {
        super(exchange, id, name);
        this.type = ExchangeableType.OPTION;
        String[] parts = split(id);
        if ( null==parts ) {
            throw new RuntimeException("Unsupported option instrument "+id);
        }
        target = Exchangeable.fromString(exchange.name(), parts[0]);
        if ( parts[1].toLowerCase().indexOf("p")>=0) {
            optionType = OptionType.Put;
        } else if ( parts[1].toLowerCase().indexOf("c")>=0 ) {
            optionType = OptionType.Call;
        }
        optionValue = parts[2];
    }

    public Exchangeable getOptionTarget() {
        return target;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public String getOptionValue() {
        return optionValue;
    }

    static Exchange detectExchange(String optionId) {
        String[] parts = split(optionId);
        if ( parts!=null ) {
            return Future.detectExchange(parts[0]);
        } else {
            throw new RuntimeException("Unsupported option instrument "+optionId);
        }
    }

    public static boolean acceptId(String optionId) {
        return split(optionId)!=null;
    }

    private static String[] split(String optionId) {
        String[] result = null;
        Matcher m = PATTERN.matcher(optionId);
        if (m.matches()) {
            String contract = m.group(1);
            String deliveryDate = m.group(2);
            String PoC = m.group(3);
            String targetValue = m.group(4);
            result = new String[] {m.group(1)+m.group(2), m.group(3), m.group(4)};
        }
        return result;
    }
}
