package trader.service.trade;

import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.PriceUtil;
import trader.service.ServiceErrorConstants;
import trader.service.md.MarketData;
import trader.service.md.MarketDataService;

public class OrderValidator implements TradeConstants, ServiceErrorConstants {
    private AccountImpl account;
    private OrderBuilder builder;

    public OrderValidator(AccountImpl account, OrderBuilder builder) {
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
        AccountView view = builder.getView();
        Exchangeable e = builder.getExchangeable();
        Integer maxVolume = view.getMaxVolumes().get(e);
        if ( maxVolume==null ) {
            throw new AppException(ERRCODE_TRADE_EXCHANGEABLE_INVALID, "开单品种 "+e+" 不在视图 "+view.getId()+" 允许范围内");
        }
        int currVolume = 0;
        Position pos = account.getOrCreatePosition(e, false);
        if ( builder.getOffsetFlag()==OrderOffsetFlag.OPEN) {
            //检查仓位限制
            if ( pos!=null ) {
                switch(builder.getDirection()) {
                case Buy:
                    currVolume = pos.getVolume(PosVolume_LongPosition);
                    break;
                case Sell:
                    currVolume = pos.getVolume(PosVolume_ShortPosition);
                    break;
                }
            }
            if ( maxVolume!=null && maxVolume<(currVolume+builder.getVolume()) ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "开单超出视图 "+view.getId()+" 持仓数量限制 "+maxVolume+" : "+builder);
            }
        }else {
            //检查持仓限制
            if ( pos!=null ) {
                switch(builder.getDirection()) {
                case Buy:
                    currVolume = pos.getVolume(PosVolume_ShortPosition);
                    break;
                case Sell:
                    currVolume = pos.getVolume(PosVolume_LongPosition);
                    break;
                }
            }
            if ( currVolume<builder.getVolume() ) {
                throw new AppException(ERRCODE_TRADE_VOL_EXCEEDS_LIMIT, "平单超出账户 "+account.getId()+" 当前持仓数量 "+currVolume+" : "+builder);
            }
        }
    }

    /**
     * 校验报单的保证金
     */
    private long[] validateOrderMargin(OrderBuilder builder) throws AppException
    {
        long[] orderMoney = new long[OdrMoney_Count];
        AccountView view = builder.getView();
        Exchangeable e = builder.getExchangeable();
        long priceCandidate = getOrderPriceCandidate(builder);
        orderMoney[OdrMoney_PriceCandidate] = priceCandidate;
        long[] odrFees = account.getFeeEvaluator().compute(e, builder.getVolume(), priceCandidate, builder.getDirection(), builder.getOffsetFlag());
        long commission = odrFees[1];
        if ( builder.getOffsetFlag()==OrderOffsetFlag.OPEN) {
            //开仓, 检查是否有新的保证金需求
            long longMargin=0, shortMargin=0, longMargin2=0, shortMargin2=0;
            Position pos = account.getOrCreatePosition(e, false);
            if ( pos!=null ) {
                longMargin = pos.getMoney(PosMoney_LongUseMargin);
                shortMargin = pos.getMoney(PosMoney_ShortUseMargin);
                longMargin2 = longMargin;
                shortMargin2 = shortMargin;
            }
            if ( builder.getDirection()==OrderDirection.Buy) {
                longMargin2 += odrFees[0];
            } else {
                shortMargin2 += odrFees[0];
            }
            //计算新的保证金需求
            long posMargin = Math.max(longMargin, shortMargin);
            long posMargin2 = Math.max(longMargin2, shortMargin2);
            long orderMarginReq = posMargin2-posMargin;
            long avail = account.getMoney(AccMoney_Available);
            if( avail <= orderMarginReq+commission ) {
                throw new AppException(ERRCODE_TRADE_MARGIN_NOT_ENOUGH, "账户 "+account.getId()+" 可用保证金 "+PriceUtil.long2price(avail)+" 不足");
            }
            orderMoney[OdrMoney_LocalFrozenMargin] = orderMarginReq;
        }else {
            //平仓, 解冻保证金这里没法计算
        }
        orderMoney[OdrMoney_LocalFrozenCommission] = commission;

        return orderMoney;
    }


    /**
     * 返回订单的保证金冻结用的价格, 市价使用最高/最低价格
     */
    long getOrderPriceCandidate(OrderBuilder builder) {
        MarketDataService mdService = account.getBeansContainer().getBean(MarketDataService.class);
        MarketData md = mdService.getLastData(builder.getExchangeable());
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
