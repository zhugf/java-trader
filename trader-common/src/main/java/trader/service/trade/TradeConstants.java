package trader.service.trade;

public interface TradeConstants {


    public static enum TxnProvider{ctp, femas};

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
        ,Deleted(true, false)
        /**
         * 报单失败
         */
        ,Failed(true, false);

        OrderState(boolean done, boolean cancelable){
            this.done = done;
            this.cancelable = cancelable;
        }

        private final boolean cancelable;
        private final boolean done;

        public boolean isDone() {
            return done;
        }

        /**
         * 该状态是否可取消
         */
        public boolean isCancelable(){
            return cancelable;
        }
    }

    /**
     * 委托申报状态
     */
    public static enum OrderSubmitState{
        /**
         * 未报
         */
        Unsubmitted
        /**
         * 委托中
         */
        ,InsertSubmitting
        /**
         * 撤销委托中
         */
        ,DeleteSubmitting
        /**
         * 修改委托中
         */
        ,ModifySubmitting
        /**
         * 委托已报
         */
        ,InsertSubmitted
        /**
         * 撤销委托已报
         */
        ,CancelSubmitted
        /**
         * 修改委托已报
         */
        ,ModifySubmitted
        /**
         * 已接受
         */
        ,Accepted
        /**
         * 委托操作被拒绝
         */
        ,InsertRejected
        /**
         * 撤销委托操作被拒绝
         */
        ,CancelRejected
        /**
         * 修改委托操作被拒绝
         */
        ,ModifyRejected
    }

    /**
     * 帐户资金
     */
    public static int AccMoney_Balance = 0;
    /**
     * 可用资金
     */
    public static int AccMoney_Available = 1;
    /**
     * 冻结的保证金
     */
    public static int AccMoney_FrozenMargin = 2;
    /**
     * 当前保证金总额
     */
    public static int AccMoney_CurrMargin = 3;
    /**
     * 上次占用的保证金
     */
    public static int AccMoney_PreMargin = 4;
    /**
     * 冻结的资金
     */
    public static int AccMoney_FrozenCash = 5;
    /**
     * 手续费
     */
    public static int AccMoney_Commission = 6;
    /**
     * 冻结的手续费
     */
    public static int AccMoney_FrozenCommission = 7;
    /**
     * 平仓盈亏
     */
    public static int AccMoney_CloseProfit = 8;
    /**
     * 持仓盈亏
     */
    public static int AccMoney_PositionProfit = 9;
    /**
     * 可取资金
     */
    public static int AccMoney_WithdrawQuota = 10;
    /**
     * 基本准备金
     */
    public static int AccMoney_Reserve = 11;
    /**
     * 入金金额
     */
    public static int AccMoney_Deposit = 12;
    /**
     * 出金金额
     */
    public static int AccMoney_Withdraw = 13;

    public static int AccMoney_Count = AccMoney_Withdraw+1;

    public static final int MarginRatio_LongByMoney = 0;
    public static final int MarginRatio_LongByVolume = 1;
    public static final int MarginRatio_ShortByMoney = 2;
    public static final int MarginRatio_ShortByVolume = 3;
    public static final int MarginRatio_Count = MarginRatio_ShortByVolume+1;

    /**
     * 开仓费率(金)
     */
    public static final int CommissionRatio_OpenByMoney = 0;
    /**
     * 开仓费率(量)
     */
    public static final int CommissionRatio_OpenByVolume = 1;
    /**
     * 平仓费率(金)
     */
    public static final int CommissionRatio_CloseByMoney = 2;
    /**
     * 平仓费率(量)
     */
    public static final int CommissionRatio_CloseByVolume = 3;
    /**
     * 今平费率(金)
     */
    public static final int CommissionRatio_CloseTodayByMoney = 4;
    /**
     * 今平费率(量)
     */
    public static final int CommissionRatio_CloseTodayByVolume = 5;

    public static final int CommissionRatio_Count = CommissionRatio_CloseTodayByVolume+1;


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

    /**
     * 今日持仓
     */
    public static final int PosVolume_Position = 0;
    /**
     * 开仓量
     */
    public static final int PosVolume_OpenVolume = 1;
    /**
     * 平仓量
     */
    public static final int PosVolume_CloseVolume = 2;
    /**
     * 多头冻结
     */
    public static final int PosVolume_LongFrozen = 3;
    /**
     * 空头冻结
     */
    public static final int PosVolume_ShortFrozen = 4;
    /**
     * 今日持仓
     */
    public static final int PosVolume_TodayPosition = 5;
    /**
     * 上日持仓
     */
    public static final int PosVolume_YdPosition = 6;
    /**
     * 多头持仓(本地计算值)
     */
    public static final int PosVolume_LongPosition = 7;
    /**
     * 空头持仓(本地计算值)
     */
    public static final int PosVolume_ShortPosition = 8;
    /**
     * 多头今仓(本地计算值)
     */
    public static final int PosVolume_LongTodayPosition = 9;
    /**
     * 空头今仓(本地计算值)
     */
    public static final int PosVolume_ShortTodayPosition = 10;
    /**
     * 多头昨仓(本地计算值)
     */
    public static final int PosVolume_LongYdPosition = 11;
    /**
     * 空头昨仓(本地计算值)
     */
    public static final int PosVolume_ShortYdPosition = 12;

    public static final int PosVolume_Count = PosVolume_ShortYdPosition+1;

    /**
     * 多头开仓冻结金额
     */
    public static final int PosMoney_LongFrozenAmount = 0;
    /**
     * 空头开仓冻结金额
     */
    public static final int PosMoney_ShortFrozenAmount = 1;
    /**
     * 开仓金额
     */
    public static final int PosMoney_OpenAmount = 2;
    /**
     * 平仓金额
     */
    public static final int PosMoney_CloseAmount = 3;
    /**
     * 开仓成本
     */
    public static final int PosMoney_OpenCost = 4;
    /**
     * 持仓成本
     */
    public static final int PosMoney_PositionCost = 5;
    /**
     * 上次占用的保证金
     */
    public static final int PosMoney_PreMargin = 6;
    /**
     * 占用的保证金
     */
    public static final int PosMoney_UseMargin = 7;
    /**
     * 冻结的保证金
     */
    public static final int PosMoney_FrozenMargin = 8;
    /**
     * 冻结的手续费
     */
    public static final int PosMoney_FrozenCommission = 10;
    /**
     * 手续费
     */
    public static final int PosMoney_Commission = 12;
    /**
     * 平仓盈亏
     */
    public static final int PosMoney_CloseProfit = 13;
    /**
     * 持仓盈亏
     */
    public static final int PosMoney_PositionProfit = 14;
    /**
     * 上次结算价
     */
    public static final int PosMoney_PreSettlementPrice = 15;
    /**
     * 本次结算价
     */
    public static final int PosMoney_SettlementPrice = 16;
    /**
     * 交易所保证金
     */
    public static final int PosMoney_ExchangeMargin = 17;
    /**
     * 冻结的资金
     */
    //public static final int PosMoney_FrozenCash = 9;
    /**
     * 资金差额
     */
    //public static final int PosMoney_CashIn = 11;
    /**
     * 多头持仓占用保证金(计算字段)
     */
    public static final int PosMoney_LongUseMargin = 18;
    /**
     * 空头持仓占用保证金(计算字段)
     */
    public static final int PosMoney_ShortUseMargin = 19;
    public static final int PosMoney_Count = PosMoney_ShortUseMargin+1;

    /**
     * 本地计算用价格
     */
    public static final int OdrMoney_PriceCandidate = 0;
    /**
     * 本地使用保证金(成交)
     */
    public static final int OdrMoney_LocalUsedMargin = 1;
    /**
     * (开仓)本地冻结保证金, 这个数值计算出后不变. 需要减去OdrMoney_LocalUnFrozenMargin, 才是实际冻结保证金
     */
    public static final int OdrMoney_LocalFrozenMargin = 2;
    /**
     * (开仓)本地解冻保证金, 成交后解冻.
     */
    public static final int OdrMoney_LocalUnfrozenMargin = 3;
    /**
     * 本地使用手续费(成交)
     */
    public static final int OdrMoney_LocalUsedCommission = 4;
    /**
     * 本地冻结手续费, 这个数值计算出后不变. 需要减去OdrMoney_LocalUnfrozenCommission, 才是实际冻结手续费
     */
    public static final int OdrMoney_LocalFrozenCommission = 5;
    /**
     * 本地解冻手续费, 成交后解冻.
     */
    public static final int OdrMoney_LocalUnfrozenCommission = 6;
    /**
     * 平均开仓成本
     */
    public static final int OdrMoney_OpenCost = 7;
    public static final int OdrMoney_Count = OdrMoney_OpenCost+1;

    /**
     * 报单量
     */
    public static final int OdrVolume_ReqVolume = 0;
    /**
     * 成交量
     */
    public static final int OdrVolume_TradeVolume = 1;
    /**
     * 多头持仓冻结(报单本地计算), 多平报单产生. CLOSE/SELL
     */
    public static final int OdrVolume_LongFrozen = 2;
    /**
     * 空头持仓冻结(报单本地计算), 空平报单产生 CLOSE/BUY
     */
    public static final int OdrVolume_ShortFrozen = 3;
    /**
     * 多头持仓解冻(报单本地计算), 多平报单成交产生
     */
    public static final int OdrVolume_LongUnfrozen = 4;
    /**
     * 空头持仓解冻(报单本地计算), 空平报单成交产生
     */
    public static final int OdrVolume_ShortUnfrozen = 5;
    public static final int OdrVolume_Count = OdrVolume_ShortUnfrozen+1;
}
