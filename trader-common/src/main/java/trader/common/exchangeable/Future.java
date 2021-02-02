package trader.common.exchangeable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

/**
 * 期货-商品
 */
public class Future extends Exchangeable {
    public static final Pattern PATTERN = Pattern.compile("([a-zA-Z]+)(\\d+)");
    protected String contract;
    /**
     * 正规后的合约月份, 格式: yymm
     */
    protected String deliveryDate;
    protected String canonicalDeliveryDate;
    protected long priceTick;
    protected int volumeMultiplier;

    public Future(Exchange exchange, String instrument) {
        this(exchange, instrument, instrument);
    }

    public Future(Exchange exchange, String instrument, String name) {
        super(exchange, canonicalizeInstrumentId(exchange, instrument), name);

        Matcher matcher = PATTERN.matcher(instrument);
        String deliveryDate0 = null;
        if ( matcher.matches() ) {
            contract = exchange.canonicalContract(matcher.group(1));
            deliveryDate0 = matcher.group(2);
        } else {
            contract = exchange.canonicalContract(instrument);
        }
        ExchangeContract exchangeContract = exchange.matchContract(contract);
        if ( exchangeContract ==null ) {
            throw new RuntimeException("Unknown future instrument "+instrument);
        }
        if ( null!=deliveryDate0 ) {
            switch(exchangeContract.getInstrumentFormat()) {
            case "YYMM":
                this.deliveryDate = deliveryDate0;
                this.canonicalDeliveryDate = deliveryDate0;
                break;
            case "YMM":
                if ( deliveryDate0.length()==3 ) {
                    //YMM -> YYMM
                    LocalDate currYear = LocalDate.now();
                    String yyyymmdd = DateUtil.date2str(currYear);
                    String y10 = yyyymmdd.substring(2, 3);
                    this.deliveryDate = deliveryDate0;
                    this.canonicalDeliveryDate = y10+deliveryDate0;
                    LocalDate date2 = DateUtil.str2localdate( yyyymmdd.substring(0,2)+canonicalDeliveryDate+"01");
                    if ( null!=date2 && currYear.plusYears(5).isBefore(date2)) {
                        yyyymmdd = DateUtil.date2str(currYear.plusYears(-10));
                        y10 = yyyymmdd.substring(2, 3);
                        this.canonicalDeliveryDate = y10+deliveryDate0;
                    }
                } else {
                    //YYMM -> YMM
                    this.deliveryDate = deliveryDate0.substring(1);
                    this.canonicalDeliveryDate = deliveryDate0;
                }
                break;
            }
            String id0 = this.id;
            this.id = contract+deliveryDate;
            if ( StringUtil.equals(id0, name) ) {
                name = this.id;
            }
        }

        priceTick = PriceUtil.price2long(exchangeContract.getPriceTick());
        volumeMultiplier = exchangeContract.getVolumeMultiplier();
    }

    /**
     * 合约名称
     */
    @Override
    public String contract() {
        return contract;
    }

    /**
     * 期货交割年月, 可以是 YYMM或YMM(CZCE)
     */
    public String getDeliveryDate() {
        return deliveryDate;
    }

    /**
     * 正规化后的期货交割年月, 始终是YYMM
     */
    public String getCanonicalDeliveryDate() {
        return canonicalDeliveryDate;
    }

    @Override
    public long getPriceTick() {
        return priceTick;
    }

    @Override
    public int getVolumeMutiplier() {
        return volumeMultiplier;
    }

    protected int compareId(Exchangeable o) {
        int result = 0;
        if ( o instanceof Future ) {
            Future f = (Future)o;
            result = contract().compareTo(f.contract());
            if ( 0==result ) {
                result = StringUtil.compareTo(getCanonicalDeliveryDate(), f.getCanonicalDeliveryDate());
            }
        } else {
            result = super.compareId(o);
        }
        return result;
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
            // Next month
            marketDay = marketDay.plusMonths(1);
            marketDay = marketDay.minusDays(marketDay.getDayOfMonth() - 1);
        }

        Set<Future> result = new TreeSet<>();
        // 当月
        String InstrumentThisMonth = instrumentId(contract, commodityName, marketDay);
        // 下月
        LocalDate ldt2 = marketDay.plus(1, ChronoUnit.MONTHS);
        String instrumentNextMonth = instrumentId(contract, commodityName,ldt2);
        // 下12月
        List<String> next12Months = instrumentsFromMonths(contract, commodityName, marketDay, Arrays.asList(new Integer[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
        List<String> next8In12Months = instrumentsFromMonths(contract, commodityName, marketDay, Arrays.asList(new Integer[] {1, 3, 5, 7, 8, 9, 11, 12}));
        List<String> next6OddMonths = instrumentsFromMonths(contract, commodityName, marketDay, Arrays.asList(new Integer[] {1, 3, 5, 7, 9, 11}));
        List<String> next1357Q4Months = instrumentsFromMonths(contract, commodityName, marketDay, Arrays.asList(new Integer[] {1, 3, 5, 7, 10, 11, 12}));
        List<String> next3And6BiMonths = new ArrayList<>();
        {
            LocalDate day2 = marketDay;
            for(int i=0;i<3;i++) {
                next3And6BiMonths.add( instrumentId(contract, commodityName, day2) );
                day2 = day2.plusMonths(1);
            }
            int biMonths = 0;
            while(biMonths<6) {
                int monthOfYear = day2.getMonth().get(ChronoField.MONTH_OF_YEAR);
                if ( monthOfYear%2 ==0 ) {
                    next3And6BiMonths.add( instrumentId(contract, commodityName, day2) );
                    biMonths++;
                }
                day2 = day2.plusMonths(1);
            }
        }
        //当月，下月和随后的两个季月
        Set<Future> thisMonth2AndNextQuarter2 = new TreeSet<>();
        {
            thisMonth2AndNextQuarter2.add(new Future(exchange, InstrumentThisMonth));
            Future nextMonth = new Future(exchange, instrumentNextMonth);
            thisMonth2AndNextQuarter2.add(nextMonth);
            LocalDate ldtq = ldt2.plusMonths(1);
            int quarters=0;
            while(true) {
                if ( isQuarterMonth(ldtq.getMonth()) ){
                    thisMonth2AndNextQuarter2.add(new Future(exchange, instrumentId(contract, commodityName, ldtq)));
                    quarters++;
                }
                ldtq = ldtq.plusMonths(1);
                if ( quarters>=2 ) {
                    break;
                }
            }
        }
        //最近的三个季月（3月、6月、9月、12月中的最近三个月循环）
        Set<Future> this3Quarter = new TreeSet<>();
        {
            int quarters=0;
            LocalDate ldtq = marketDay;
            while(true) {
                if ( isQuarterMonth(ldtq.getMonth()) ){
                    this3Quarter.add(new Future(exchange, instrumentId(contract, commodityName, ldtq)));
                    quarters++;
                }
                ldtq = ldtq.plusMonths(1);
                if ( quarters>=3 ) {
                    break;
                }
            }
        }
        // 当季
        int marketMonth = marketDay.getMonthValue();
        int thisQuarterMonth = ((marketMonth - 1) / 3 + 1) * 3;
        LocalDate ldt3 = marketDay.plus((thisQuarterMonth - marketMonth), ChronoUnit.MONTHS);
        String instrumentThisQuarter = instrumentId(contract, commodityName, ldt3);
        // 下季
        LocalDate ldt4 = ldt3.plus(3, ChronoUnit.MONTHS);
        String instrumentNextQuarter = instrumentId(contract, commodityName,ldt4);
        // 隔季
        LocalDate ldt5 = ldt4.plus(3, ChronoUnit.MONTHS);
        String instrumentNextQuarter2 = instrumentId(contract, commodityName, ldt5);
        // 12个月后的8个季度
        LocalDate ldt6 = ldt3.plus(12, ChronoUnit.MONTHS);
        List<String> next8QuartersAfter12Months = new ArrayList<>();
        while(true) {
            ldt6 = ldt6.plusMonths(1);
            if ( isQuarterMonth(ldt6.getMonth())) {
                next8QuartersAfter12Months.add( instrumentId(contract, commodityName, ldt6) );
            }
            if ( next8QuartersAfter12Months.size()>=8 ) {
                break;
            }
        }

        List<Integer> months = new ArrayList<>();
        for (String monthStr : contract.getMonths()) {
            switch (monthStr) {
            case "ThisMonth2AndNextQuarter2":
                result.addAll(thisMonth2AndNextQuarter2);
                break;
            case "This3Quarter":
                result.addAll(this3Quarter);
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
            case "Next12MonthsAnd8Quarters":
                for (String n : next12Months) {
                    result.add(new Future(exchange, n));
                }
                for (String n : next8QuartersAfter12Months) {
                    result.add(new Future(exchange, n));
                }
                break;
            case "Next3And6BiMonths":{
                for (String n : next3And6BiMonths) {
                    result.add(new Future(exchange, n));
                }
                break;
            }
            default:
                months.add(ConversionUtil.toInt(monthStr));
                break;
            }
        }
        if ( months.size()>0 ) {
            for(String n:instrumentsFromMonths(contract, commodityName, marketDay, months)) {
                result.add(new Future(exchange, n));
            }
        }
        return new ArrayList<>(result);
    }

    private static List<String> instrumentsFromMonths(ExchangeContract contract, String commodityName, LocalDate marketDay, List<Integer> months){
        List<Integer> monthList = new ArrayList<>();
        for(int m:months) {
            monthList.add(m);
        }
        List<String> result = new ArrayList<>();
        for(int i=0; i<=12;i++) {
            LocalDate month = marketDay.plus(i, ChronoUnit.MONTHS);
            if ( monthList.contains(month.getMonth().getValue())) {
                boolean afterLastTradingDay = false;
                if ( marketDay.equals(month) && contract.isAfterLastTradingDay(month)) {
                    afterLastTradingDay = true;
                }
                if ( !afterLastTradingDay ) {
                    result.add(instrumentId(contract, commodityName, month));
                }
            }
        }
        return result;
    }

    /**
     * 从月份生成
     */
    private static List<Future> genInstrumentFromMonths(Exchange exchange, ExchangeContract contract, String commodityName, LocalDate marketDay){
        List<Future> result = new ArrayList<>();

        if ( contract.getLastTradingDayOfMonth()>0 && marketDay.getDayOfMonth()<contract.getLastTradingDayOfMonth() ) {
            result.add(new Future(exchange, instrumentId(contract, commodityName, marketDay)));
        }
        List<String> months = new ArrayList<>();
        for(String instruments:contract.getMonths()) {
            String[] monthStrs = StringUtil.split(instruments, ",");
            for(String month:monthStrs) {
                months.add(month);
            }
        }
        for(int i=1;i<=12;i++) {
            LocalDate month = marketDay.plus(i, ChronoUnit.MONTHS);
            int marketMonth = month.getMonth().getValue();
            if ( months.contains(""+marketMonth)) {
                result.add(new Future(exchange, instrumentId(contract, commodityName, month)));
            }
            if ( result.size()>=months.size()) {
                break;
            }
        }
        return result;
    }

    private static String canonicalizeInstrumentId(Exchange exchange, String instrument) {
        String result = instrument;
        Matcher matcher = PATTERN.matcher(instrument);
        if ( matcher.matches() ) {
            String commodity = exchange.canonicalContract(matcher.group(1));
            String contract = matcher.group(2);
            result = commodity+contract;
        }
        return result;
    }

    private static String instrumentId(ExchangeContract exchange, String contract, LocalDate marketDay) {
        switch(exchange.getInstrumentFormat()) {
        case "YYMM":
            return (contract + DateUtil.date2str(marketDay).substring(2, 6));
        case "YMM":
            return (contract + DateUtil.date2str(marketDay).substring(3, 6));
        default:
            throw new RuntimeException("Exchange "+exchange+" contract "+contract+" unsupported format "+exchange.getInstrumentFormat());
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

    private static class PrimaryInstrument{
        Future instrument;
        LocalDate beginDate;
        LocalDate endDate;

        PrimaryInstrument(JsonObject json){
            this.instrument = (Future)Exchangeable.fromString(json.get("instrument").getAsString());
            String[] dateRange = StringUtil.split(json.get("dateRange").getAsString(), "-");
            beginDate = DateUtil.str2localdate(dateRange[0]);
            endDate = DateUtil.str2localdate(dateRange[1]);
        }
    }

    private static boolean isQuarterMonth(Month month) {
        switch(month) {
        case MARCH: //3
        case JUNE: //6
        case SEPTEMBER: //9
        case DECEMBER: //12
            return true;
        default:
            return false;
        }
    }
}
