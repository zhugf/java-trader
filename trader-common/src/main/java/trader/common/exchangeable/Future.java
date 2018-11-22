package trader.common.exchangeable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.DateUtil;

/**
 * 期货-商品
 */
public class Future extends Exchangeable {
    public static final Pattern PATTERN = Pattern.compile("([a-zA-Z]+)(\\d+)");
    protected String commodity;
    protected String contract;

    public Future(Exchange exchange, String instrument) {
        this(exchange, instrument, instrument);
    }

    public Future(Exchange exchange, String instrument, String name) {
        super(exchange, instrument, name);

        Matcher matcher = PATTERN.matcher(instrument);
        if ( matcher.matches() ) {
            commodity = matcher.group(1);
            contract = matcher.group(2);
        }else {
            commodity = instrument;
        }
    }

    /**
     * 品种名称
     */
    @Override
    public String commodity() {
        return commodity;
    }

    /**
     * 合约名称
     */
    public String contract() {
        return contract;
    }

    public static Future fromInstrument(String uniqueId) {
        return new Future(detectExchange(uniqueId), uniqueId);
    }

    public static Exchange detectExchange(String instrument) {
        instrument = instrument.toUpperCase();
        String commodity;
        if (instrument.indexOf(".") > 0) {
            String exchangeName = instrument.substring(0, instrument.indexOf("."));
            return Exchange.getInstance(exchangeName);
        }
        if (instrument.length() <= 2) {
            commodity = instrument;
        } else if (instrument.length() == 5) {
            char c = instrument.charAt(1);
            if ( c>='0' && c<='9' ){
                commodity = instrument.substring(0, 1);
            }else{
                commodity = instrument.substring(0, 2);
            }
        } else if (instrument.length() == 6) {
            commodity = instrument.substring(0, 2);
        } else {
            throw new RuntimeException("Unknown instrument " + instrument);
        }
        switch (commodity.toUpperCase()) {
        case "IF": // HS300
        case "IH": // 上证50
        case "IC": // 中证500
        case "TF": // 5年期国债
        case "T": // 10年期国债
            return Exchange.CFFEX;
        case "SC": //原油
            return Exchange.INE;
        case "CU":
        case "AL":
        case "ZN":
        case "PB":
        case "AU":
        case "AG":
        case "RB":
        case "WR":
        case "NI":
        case "HC":
        case "FU":
        case "BU":
        case "RU":
        case "SN":
            return Exchange.SHFE;
        case "A": //黄大豆一号
        case "B": //黄大豆二号
        case "BB": //胶合板
        case "C": //玉米
        case "CS": //玉米淀粉
        case "M": //豆粕
        case "J":
        case "JD":
        case "JM": //焦煤
        case "I": //铁矿石
        case "FB": //纤维板
        case "L": //聚乙烯
        case "P": //棕榈油
        case "PP": //聚丙烯
        case "V": //聚氯乙烯
        case "Y": //豆油
            return Exchange.DCE;
        case "SR":
        case "ME":
        case "MA": //甲醇
        case "CF":
        case "FG":
        case "SF":
        case "JR":
        case "LR":
        case "OI": //菜籽油
        case "PM": //普通小麦(普麦)
        case "RI": //早籼稻
        case "RM": //菜粕
        case "RS": //菜籽
        case "SM": //锰硅
        case "TA": //PTA-
        case "ZC": //动力煤
        case "WH": //强麦
        case "TC": //动力煤
        case "CY": //棉纱
        case "AP": //苹果
            return Exchange.CZCE;
        }
        throw new RuntimeException("Unable to detect exchange from instrument " + instrument);
    }

    /**
     * 根据交易日判断当天的期货合约
     */
    public static List<Future> instrumentsFromMarketDay(LocalDate marketDay, String commodityName) {
        Exchange exchange = detectExchange(commodityName);
        if ( commodityName.indexOf(".")>0) {
            commodityName = commodityName.substring(commodityName.indexOf(".")+1);
        }
        ExchangeContract contract = ExchangeContract.matchContract(exchange, commodityName);
        if ( contract==null ) {
            throw new RuntimeException("No exchange contract info for "+ exchange + "." + commodityName);
        }
        if (!MarketDayUtil.isMarketDay(exchange, marketDay)) {
            marketDay = MarketDayUtil.prevMarketDay(exchange, marketDay);
        }

        DayOfWeek dayOfWeek = marketDay.getDayOfWeek();
        int weekOfMonth = marketDay.get(WeekFields.of(DayOfWeek.SUNDAY, 2).weekOfMonth());
        if ( contract.getLastTradingWeekOfMonth()>0 && ( weekOfMonth > contract.getLastTradingWeekOfMonth() ||
                (weekOfMonth == contract.getLastTradingWeekOfMonth() && dayOfWeek.getValue() > contract.getLastTradingDayOfWeek().getValue()) ) )
                {
            // Next month
            marketDay = marketDay.plusMonths(1);
            marketDay = marketDay.minusDays(marketDay.getDayOfMonth() - 1);
        }
        if ( contract.getLastTradingDayOfMonth()>0 && marketDay.getDayOfMonth()>=contract.getLastTradingDayOfMonth() ) {

        }

        List<Future> result = new ArrayList<>();
        // 当月
        String InstrumentThisMonth =instrumentId(contract, commodityName,marketDay);
        // 下月
        LocalDate ldt2 = marketDay.plus(1, ChronoUnit.MONTHS);
        String instrumentNextMonth = instrumentId(contract, commodityName,ldt2);
        // 下12月
        List<String> next12Months = new ArrayList<>(12);
        for (int i = 0; i <= 12; i++) {
            LocalDate ldtn = marketDay.plus(i, ChronoUnit.MONTHS);
            String instrumentNextMonthN = instrumentId(contract, commodityName,ldtn);
            next12Months.add(instrumentNextMonthN);
        }
        // 1,3,5,7,8,9,11,12
        List<String> next8In12Months = new ArrayList<>();
        {
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(1, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(3, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(5, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(7, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(8, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(9, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(11, ChronoUnit.MONTHS)));
            next8In12Months
            .add(instrumentId(contract, commodityName, marketDay.plus(12, ChronoUnit.MONTHS)));
        }
        List<String> next6OddMonths = new ArrayList<>();
        {
            int marketMonth = marketDay.getMonth().getValue();
            int monthAdjust = 0;
            if (marketMonth%2==0){
                monthAdjust=1;
            }
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust, ChronoUnit.MONTHS)));
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust+2, ChronoUnit.MONTHS)));
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust+4, ChronoUnit.MONTHS)));
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust+6, ChronoUnit.MONTHS)));
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust+8, ChronoUnit.MONTHS)));
            next6OddMonths
            .add(instrumentId(contract, commodityName, marketDay.plus(monthAdjust+10, ChronoUnit.MONTHS)));
        }
        List<String> next1357Q4Months = new ArrayList<>();
        {
            for(int i=1;i<=12;i++) {
                LocalDate month = marketDay.plus(i, ChronoUnit.MONTHS);
                int marketMonth = month.getMonth().getValue();
                switch(marketMonth) {
                case 1:
                case 3:
                case 5:
                case 7:
                case 10:
                case 11:
                case 12:
                    next1357Q4Months.add(instrumentId(contract, commodityName, month));
                    break;
                }
            }
        }
        // 当季
        int month = marketDay.getMonthValue();
        int thisQuarterMonth = ((month - 1) / 3 + 1) * 3;
        LocalDate ldt3 = marketDay.plus((thisQuarterMonth - month), ChronoUnit.MONTHS);
        String instrumentThisQuarter = instrumentId(contract, commodityName, ldt3);
        // 下季
        LocalDate ldt4 = ldt3.plus(3, ChronoUnit.MONTHS);
        String instrumentNextQuarter = instrumentId(contract, commodityName,ldt4);
        // 隔季
        LocalDate ldt5 = ldt4.plus(3, ChronoUnit.MONTHS);
        String instrumentNextQuarter2 = instrumentId(contract, commodityName,ldt5);

        for (String instrument : contract.getInstruments()) {
            switch (instrument) {
            case "ThisMonth":
                result.add(new Future(exchange, InstrumentThisMonth));
                break;
            case "NextMonth":
                result.add(new Future(exchange, instrumentNextMonth));
                break;
            case "ThisQuarter":
                result.add(new Future(exchange, instrumentThisQuarter));
                break;
            case "NextQuarter":
                result.add(new Future(exchange, instrumentNextQuarter));
                break;
            case "NextQuarter2":
                result.add(new Future(exchange, instrumentNextQuarter2));
                break;
            case "Next12Months":
                for (String n : next12Months) {
                    result.add(new Future(exchange, n));
                }
                break;
            case "Next8In12Months":
                for (String n : next8In12Months) {
                    result.add(new Future(exchange, n));
                }
                break;
            case "Next6OddMonths":
                for (String n : next6OddMonths) {
                    result.add(new Future(exchange, n));
                }
                break;
            case "Next1357Q4Months":
                for (String n : next1357Q4Months) {
                    result.add(new Future(exchange, n));
                }
                break;
            default:
                throw new RuntimeException("Unsupported commodity name: " + commodityName);
            }
        }
        return result;
    }

    private static String instrumentId(ExchangeContract contract, String commodityName, LocalDate marketDay) {
        switch(contract.getInstrumentFormat()) {
        case "YYMM":
            return (commodityName + DateUtil.date2str(marketDay).substring(2, 6));
        case "YMM":
            return (commodityName + DateUtil.date2str(marketDay).substring(3, 6));
        default:
            throw new RuntimeException("Commodity "+commodityName+" unsupported format "+marketDay);
        }
    }

    /**
     * 根据交易日构建所有的期货品种
     */
    public static List<Future> buildAllInstruments(LocalDate marketDay){
        List<Future> allExchangeables = new ArrayList<>(100);
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "IF") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "IH") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "IC") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "T") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "TF") );

        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "sc") );

        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "cu") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "al") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "zn") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "pb") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "au") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "ag") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "rb") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "wr") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "ni") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "hc") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "fu") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "ru") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "bu") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "sn") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "zn") );

        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "a") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "b") );
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "BB") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "c") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "cs") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "m") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "i") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "j") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "jd") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "jm") );
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "fb") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "l") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "p") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "pp") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "v") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "y") );
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "SR") ); //白糖
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "ME"));
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "MA")); //甲醇
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "CF"));
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "FG")); //玻璃
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "SF")); //硅铁
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "JR")); //粳稻
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "LR"));
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "OI")); //菜籽油（简称“菜油”）
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "PM"));
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "RI"));
//        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(),  "RM"));
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "RS")); //油菜籽
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "SM")); //锰硅
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "TA")); //PTA
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "ZC")); //动力煤
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "WH")); //强麦
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "CY")); //棉纱
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "CF")); //棉花
        allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), "AP")); //苹果

        return allExchangeables;
    }
}
