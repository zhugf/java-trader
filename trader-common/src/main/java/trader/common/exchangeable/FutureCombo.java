package trader.common.exchangeable;

import trader.common.util.StringUtil;

/**
 * 组合套利合约:
 * <BR>CZCE: SPD FG102&FG106 跨期套利-FG102/FG106, IPS FG102&SA102 跨品种套利-FG102/SA1
 * <BR>DCE: SP b2008&b2008
 */
public class FutureCombo extends Exchangeable {

    private Exchangeable e1;
    private Exchangeable e2;

    public FutureCombo(Exchange exchange, String comboId, String comboName) {
        super(exchange, comboId, comboName);
        int beginIdx = comboId.indexOf(' ');
        String[] ids = StringUtil.split(comboId.substring(beginIdx+1).trim(), "&");
        e1 = Future.fromString(ids[0]);
        e2 = Future.fromString(ids[1]);
    }

    public String contract() {
        return e1.contract();
    }

    public Exchangeable getExchangeable1() {
        return e1;
    }

    public Exchangeable getExchangeable2() {
        return e2;
    }

    static Exchange detectExchange(String comboId) {
        int beginIdx = comboId.indexOf(' ');
        int endIdx = comboId.indexOf('&');
        String e1 = comboId.substring(beginIdx+1, endIdx);
        return Future.detectExchange(e1);
    }

    public static boolean acceptId(String comboId) {
        comboId = comboId.toLowerCase().trim();
        boolean result = false;
        if ( comboId.indexOf('&')>0
             && ( false
                 //DCE
                 || comboId.startsWith("spc ")
                 || comboId.startsWith("sp ")
                 //CZCE
                 || comboId.startsWith("spd ")
                 || comboId.startsWith("ips "))
             )
        {
            result = true;
        }
        return result;
    }

}
