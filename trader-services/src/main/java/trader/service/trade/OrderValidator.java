package trader.service.trade;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.PriceUtil;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;

public class OrderValidator implements TradeConstants, ServiceErrorConstants {

    private BeansContainer beansContainer;
    private AccountImpl account;
    private OrderBuilder builder;

    public OrderValidator(BeansContainer beansContainer, AccountImpl account, OrderBuilder builder) {
        this.beansContainer = beansContainer;
        this.account = account;
        this.builder = builder;
    }

    public long[] validate() throws AppException
    {
        validateOrderVolume(builder);
        return validateOrderMargin(builder);
    }

    /**
     * 检查报单请求, 看有无超出限制
     * @param builder
     */
    private void validateOrderVolume(OrderBuilder builder) throws AppException
    {
        Exchangeable e = builder.getInstrument();
        //计算可用资金可以开仓手数
        int currVolume = 0;
        Position pos = account.getPosition(e);
        if ( builder.getOffsetFlag()!=OrderOffsetFlag.OPEN) {
            //检查持仓限制
            if ( pos!=null ) {
                switch(builder.getDirection()) {
                case Buy:
                    currVolume = pos.getVolume(PosVolume.ShortPosition);
                    break;
                case Sell:
                    currVolume = pos.getVolume(PosVolume.LongPosition);
                    break;
                }
            }
            if ( currVolume<builder.getVolume() ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "Account "+account.getId()+" close order volumes exceeds curr position : "+currVolume+" : "+builder);
            }
        }
    }

    /**
     * 校验报单的保证金
     */
    private long[] validateOrderMargin(OrderBuilder builder) throws AppException
    {
        long[] orderMoney = new long[OdrMoney.values().length];
        Exchangeable e = builder.getInstrument();
        long priceCandidate = getOrderPriceCandidate(builder);
        orderMoney[OdrMoney.PriceCandidate.ordinal()] = priceCandidate;
        long[] odrFees = account.getFeeEvaluator().compute(e, builder.getVolume(), priceCandidate, builder.getDirection(), builder.getOffsetFlag());
        long odrMarginReq = odrFees[0];
        long odrCommissionReq = odrFees[1];
        if ( builder.getOffsetFlag()==OrderOffsetFlag.OPEN ) {
            //开仓, 计算冻结保证金
            //这里出于保守起见, 不采用单边保证金机制(shfe)
            long avail = account.getMoney(AccMoney.Available);
            if( avail <= odrMarginReq+odrCommissionReq ) {
                throw new AppException(ERRCODE_TRADE_MARGIN_NOT_ENOUGH, "Account "+account.getId()+" avail "+PriceUtil.long2price(avail)+" is NOT enough: "+odrMarginReq);
            }
            //计算持仓+新增保证金是否超出账户视图限制
            long posMargin = 0;
            for(Position pos:account.getPositions()) {
                posMargin += pos.getMoney(PosMoney.UseMargin);
            }
            orderMoney[OdrMoney.LocalFrozenMargin.ordinal()] = odrMarginReq;
        }else {
            //平仓, 解冻保证金这里没法计算
        }
        orderMoney[OdrMoney.LocalFrozenCommission.ordinal()] = odrCommissionReq;

        return orderMoney;
    }


    /**
     * 返回订单的保证金冻结用的价格, 市价使用最高/最低价格
     */
    long getOrderPriceCandidate(OrderBuilder builder) {
        MarketDataService mdService = beansContainer.getBean(MarketDataService.class);
        MarketData md = mdService.getLastData(builder.getInstrument());
        switch(builder.getPriceType()) {
        case Unknown:
        case AnyPrice:
            if ( builder.getDirection()==OrderDirection.Buy ) {
                return md.highestPrice;
            }else {
                return md.lowestPrice;
            }
        case BestPrice:
            return md.lastPrice;
        case LimitPrice:
            return builder.getLimitPrice();
        }
        return md.lastPrice;
    }

}
