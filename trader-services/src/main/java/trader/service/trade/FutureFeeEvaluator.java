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
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        TreeSet<Exchangeable> keys = new TreeSet<>(feeInfos.keySet());
        for(Exchangeable e:keys) {
            json.add(e.toString(), feeInfos.get(e).toJsonObject());
        }
        return json;
    }

}
