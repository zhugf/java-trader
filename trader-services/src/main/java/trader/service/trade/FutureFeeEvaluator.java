package trader.service.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;

/**
 * 期货保证金占用, 手续费计算
 */
public class FutureFeeEvaluator implements TxnFeeEvaluator, TradeConstants {
    private final static Logger logger = LoggerFactory.getLogger(FutureFeeEvaluator.class);

    public static class FutureFeeInfo implements JsonEnabled {
        private long priceTick;
        private int volumeMultiple;
        private double[] marginRatios = new double[MarginRatio.values().length];
        private double[] commissionRatios = new double[CommissionRatio.values().length];

        public long getPriceTick() {
            return priceTick;
        }

        public int getVolumeMultiple() {
            return volumeMultiple;
        }

        /**
         * @see TradeConstants#MarginRatio.LongByMoney
         * @see TradeConstants#MarginRatio.LongByVolume
         * @see TradeConstants#MarginRatio.ShortByMoney
         * @see TradeConstants#MarginRatio.ShortByVolume
         */
        public double getMarginRatio(int idx) {
            return marginRatios[idx];
        }

        /**
         * @see TradeConstants#CommissionRatio.OpenByMoney
         * @see TradeConstants#CommissionRatio.OpenByVolume
         * @see TradeConstants#CommissionRatio.CloseByMoney
         * @see TradeConstants#CommissionRatio.CloseByVolume
         * @see TradeConstants#CommissionRatio.CloseTodayByMoney
         * @see TradeConstants#CommissionRatio.CloseTodayByVolume
         */
        public double getCommissionRatio(int idx) {
            return commissionRatios[idx];
        }

        public void setPriceTick(long v) {
            this.priceTick = v;
        }

        public void setVolumeMultiple(int v) {
            this.volumeMultiple = v;
        }

        public void setMarginRatio(int idx, double v) {
            marginRatios[idx] = v;
        }

        public void setCommissionRatio(int idx, double v) {
            commissionRatios[idx] = v;
        }

        @Override
        public JsonElement toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("priceTick", PriceUtil.long2str(priceTick));
            json.addProperty("volumeMultiple", volumeMultiple);
            json.add("marginRatios", JsonUtil.object2json(marginRatios));
            json.add("commissionRatios", JsonUtil.object2json(commissionRatios));
            return json;
        }

        public static FutureFeeInfo fromJson(double brokerMarginRatio, JsonObject json) {
            if ( !json.has("commissionRatios") || !json.has("marginRatios") ) {
                return null;
            }
            FutureFeeInfo result = new FutureFeeInfo();
            result.priceTick = PriceUtil.str2long(json.get("priceTick").getAsString());
            result.volumeMultiple = ConversionUtil.toInt(json.get("volumeMultiple").getAsString());
            {
                JsonArray array = (JsonArray)json.get("marginRatios");
                result.marginRatios = new double[array.size()];
                for(int i=0;i<array.size();i++) {
                    result.marginRatios[i] = ConversionUtil.toDouble( array.get(i) );
                    //只有原始保证金比率不为0, 才会用新的保证金比率替换
                    if ( result.marginRatios[i]!=0 && brokerMarginRatio!=0 ) {
                        result.marginRatios[i] = brokerMarginRatio;
                    }
                }
            }
            {
                JsonArray array = (JsonArray)json.get("commissionRatios");
                result.commissionRatios = new double[array.size()];
                for(int i=0;i<array.size();i++) {
                    result.commissionRatios[i] = ConversionUtil.toDouble( array.get(i) );
                }
            }
            return result;
        }

    }

    private Map<Exchangeable, FutureFeeInfo> feeInfos;
    private Properties brokerMarginRatio;

    public FutureFeeEvaluator(Properties brokerMarginRatio, Map<Exchangeable, FutureFeeInfo> feeInfos)
    {
        this.brokerMarginRatio = brokerMarginRatio;
        this.feeInfos = feeInfos;
        for(Exchangeable e:feeInfos.keySet()) {
            long feePriceTick = feeInfos.get(e).priceTick;
            if ( e.getPriceTick()!= feePriceTick)  {
                logger.error("Exchangeable "+e+" priceTick "+PriceUtil.long2str(e.getPriceTick())+" is WRONG, expected value is "+PriceUtil.long2str(feePriceTick));
            }
            long feeVolumeMutiplier = feeInfos.get(e).volumeMultiple;
            if ( e.getVolumeMutiplier()!=feeVolumeMutiplier) {
                logger.error("Exchangeable "+e+" volumeMultiple "+(e.getVolumeMutiplier())+" is WRONG, expected value is "+feeVolumeMutiplier);
            }
        }
    }

    @Override
    public Collection<Exchangeable> getInstruments(){
        return feeInfos.keySet();
    }

    public Properties getBrokerMarginRatio() {
        return this.brokerMarginRatio;
    }

    @Override
    public long getPriceTick(Exchangeable e) {
        FutureFeeInfo feeInfo = feeInfos.get(e);
        long result = 0;
        if ( feeInfo!=null ) {
            result = feeInfo.getPriceTick();
        }
        return result;
    }

    @Override
    public long[] compute(Transaction txn) {
        return compute(txn.getInstrument(), txn.getVolume(), txn.getPrice(), txn.getDirection(), txn.getOffsetFlags());
    }

    @Override
    public long[] compute(Exchangeable e, int volume, long price, OrderDirection direction, OrderOffsetFlag offsetFlag) {
        FutureFeeInfo feeInfo = feeInfos.get(e);
        if ( feeInfo==null ) {
            logger.error("No fee info for "+e);
            return null;
        }
        long turnover = volume*price*feeInfo.getVolumeMultiple();
        long margin=0, commission=0;
        {//保证金
            if ( direction==OrderDirection.Buy ) {
                double longByMoney = feeInfo.marginRatios[MarginRatio.LongByMoney.ordinal()];
                double longByVolume = feeInfo.marginRatios[MarginRatio.LongByVolume.ordinal()];
                long marginByMoney = (long)(longByMoney*turnover);
                margin = marginByMoney;
            }else {
                double shortByMoney = feeInfo.marginRatios[MarginRatio.ShortByMoney.ordinal()];
                double shortByVolume = feeInfo.marginRatios[MarginRatio.ShortByVolume.ordinal()];
                long marginByMoney = (long)(shortByMoney*turnover);
                margin = marginByMoney;
            }
        }
        {//手续费
            switch(offsetFlag) {
            case OPEN:
                long openMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio.OpenByMoney.ordinal()] );
                long openVolume = PriceUtil.price2long( volume*feeInfo.commissionRatios[CommissionRatio.OpenByVolume.ordinal()] );
                commission = (openMoney+openVolume);
                break;
            case CLOSE:
            case CLOSE_YESTERDAY:
            case FORCE_CLOSE:
                long closeMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio.CloseByMoney.ordinal()] );
                long closeVolume = PriceUtil.price2long( volume*feeInfo.commissionRatios[CommissionRatio.CloseByVolume.ordinal()] );
                commission = closeMoney+closeVolume;
                break;
            case CLOSE_TODAY:
                long closeTodayMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio.CloseTodayByMoney.ordinal()] );
                long closeTodayVolume = PriceUtil.price2long( volume*feeInfo.commissionRatios[CommissionRatio.CloseTodayByVolume.ordinal()] );
                commission = closeTodayMoney+closeTodayVolume;
                break;
            }
        }
        margin = PriceUtil.round(margin);
        commission = PriceUtil.round(commission);
        turnover = PriceUtil.round(turnover);
        return new long[] {margin, commission, turnover};
    }

    @Override
    public long[] compute(Exchangeable e, int volume, long price, PosDirection direction){
        FutureFeeInfo feeInfo = feeInfos.get(e);
        if ( feeInfo==null ) {
            return null;
        }
        long turnover = volume*price*feeInfo.getVolumeMultiple();
        long margin = 0;
        switch(direction) {
        case Long:
            {
                double longByMoney = feeInfo.marginRatios[MarginRatio.LongByMoney.ordinal()];
                double longByVolume = feeInfo.marginRatios[MarginRatio.LongByVolume.ordinal()];
                long marginByMoney = (long)(longByMoney*turnover);
                margin = marginByMoney;
                break;
            }
        default:
            {
                double shortByMoney = feeInfo.marginRatios[MarginRatio.ShortByMoney.ordinal()];
                double shortByVolume = feeInfo.marginRatios[MarginRatio.ShortByVolume.ordinal()];
                long marginByMoney = (long)(shortByMoney*turnover);
                margin = marginByMoney;
                break;
            }
        }
        margin = PriceUtil.round(margin);
        turnover = PriceUtil.round(turnover);
        return new long[] {margin, turnover};
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        TreeSet<Exchangeable> keys = new TreeSet<>(feeInfos.keySet());
        for(Exchangeable e:keys) {
            json.add(e.toString(), feeInfos.get(e).toJson());
        }
        JsonObject result = new JsonObject();
        result.add("feeInfos", json);
        if ( brokerMarginRatio!=null ) {
            result.add("brokerMarginRatio", JsonUtil.object2json(brokerMarginRatio));
        }
        return result;
    }

    public static FutureFeeEvaluator fromJson(Properties brokerMarginRatio, JsonObject json) {
        {
            //加载期货公司的保证金调整, 如果JSON有明确检测出来的值, 那么优先使用JSON的数据
            if ( brokerMarginRatio==null ) {
                brokerMarginRatio = new Properties();
            }
            JsonObject brokerMarginShiftJson = (JsonObject)json.get("brokerMarginRatio");
            for(String key:brokerMarginShiftJson.keySet()) {
                String value = brokerMarginShiftJson.get(key).getAsString();
                brokerMarginRatio.setProperty(key, value);
            }
        }
        JsonObject feeInfos = (JsonObject)json.get("feeInfos");
        Map<Exchangeable, FutureFeeInfo> result = new HashMap<>();
        for(String key:feeInfos.keySet()) {
            Exchangeable e=Exchangeable.fromString(key);
            JsonObject feeJson = (JsonObject)feeInfos.get(key);
            FutureFeeInfo feeInfo = FutureFeeInfo.fromJson(resolveBrokerMarginRatio(e , brokerMarginRatio), feeJson);
            if ( feeInfo!=null ) {
                result.put(e, feeInfo);
            }
        }

        return new FutureFeeEvaluator(brokerMarginRatio, result);
    }

    private static double resolveBrokerMarginRatio(Exchangeable e, Properties brokerMarginShift) {
        List<String> keys = new ArrayList<>();
        keys.add(e.toString());
        keys.add(e.toString().toUpperCase());
        keys.add(e.toString().toLowerCase());
        keys.add(e.id());
        keys.add(e.id().toUpperCase());
        keys.add(e.id().toLowerCase());
        keys.add(e.commodity());
        keys.add(e.commodity().toUpperCase());
        keys.add(e.commodity().toLowerCase());
        keys.add("*");
        for(String key:keys) {
            String value = brokerMarginShift.getProperty(key);
            if ( StringUtil.isEmpty(value)) {
                continue;
            }
            if ( value.endsWith("%") ) {
                return ConversionUtil.toDouble(value.substring(0, value.length()-1))*0.01;
            }else {
                return ConversionUtil.toDouble(value);
            }
        }
        return 0;
    }

}
