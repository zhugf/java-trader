package trader.service.trade;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;

/**
 * 期货保证金占用, 手续费计算
 */
public class FutureFeeEvaluator implements TxnFeeEvaluator, TradeConstants {

    private static final int PriceType_Last = 0;
    private static final int PriceType_Highest = 1;
    private static final int PriceType_Lowest = 2;


    public static class FutureFeeInfo implements JsonEnabled {
        private double brokerMarginShift;
        private long priceTick;
        private int volumeMultiple;
        private double[] marginRatios = new double[MarginRatio_Count];
        private double[] commissionRatios = new double[CommissionRatio_Count];

        public long getPriceTick() {
            return priceTick;
        }

        public int getVolumeMultiple() {
            return volumeMultiple;
        }

        /**
         * @see TradeConstants#MarginRatio_LongByMoney
         * @see TradeConstants#MarginRatio_LongByVolume
         * @see TradeConstants#MarginRatio_ShortByMoney
         * @see TradeConstants#MarginRatio_ShortByVolume
         */
        public double getMarginRatio(int idx) {
            return marginRatios[idx];
        }

        /**
         * @see TradeConstants#CommissionRatio_OpenByMoney
         * @see TradeConstants#CommissionRatio_OpenByVolume
         * @see TradeConstants#CommissionRatio_CloseByMoney
         * @see TradeConstants#CommissionRatio_CloseByVolume
         * @see TradeConstants#CommissionRatio_CloseTodayByMoney
         * @see TradeConstants#CommissionRatio_CloseTodayByVolume
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
            json.addProperty("priceTick", PriceUtil.long2price(priceTick));
            json.addProperty("volumeMultiple", volumeMultiple);
            json.add("marginRatios", JsonUtil.object2json(marginRatios));
            json.add("commissionRatios", JsonUtil.object2json(commissionRatios));
            return json;
        }

        public static FutureFeeInfo fromJson(Double brokerMarginShift, JsonObject json) {
            FutureFeeInfo result = new FutureFeeInfo();
            if ( brokerMarginShift!=null ) {
                result.brokerMarginShift = brokerMarginShift;
            }
            if ( json.has("brokerMarginShift")) {
                result.brokerMarginShift = ConversionUtil.toDouble(json.get("brokerMarginShift").getAsString());
            }
            result.priceTick = PriceUtil.str2long(json.get("priceTick").getAsString());
            result.volumeMultiple = ConversionUtil.toInt(json.get("volumeMultiple").getAsString());
            {
                JsonArray array = (JsonArray)json.get("marginRatios");
                result.marginRatios = new double[array.size()];
                for(int i=0;i<array.size();i++) {
                    result.marginRatios[i] = ConversionUtil.toDouble( array.get(i) );
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

    Map<Exchangeable, FutureFeeInfo> feeInfos;
    Double brokerMarginShift = null;

    public FutureFeeEvaluator(Double brokerMarginShift, Map<Exchangeable, FutureFeeInfo> feeInfos)
    {
        this.brokerMarginShift = brokerMarginShift;
        this.feeInfos = feeInfos;
    }

    public Double getBrokerMarginShift() {
        return brokerMarginShift;
    }

    @Override
    public Collection<Exchangeable> getExchangeables(){
        return feeInfos.keySet();
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
        return compute(txn.getOrder().getExchangeable(), txn.getVolume(), txn.getPrice(), txn.getDirection(), txn.getOffsetFlags());
    }

    @Override
    public long[] compute(Exchangeable e, int volume, long price, OrderDirection direction, OrderOffsetFlag offsetFlag) {
        FutureFeeInfo feeInfo = feeInfos.get(e);
        if ( feeInfo==null ) {
            return null;
        }
        long turnover = volume*price*feeInfo.getVolumeMultiple();
        long margin=0, commission=0;
        {//保证金
            if ( direction==OrderDirection.Buy ) {
                double longByMoney = feeInfo.marginRatios[MarginRatio_LongByMoney];
                double longByVolume = feeInfo.marginRatios[MarginRatio_LongByVolume];
                long marginByMoney = (long)(longByMoney*turnover);
                margin = marginByMoney;
            }else {
                double shortByMoney = feeInfo.marginRatios[MarginRatio_ShortByMoney];
                double shortByVolume = feeInfo.marginRatios[MarginRatio_ShortByVolume];
                long marginByMoney = (long)(shortByMoney*turnover);
                margin = marginByMoney;
            }
        }
        {//手续费
            switch(offsetFlag) {
            case OPEN:
                long openMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_OpenByMoney] );
                long openVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_OpenByVolume] );
                commission = (openMoney+openVolume);
                break;
            case CLOSE:
            case CLOSE_YESTERDAY:
            case FORCE_CLOSE:
                long closeMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_CloseByMoney] );
                long closeVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_CloseByVolume] );
                commission = closeMoney+closeVolume;
                break;
            case CLOSE_TODAY:
                long closeTodayMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_CloseTodayByMoney] );
                long closeTodayVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_CloseTodayByVolume] );
                commission = closeTodayMoney+closeTodayVolume;
                break;
            }
        }
        return  new long[] {margin, commission, turnover};
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
                double longByMoney = feeInfo.marginRatios[MarginRatio_LongByMoney]+feeInfo.brokerMarginShift;
                double longByVolume = feeInfo.marginRatios[MarginRatio_LongByVolume]+feeInfo.brokerMarginShift;
                long marginByMoney = (long)(longByMoney*turnover);
                margin = marginByMoney;
                break;
            }
        default:
            {
                double shortByMoney = feeInfo.marginRatios[MarginRatio_ShortByMoney]+feeInfo.brokerMarginShift;
                double shortByVolume = feeInfo.marginRatios[MarginRatio_ShortByVolume]+feeInfo.brokerMarginShift;
                long marginByMoney = (long)(shortByMoney*turnover);
                margin = marginByMoney;
                break;
            }
        }
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
        if ( brokerMarginShift!=null ) {
            result.addProperty("brokerMarginShift", brokerMarginShift);
        }
        return result;
    }

    public static FutureFeeEvaluator fromJson(Double brokerMarginShift, JsonObject json) {
        if ( json.has("brokerMarginShift")) {
            brokerMarginShift = ConversionUtil.toDouble(json.get("brokerMarginShift").getAsString());
        }
        JsonObject feeInfos = (JsonObject)json.get("feeInfos");
        Map<Exchangeable, FutureFeeInfo> result = new HashMap<>();
        for(String key:feeInfos.keySet()) {
            Exchangeable e=Exchangeable.fromString(key);
            JsonObject feeJson = (JsonObject)feeInfos.get(key);
            FutureFeeInfo feeInfo = FutureFeeInfo.fromJson(brokerMarginShift, feeJson);
            result.put(e, feeInfo);
        }
        return new FutureFeeEvaluator(brokerMarginShift, result);
    }

}
