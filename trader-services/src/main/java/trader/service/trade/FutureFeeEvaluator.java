package trader.service.trade;

import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;

import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
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
        public JsonObject toJsonObject() {
            JsonObject json = new JsonObject();
            json.addProperty("priceTick", PriceUtil.long2price(priceTick));
            json.addProperty("volumeMultiple", volumeMultiple);
            json.add("marginRatios", JsonUtil.doubles2array(marginRatios));
            json.add("commissionRatios", JsonUtil.doubles2array(commissionRatios));
            return json;
        }

    }

    Map<Exchangeable, FutureFeeInfo> feeInfos;

    public FutureFeeEvaluator(Map<Exchangeable, FutureFeeInfo> feeInfos)
    {
        this.feeInfos = feeInfos;
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
                if ( longByMoney!=0 ) {
                    double value = turnover;
                    value = value*longByMoney;
                    margin = (long)value;
                } else {
                    margin = (long)( volume*longByVolume );
                }
            }else {
                double shortByMoney = feeInfo.marginRatios[MarginRatio_ShortByMoney];
                double shortByVolume = feeInfo.marginRatios[MarginRatio_ShortByVolume];
                if ( shortByMoney!=0 ) {
                    double value = turnover;
                    value = value*shortByMoney;
                    margin = (long)value;
                } else {
                    margin = (long)( volume*shortByVolume );
                }
            }
        }
        {//手续费
            switch(offsetFlag) {
            case OPEN:
                long openMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_OpenByMoney] );
                long openVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_OpenByVolume] );
                commission = Math.max(openMoney, openVolume);
                break;
            case CLOSE:
            case CLOSE_YESTERDAY:
            case FORCE_CLOSE:
                long closeMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_CloseByMoney] );
                long closeVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_CloseByVolume] );
                commission = Math.max(closeMoney, closeVolume);
                break;
            case CLOSE_TODAY:
                long closeTodayMoney = (long)( turnover*feeInfo.commissionRatios[CommissionRatio_CloseTodayByMoney] );
                long closeTodayVolume = (long)( volume*feeInfo.commissionRatios[CommissionRatio_CloseTodayByVolume] );
                commission = Math.max(closeTodayMoney, closeTodayVolume);
                break;
            }
        }
        return  new long[] {margin, commission};
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        TreeSet<Exchangeable> keys = new TreeSet<>(feeInfos.keySet());
        for(Exchangeable e:keys) {
            json.add(e.toString(), feeInfos.get(e).toJsonObject());
        }
        return json;
    }

}
