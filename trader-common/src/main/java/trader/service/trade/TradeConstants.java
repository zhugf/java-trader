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
    public static enum OrderDirection{
        /**
         * 买
         */
        Buy(true)
        /**
         * 卖
         */
        ,Sell(false);
        //TODO 融资融券


        private final boolean isLong;

        OrderDirection(boolean v){
            this.isLong = v;
        }

        /**
         * 该状态是否可取消
         */
        public boolean isLong(){
            return isLong;
        }

        public OrderDirection inverse(){
            switch(this){
            case Buy:
                return Sell;
            case Sell:
                return Buy;
            default:
                throw new RuntimeException("Unknown opposite direction: "+this);
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

    public static enum OrderState {
        /**
         * 未知--初始化状态
         */
        Unknown(false)
        /**
         * 提交中
         */
        ,Submitting(true)
        /**
         * 已提交
         */
        ,Submitted(true)
        /**
         * 已接受
         */
        ,Accepted(true)
        /**
         * 部分成交
         */
        ,ParticallyComplete(true)
        /**
         * 完全成交
         */
        ,Complete(false)
        /**
         * 部分订单已取消--必须是部分成交
         */
        ,PartiallyCanceled(false)
        /**
         * 完全取消
         */
        ,Canceled(false)
        /**
         * 报单失败
         */
        ,Failed(false);

        OrderState(boolean cancelable){
            this.cancelable = cancelable;
        }

        private final boolean cancelable;

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
        ,CancelSubmitting
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

    public static enum OrderTime{
        SubmitTime
        ,AcknowledgeTime
        ,CompleteTime
    }

}
