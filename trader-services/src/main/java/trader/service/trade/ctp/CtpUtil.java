package trader.service.trade.ctp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jctp.JctpConstants;
import trader.service.trade.TradeConstants.OrderDirection;
import trader.service.trade.TradeConstants.OrderOffsetFlag;
import trader.service.trade.TradeConstants.OrderPriceType;
import trader.service.trade.TradeConstants.OrderState;
import trader.service.trade.TradeConstants.OrderSubmitState;
import trader.service.trade.TradeConstants.OrderVolumeCondition;
import trader.service.trade.TradeConstants.PosDirection;

public class CtpUtil implements JctpConstants{
    private final static Logger logger = LoggerFactory.getLogger(CtpUtil.class);

    public static char orderPriceType2ctp(OrderPriceType priceType) {
        switch(priceType){
        case AnyPrice:
            return THOST_FTDC_OPT_AnyPrice;
        case BestPrice:
            return THOST_FTDC_OPT_BestPrice;
        case LimitPrice:
            return THOST_FTDC_OPT_LimitPrice; //限价
        default:
            logger.error("Unsupported order price type for CTP: "+priceType);
            return THOST_FTDC_OPT_LimitPrice;
        }
    }

    public static char orderVolumeCondition2ctp(OrderVolumeCondition volumeCondition) {
        switch(volumeCondition) {
        case All:
            return THOST_FTDC_VC_CV;
        case Any:
        default:
            return THOST_FTDC_VC_AV;
        }
    }

    public static OrderOffsetFlag ctp2OrderOffsetFlag(char orderComboOffsetFlag){
        switch(orderComboOffsetFlag){
        case THOST_FTDC_OF_Open:
            return OrderOffsetFlag.OPEN;
        case THOST_FTDC_OF_Close:
            return OrderOffsetFlag.CLOSE;
        case THOST_FTDC_OF_ForceClose:
            return OrderOffsetFlag.FORCE_CLOSE;
        case THOST_FTDC_OF_CloseToday:
            return OrderOffsetFlag.CLOSE_TODAY;
        case THOST_FTDC_OF_CloseYesterday:
            return OrderOffsetFlag.CLOSE_YESTERDAY;
        case THOST_FTDC_OF_ForceOff:
        case THOST_FTDC_OF_LocalForceClose:
            return OrderOffsetFlag.FORCE_CLOSE;
        default:
            logger.error("Unknown CTP order offset flag: "+orderComboOffsetFlag);
            return OrderOffsetFlag.OPEN;
        }
    }

    public static String orderOffsetFlag2ctp(OrderOffsetFlag offsetFlag) {
        switch(offsetFlag){
        case OPEN:
            return STRING_THOST_FTDC_OF_Open;
        case CLOSE:
            return STRING_THOST_FTDC_OF_Close;
        case FORCE_CLOSE:
            return STRING_THOST_FTDC_OF_ForceClose;
        case CLOSE_TODAY:
            return STRING_THOST_FTDC_OF_CloseToday;
        case CLOSE_YESTERDAY:
            return STRING_THOST_FTDC_OF_CloseYesterday;
        default:
            logger.error("Unsupported order comboOffsetFlags for CTP: "+offsetFlag);
            return STRING_THOST_FTDC_OF_Open;
        }
    }

    public static OrderPriceType ctp2OrderPriceType(int ctpOrderPriceType){
        switch(ctpOrderPriceType){
        case THOST_FTDC_OPT_AnyPrice:
            return OrderPriceType.AnyPrice;
        case THOST_FTDC_OPT_LimitPrice:
            return OrderPriceType.LimitPrice;
        case THOST_FTDC_OPT_BestPrice:
            return OrderPriceType.BestPrice;
        default:
            logger.error("Unknown OrderPriceType or CTP: "+ctpOrderPriceType);
            return OrderPriceType.Unknown;
        }
    }

    public static OrderDirection ctp2OrderDirection(char ctpOrderDirectionType)
    {
        switch(ctpOrderDirectionType){
        case THOST_FTDC_D_Buy:
            return OrderDirection.Buy;
        case THOST_FTDC_D_Sell:
            return OrderDirection.Sell;
        default:
            logger.error("Unknown CTP order direction type: "+ctpOrderDirectionType);
            return OrderDirection.Buy;
        }
    }

    public static char orderDirection2ctp(OrderDirection orderDirection){
        switch( orderDirection){
        case Buy:
            return THOST_FTDC_D_Buy;
        case Sell:
            return THOST_FTDC_D_Sell;
        default:
            return '\0';
        }
    }

    public static PosDirection ctp2PosDirection(char posDirection){
        switch( posDirection ){
        case THOST_FTDC_PD_Net:
            return PosDirection.Net;
        case THOST_FTDC_PD_Long:
            return PosDirection.Long;
        case THOST_FTDC_PD_Short:
            return PosDirection.Short;
        }
        logger.error("Unknown CTP position direction type: "+posDirection);
        return PosDirection.Long;
    }

    public static OrderState ctp2OrderState(char ctpStatus, char submitStatus){
        switch(ctpStatus){
        case THOST_FTDC_OST_Unknown:
            return (OrderState.Submitted); //CTP接受，但未发到交易所
        case THOST_FTDC_OST_NotTouched:
        case THOST_FTDC_OST_Touched:
            //Ignore
            return OrderState.Submitted; //CTP接受，但未发到交易所
        case THOST_FTDC_OST_Canceled: //撤单，检查原因
            if ( submitStatus == THOST_FTDC_OSS_InsertRejected ){
                return OrderState.Failed;
            } else {
                return OrderState.Deleted;
            }
        case THOST_FTDC_OST_PartTradedQueueing:
        case THOST_FTDC_OST_PartTradedNotQueueing:
            return (OrderState.ParticallyComplete);
        case THOST_FTDC_OST_AllTraded:
            return (OrderState.Complete);
        case THOST_FTDC_OST_NoTradeQueueing:
        case THOST_FTDC_OST_NoTradeNotQueueing:
            return (OrderState.Accepted);
        }
        logger.error("Unknown CTP status: "+ctpStatus);
        return OrderState.Unknown;
    }

    public static OrderSubmitState ctp2OrderSubmitState(char submitStatus){
        switch(submitStatus){
        case THOST_FTDC_OSS_Accepted:
            return OrderSubmitState.Accepted;
        case  THOST_FTDC_OSS_InsertSubmitted:
            return OrderSubmitState.InsertSubmitted;
        case  THOST_FTDC_OSS_ModifySubmitted:
            return OrderSubmitState.ModifySubmitted;
        case  THOST_FTDC_OSS_CancelSubmitted:
            return OrderSubmitState.CancelSubmitted;
        case THOST_FTDC_OSS_InsertRejected:
            return OrderSubmitState.InsertRejected;
        case THOST_FTDC_OSS_ModifyRejected:
            return OrderSubmitState.ModifyRejected;
        case THOST_FTDC_OSS_CancelRejected:
            return OrderSubmitState.CancelRejected;
        }
        logger.error("Unknown CTP submit status: "+submitStatus);
        return OrderSubmitState.Unsubmitted;
    }

}
