package trader.service.trade;

import com.google.gson.JsonObject;

import trader.common.util.ConversionUtil;
import trader.common.util.JsonUtil;
import trader.common.util.PriceUtil;

public interface TradeConstants {

    public static enum TradeServiceType{
        /**
         * 真正实时交易服务
         */
        RealTime
        /**
         * 模拟
         */
        ,Simulator
    }

    public static enum OrderPriceType{
        /**
         * 限价
         */
        LimitPrice
        /**
         * 任意价/市场价
         */
        ,AnyPrice
        /**
         * 对方最优价
         */
        ,BestPrice
        /**
         * 未知类型
         */
        ,Unknown
    }

    /**
     * 买卖方向
     */
    public static enum OrderDirection {
        /**
         * 买
         */
        Buy
        /**
         * 卖
         */
        , Sell;
        //支持融资融券

        public PosDirection toPosDirection() {
            switch (this) {
            case Buy:
                return PosDirection.Long;
            case Sell:
                return PosDirection.Short;
            }
            return null;
        }

        public OrderDirection inverse() {
            switch (this) {
            case Buy:
                return Sell;
            case Sell:
                return Buy;
            default:
                throw new RuntimeException("Unknown opposite direction: " + this);
            }
        }
    }

    /**
     * 开平标志类型
     */
    public static enum OrderOffsetFlag{
        /**
         * 开仓
         */
        OPEN
        /**
         * 平仓
         */
        ,CLOSE
        /**
         * 强平
         */
        ,FORCE_CLOSE
        /**
         * 平今
         */
        ,CLOSE_TODAY
        /**
         * 平昨
         */
        ,CLOSE_YESTERDAY
        /**
         * 强减
         */
        //,ForceOff
        /**
         * 本地强平
         */
        //,LocalForceClose
        ;
        public OrderOffsetFlag inverse(){
            switch(this){
            case OPEN:
                return CLOSE;
            case CLOSE:
            case FORCE_CLOSE:
            case CLOSE_TODAY:
            case CLOSE_YESTERDAY:
                return OPEN;
            default:
                throw new RuntimeException("Unsupported inverse order offset flag: "+this);
            }
        }
    }

    /**
     * 成交方式, 只有当PriceType是LimitedPrice时才有用
     */
    public static enum OrderVolumeCondition{
        Any,
        All
    }

    public static enum OrderState {
        /**
         * 未知--初始化状态
         */
        Unknown(false, false)
        /**
         * 提交中
         */
        ,Submitting(false, true)
        /**
         * 已提交
         */
        ,Submitted(false, true)
        /**
         * 已接受
         */
        ,Accepted(false, true)
        /**
         * 部分成交
         */
        ,ParticallyComplete(false, true)
        /**
         * 完全成交
         */
        ,Complete(true, false)
        /**
         * 部分订单已取消--必须是部分成交
         */
        ,PartiallyDeleted(true, false)
        /**
         * 完全取消
         */
        ,Canceled(true, false)
        /**
         * 报单失败
         */
        ,Failed(true, false);

        OrderState(boolean done, boolean revocable){
            this.done = done;
            this.revocable = revocable;
        }

        private final boolean revocable;
        private final boolean done;

        public boolean isDone() {
            return done;
        }

        /**
         * 该状态是否可取消
         */
        public boolean isRevocable(){
            return revocable;
        }
    }

    /**
     * 委托申报状态
     */
    public static enum OrderSubmitState{
        /**
         * 未报
         */
        Unsubmitted(false)
        /**
         * 委托中
         */
        ,InsertSubmitting(true)
        /**
         * 撤销委托中
         */
        ,CancelSubmitting(true)
        /**
         * 修改委托中
         */
        ,ModifySubmitting(true)
        /**
         * 委托已报
         */
        ,InsertSubmitted(true)
        /**
         * 撤销委托已报
         */
        ,CancelSubmitted(false)
        /**
         * 修改委托已报
         */
        ,ModifySubmitted(false)
        /**
         * 已接受
         */
        ,Accepted(false)
        /**
         * 委托操作被拒绝
         */
        ,InsertRejected(false)
        /**
         * 撤销委托操作被拒绝
         */
        ,CancelRejected(false)
        /**
         * 修改委托操作被拒绝
         */
        ,ModifyRejected(false);

        private boolean submitting;
        OrderSubmitState(boolean submitting){
            this.submitting = submitting;
        }
        public boolean isSubmitting() {
            return submitting;
        }
    }

    /**
     * 报单动作
     */
    public static enum OrderAction{
        /**
         * 报单
         */
        Send
        /**
         * 取消报单
         */
        ,Cancel
        /**
         * 修改报单
         */
        ,Modify
    }

    public static enum AccMoney{
    /**
     * 帐户资金(计算持仓盈亏)
     */
    Balance
    /**
     * 可用资金
     */
    ,Available
    /**
     * 冻结的保证金
     */
    ,FrozenMargin
    /**
     * 当前保证金总额
     */
    ,CurrMargin
    /**
     * 上次占用的保证金
     */
    ,PreMargin
    /**
     * 冻结的资金
     */
    ,FrozenCash
    /**
     * 手续费
     */
    ,Commission
    /**
     * 冻结的手续费
     */
    ,FrozenCommission
    /**
     * 平仓盈亏
     */
    ,CloseProfit
    /**
     * 持仓盈亏
     */
    ,PositionProfit
    /**
     * 可取资金
     */
    ,WithdrawQuota
    /**
     * 基本准备金
     */
    ,Reserve
    /**
     * 入金金额
     */
    ,Deposit
    /**
     * 出金金额
     */
    ,Withdraw
    /**
     * 期初结存(不计算持仓盈亏, 计算字段)
     */
    ,BalanceBefore
    };

    public static JsonObject accMoney2json(long money[]) {
        JsonObject moneyJson = new JsonObject();
        for(AccMoney m:AccMoney.values()) {
            moneyJson.addProperty(m.name(), PriceUtil.long2str(money[m.ordinal()]) );
        }
        return moneyJson;
    }

    public static long[] json2accMoney(JsonObject json) {
        long[] money = new long[AccMoney.values().length];
        for(AccMoney pm:AccMoney.values()) {
            money[pm.ordinal()] = PriceUtil.str2long(json.get(pm.name()).getAsString());
        }
        return money;
    }

    /**
     * 账户种类
     */
    public static enum AccClassification{
        /**
         * 期货/期权
         */
        Future
        /**
         * 普通股票/LoF基金/国债/期权
         */
        ,Security
        /**
         * 融资融券账户
         */
        ,SecurityMargin
    }

    public static enum MarginRatio{
    LongByMoney
    ,LongByVolume
    ,ShortByMoney
    ,ShortByVolume
    }

    public static enum CommissionRatio{
    /**
     * 开仓费率(金)
     */
    OpenByMoney
    /**
     * 开仓费率(量)
     */
    ,OpenByVolume
    /**
     * 平仓费率(金)
     */
    ,CloseByMoney
    /**
     * 平仓费率(量)
     */
    ,CloseByVolume
    /**
     * 今平费率(金)
     */
    ,CloseTodayByMoney
    /**
     * 今平费率(量)
     */
    ,CloseTodayByVolume
    }


    static enum PosDirection
    {
        /**
         * 多头
         */
        Long
        /**
         * 空头
         */
        ,Short
        /**
         * 净
         */
        ,Net;

        public PosDirection oppose()
        {
            switch(this){
            case Net:
                return Net;
            case Long:
                return Short;
            case Short:
                return Long;
            default:
                throw new RuntimeException("Unknown oppose position direction: "+this);
            }
        }

        public static PosDirection fromOrderDirection(OrderDirection dir){
            switch(dir){
            case Buy:
                return Long;
            case Sell:
                return Short;
            }
            return null;
        }

    }


    public static enum PosVolume{
    /**
     * 今日持仓
     */
    Position
    /**
     * 开仓量
     */
    ,OpenVolume
    /**
     * 平仓量
     */
    ,CloseVolume
    /**
     * 多头冻结
     */
    ,LongFrozen
    /**
     * 空头冻结
     */
    ,ShortFrozen
    /**
     * 今日持仓
     */
    ,TodayPosition
    /**
     * 上日持仓
     */
    ,YdPosition
    /**
     * 多头持仓(本地计算值)
     */
    ,LongPosition
    /**
     * 空头持仓(本地计算值)
     */
    ,ShortPosition
    /**
     * 多头今仓(本地计算值)
     */
    ,LongTodayPosition
    /**
     * 空头今仓(本地计算值)
     */
    ,ShortTodayPosition
    /**
     * 多头昨仓(本地计算值)
     */
    ,LongYdPosition
    /**
     * 空头昨仓(本地计算值)
     */
    ,ShortYdPosition
    }


    public static JsonObject posVolume2json(int[] volumes) {
        JsonObject volumeJson = new JsonObject();
        for(PosVolume posVol:PosVolume.values()) {
            volumeJson.addProperty(posVol.name(), volumes[posVol.ordinal()]);
        }
        return volumeJson;
    }


    public static int[] json2posVolumes(JsonObject json) {
        int volumes[] = new int[PosVolume.values().length];
        volumes[PosVolume.Position.ordinal()] = json.get("Position").getAsInt();
        volumes[PosVolume.OpenVolume.ordinal()] = json.get("OpenVolume").getAsInt();
        volumes[PosVolume.CloseVolume.ordinal()] = json.get("CloseVolume").getAsInt();
        volumes[PosVolume.LongFrozen.ordinal()] = json.get("LongFrozen").getAsInt();
        volumes[PosVolume.ShortFrozen.ordinal()] = json.get("ShortFrozen").getAsInt();
        volumes[PosVolume.TodayPosition.ordinal()] = json.get("TodayPosition").getAsInt();
        volumes[PosVolume.YdPosition.ordinal()] = json.get("YdPosition").getAsInt();
        volumes[PosVolume.LongPosition.ordinal()] = json.get("LongPosition").getAsInt();
        volumes[PosVolume.ShortPosition.ordinal()] = json.get("ShortPosition").getAsInt();
        volumes[PosVolume.LongTodayPosition.ordinal()] = json.get("LongTodayPosition").getAsInt();
        volumes[PosVolume.ShortTodayPosition.ordinal()] = json.get("ShortTodayPosition").getAsInt();
        volumes[PosVolume.LongYdPosition.ordinal()] = json.get("LongYdPosition").getAsInt();
        volumes[PosVolume.ShortYdPosition.ordinal()] = json.get("ShortYdPosition").getAsInt();

        return volumes;
    }

    public static enum PosMoney{
    /**
     * 多头开仓冻结金额
     */
    LongFrozenAmount
    /**
     * 空头开仓冻结金额
     */
    ,ShortFrozenAmount
    /**
     * 开仓金额
     */
    ,OpenAmount
    /**
     * 平仓金额
     */
    ,CloseAmount
    /**
     * 开仓成本
     */
    ,OpenCost
    /**
     * 持仓成本
     */
    ,PositionCost
    /**
     * 上次占用的保证金
     */
    ,PreMargin
    /**
     * 占用的保证金
     */
    ,UseMargin
    /**
     * 冻结的保证金
     */
    ,FrozenMargin
    /**
     * 冻结的手续费
     */
    ,FrozenCommission
    /**
     * 手续费
     */
    ,Commission
    /**
     * 平仓盈亏
     */
    ,CloseProfit
    /**
     * 持仓盈亏
     */
    ,PositionProfit
    /**
     * 上次结算价
     */
    ,PreSettlementPrice
    /**
     * 本次结算价
     */
    ,SettlementPrice
    /**
     * 交易所保证金
     */
    ,ExchangeMargin
    /**
     * 多头持仓占用保证金(计算字段)
     */
    ,LongUseMargin
    /**
     * 空头持仓占用保证金(计算字段)
     */
    ,ShortUseMargin}

    public static JsonObject posMoney2json(long[] money) {
        JsonObject moneyJson = new JsonObject();
        for(PosMoney pm:PosMoney.values()) {
            moneyJson.addProperty(pm.name(), PriceUtil.long2str(money[pm.ordinal()]));
        }

        return moneyJson;
    }

    public static long[] json2posMoney(JsonObject json) {
        long[] money = new long[PosMoney.values().length];
        for(PosMoney pm:PosMoney.values()) {
            money[pm.ordinal()] = PriceUtil.str2long(json.get(pm.name()).getAsString());
        }
        return money;
    }

    /**
     * 冻结的资金
     */
    //,FrozenCash = 9;
    /**
     * 资金差额
     */
    //,CashIn = 11;

    public static enum OdrMoney{
    /**
     * 本地计算用价格
     */
    PriceCandidate
    /**
     * 本地使用保证金(成交)
     */
    ,LocalUsedMargin
    /**
     * (开仓)本地冻结保证金, 这个数值计算出后不变. 需要减去OdrMoney_LocalUnFrozenMargin, 才是实际冻结保证金
     */
    ,LocalFrozenMargin
    /**
     * (开仓)本地解冻保证金, 成交后解冻.
     */
    ,LocalUnfrozenMargin
    /**
     * 本地使用手续费(成交)
     */
    ,LocalUsedCommission
    /**
     * 本地冻结手续费, 这个数值计算出后不变. 需要减去OdrMoney_LocalUnfrozenCommission, 才是实际冻结手续费
     */
    ,LocalFrozenCommission
    /**
     * 本地解冻手续费, 成交后解冻.
     */
    ,LocalUnfrozenCommission
    /**
     * 平均开仓成本
     */
    ,OpenCost}

    public static JsonObject odrMoney2json(long[] money) {
        JsonObject moneyJson = new JsonObject();
        for(OdrMoney mny:OdrMoney.values()) {
            moneyJson.addProperty(mny.name(), PriceUtil.long2str(money[mny.ordinal()]));
        }
        return moneyJson;
    }

    public static long[] json2OdrMoney(JsonObject json) {
        long[] result = new long[OdrMoney.values().length];
        for(String key:json.keySet()) {
            OdrMoney vol = ConversionUtil.toEnum(OdrMoney.class, key);
            result[vol.ordinal()] = JsonUtil.getPropertyAsPrice(json, key, 0);
        }
        return result;
    }

    public static enum OdrVolume{
    /**
     * 报单量
     */
    ReqVolume
    /**
     * 成交量
     */
    ,TradeVolume
    /**
     * 多头持仓冻结(报单本地计算), 多平报单产生. CLOSE/SELL
     */
    ,LongFrozen
    /**
     * 空头持仓冻结(报单本地计算), 空平报单产生 CLOSE/BUY
     */
    ,ShortFrozen
    /**
     * 多头持仓解冻(报单本地计算), 多平报单成交产生
     */
    ,LongUnfrozen
    /**
     * 空头持仓解冻(报单本地计算), 空平报单成交产生
     */
    ,ShortUnfrozen}

    public static JsonObject odrVolume2json(int[] volumes) {
        JsonObject volumeJson = new JsonObject();
        for(OdrVolume vol:OdrVolume.values()) {
            volumeJson.addProperty(vol.name(), volumes[vol.ordinal()]);
        }
        return volumeJson;
    }

    public static int[] json2OdrVolume(JsonObject json) {
        int[] result = new int[OdrVolume.values().length];
        for(String key:json.keySet()) {
            OdrVolume vol = ConversionUtil.toEnum(OdrVolume.class, key);
            result[vol.ordinal()] = JsonUtil.getPropertyAsInt(json, key, 0);
        }
        return result;
    }

    public static enum AccountTransferAction{
        /**
         * 账户存款
         */
        Deposit,
        /**
         * 账户取款
         */
        Withdraw
    }

}
