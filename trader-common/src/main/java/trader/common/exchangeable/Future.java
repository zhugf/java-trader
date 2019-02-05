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
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

/**
 * 期货-商品
 */
public class Future extends Exchangeable {
    public static final Pattern PATTERN = Pattern.compile("([a-zA-Z]+)(\\d+)");
    protected String commodity;
    protected String contract;
    protected long priceTick;

    public Future(Exchange exchange, String instrument) {
        this(exchange, instrument, instrument);
    }

    public Future(Exchange exchange, String instrument, String name) {
        super(exchange, canonicalizeInstrumentId(exchange, instrument), name);

        Matcher matcher = PATTERN.matcher(instrument);
        if ( matcher.matches() ) {
            commodity = exchange.detectCommodity(matcher.group(1));
            contract = matcher.group(2);
            //commodity大小写有变化, 修改id/name
            String newId = commodity+contract;
            boolean changeName = StringUtil.equals(id, name);
            id = newId;
            if ( changeName ) {
                name = newId;
            }
        }else {
            commodity = exchange.detectCommodity(instrument);
        }
        ExchangeContract exchangeContract = exchange.matchContract(commodity);
        priceTick = PriceUtil.price2long(exchangeContract.getPriceTick());
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

    @Override
    public long getPriceTick() {
        return priceTick;
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
        Exchange exchange = ExchangeContract.detectContract(commodity);
        if ( exchange==null ) {
            throw new RuntimeException("Unable to detect exchange from instrument " + instrument);
        }
        return exchange;
    }

    /**
     * 根据交易日判断当天的期货合约
     */
    public static List<Future> instrumentsFromMarketDay(LocalDate marketDay, String commodityName) {
        Exchange exchange = detectExchange(commodityName);
        if ( commodityName.indexOf(".")>0) {
            commodityName = commodityName.substring(commodityName.indexOf(".")+1);
        }
        ExchangeContract contract = exchange.matchContract(commodityName);
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

    private static String canonicalizeInstrumentId(Exchange exchange, String instrument) {
        String result = instrument;
        Matcher matcher = PATTERN.matcher(instrument);
        if ( matcher.matches() ) {
            String commodity = exchange.detectCommodity(matcher.group(1));
            String contract = matcher.group(2);
            result = commodity+contract;
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
        for(Exchange exchange:Exchange.getInstances()) {
            if ( exchange.isFuture()) {
                for(String commodity: exchange.getContractNames()) {
                    allExchangeables.addAll( Future.instrumentsFromMarketDay(LocalDate.now(), commodity) );
                }
            }
        }
        return allExchangeables;
    }
}
