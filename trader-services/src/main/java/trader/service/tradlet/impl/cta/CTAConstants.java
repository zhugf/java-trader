package trader.service.tradlet.impl.cta;

public interface CTAConstants {

    public static enum CTARuleState{
        /**
         * 待进场
         */
        ToEnter(false)
        /**
         * 开仓中
         */
        ,Opening(false)
        /**
         * 持仓中
         */
        ,Holding(false)
        /**
         * 止盈
         */
        ,TakeProfit(true)
        /**
         * 止损
         */
        ,StopLoss(true)
        /**
         * 超时
         */
        ,Timeout(true)
        /**
         * 未进场撤
         */
        ,Discarded(true);

        private boolean done;
        CTARuleState(boolean done){
            this.done = done;
        }
        public boolean isDone() {
            return done;
        }
    }

}
