package trader.service.trade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.DateUtil;
import trader.common.util.JsonUtil;
import trader.service.md.MarketData;

/**
 * 持仓的本地实现, 负责计算持仓盈亏等数据
 */
public class PositionImpl implements Position, TradeConstants {
    private Logger logger;
    private AccountImpl account;
    private Exchangeable exchangeable;
    private PosDirection direction;
    private long[] money = new long[PosMoney_Count];
    private int[] volumes = new int[PosVolume_Count];
    private List<PositionDetailImpl> details;

    /**
     * 当前在途报单
     */
    private List<OrderImpl> orders = new LinkedList<>();

    private long lastPrice;

    public PositionImpl(AccountImpl account, Exchangeable e, PosDirection direction, long[] money, int[] volumes, List<PositionDetailImpl> details) {
        this.account = account;
        this.exchangeable = e;
        this.direction = direction;
        this.money = money;
        this.volumes = volumes;
        this.details = new LinkedList<>(details);
        java.util.Collections.sort(this.details);
        logger = LoggerFactory.getLogger(account.getLoggerPackage()+"."+PositionImpl.class.getSimpleName());
    }

    public PositionImpl(Exchangeable e) {
        this.exchangeable = e;
        direction = PosDirection.Net;
    }

    @Override
    public Exchangeable getExchangeable() {
        return exchangeable;
    }

    @Override
    public PosDirection getDirection() {
        return direction;
    }

    @Override
    public long getMoney(int posMoneyIdx) {
        return money[posMoneyIdx];
    }

    @Override
    public int getVolume(int posVolumeIdx) {
        return volumes[posVolumeIdx];
    }

    @Override
    public List<Order> getActiveOrders() {
        return (List)orders;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("exchangeable", exchangeable.toString());
        json.addProperty("direction", direction.name());

        json.add("money", TradeConstants.posMoney2json(money));
        json.add("volumes", TradeConstants.posVolume2json(volumes));
        json.add("details", JsonUtil.object2json(details));
        return json;
    }

    long addMoney(int posMoneyIdx, long toadd) {
        money[posMoneyIdx] += toadd;
        return money[posMoneyIdx];
    }

    long setMoney(int posMoneyIdx, long value) {
        long result = money[posMoneyIdx];
        money[posMoneyIdx] = value;
        return result;
    }

    /**
     * 将资金从moneyIdx转移到moneyIdx2下, 在扣除保证金时有用.
     * 如果moneyIdx的资金小于amount, 失败.
     */
    boolean transferMoney(int moneyIdx, int moneyIdx2, long amount) {
        if ( money[moneyIdx]<amount ) {
            return false;
        }
        money[moneyIdx] -= amount;
        money[moneyIdx2] += amount;
        return true;
    }

    int addVolume(int posVolumeIdx, int toadd) {
        volumes[posVolumeIdx] += toadd;
        return volumes[posVolumeIdx];
    }

    int setVolume(int posVolumeIdx, int toset) {
        int result = volumes[posVolumeIdx];
        volumes[posVolumeIdx] = toset;
        return result;
    }

    /**
     * 本地计算和冻结仓位. 非线程安全
     */
    public void localFreeze(OrderImpl order) {
        localFreeze0(order, 1);
    }

    /**
     * 本地计算和解冻仓位, 非线程安全
     */
    public void localUnfreeze(OrderImpl order) {
        localFreeze0(order, -1);
    }

    private void localFreeze0(OrderImpl order, int unit) {
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            //开仓冻结资金
            long orderFrozenMargin = order.getMoney(OdrMoney_LocalFrozenMargin) - order.getMoney(OdrMoney_LocalUnfrozenMargin);
            long orderFrozenCommission = order.getMoney(OdrMoney_LocalFrozenCommission) - order.getMoney(OdrMoney_LocalUnfrozenCommission);

            addMoney(PosMoney_FrozenMargin, unit*orderFrozenMargin);
            addMoney(PosMoney_FrozenCommission, unit*orderFrozenCommission);
            if ( order.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney_LongFrozenAmount, unit*orderFrozenMargin);
            }else{
                addMoney(PosMoney_ShortFrozenAmount, unit*orderFrozenMargin);
            }
        } else {
            //平仓冻结已有仓位
            int odrVol = order.getVolume(OdrVolume_ReqVolume) - order.getVolume(OdrVolume_TradeVolume);
            if ( order.getDirection()==OrderDirection.Sell ) {
                //多仓冻结
                addVolume(PosVolume_LongFrozen, unit*odrVol);
            }else {
                addVolume(PosVolume_ShortFrozen, unit*odrVol);
            }
        }
    }

    boolean onMarketData(MarketData marketData) {
        boolean result = false;
        if ( marketData.lastPrice!=lastPrice ) {
            lastPrice = marketData.lastPrice;
            computePositionProfit(false);
            result = true;
        }
        return result;
    }

    /**
     * 报单成交
     */
    void onTransaction(OrderImpl order, TransactionImpl txn, long[] txnFees, long[] lastOrderMoney)
    {
        lastPrice = txn.getPrice();
        long txnUnfrozenMargin = Math.abs( order.getMoney(OdrMoney_LocalUnfrozenMargin) - lastOrderMoney[OdrMoney_LocalUnfrozenMargin] );
        long txnMargin = txnFees[0];
        long txnCommission = txnFees[1];//order.getMoney(OdrMoney_LocalUsedCommission) - lastOrderMoney[OdrMoney_LocalUsedCommission];
        long txnPrice = txn.getPrice();
        int txnVolume = txn.getVolume();

        assert( txnUnfrozenMargin!=0 && txnMargin!=0 && txnCommission!=0 && txnPrice!=0 && txnVolume!=0 );

        if (txn.getOffsetFlags()==OrderOffsetFlag.OPEN) {
            //开仓-更新仓位
            addVolume(PosVolume_OpenVolume, txnVolume);
            //增加持仓明细
            details.add( txn2detail(txn) );
            //解除保证金冻结, 增加保证金占用
            if ( txn.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney_LongFrozenAmount, -1*txnUnfrozenMargin);
                addMoney(PosMoney_LongUseMargin, txnMargin);
            }else {
                addMoney(PosMoney_ShortFrozenAmount, -1*txnUnfrozenMargin);
                addMoney(PosMoney_ShortUseMargin, txnMargin);
            }
        }else {
            //平仓-更新仓位
            addVolume(PosVolume_CloseVolume, txnVolume);
            if( txn.getDirection()==OrderDirection.Sell) {
                addVolume(PosVolume_LongFrozen, -1*txnVolume);
            }else {
                addVolume(PosVolume_ShortFrozen, -1*txnVolume);
            }
            //删除持仓明细
            List<PositionDetailImpl> closedDetails = removeDetails(txn);
            txn.setClosedDetails((List)closedDetails);
            //降低保证金占用, 计算仓位实现盈利
            computeTxnProfit(txn, closedDetails);
        }

        //更新手续费, 解除手续费冻结
        long txnUnfrozenCommission = order.getMoney(OdrMoney_LocalUnfrozenCommission) - lastOrderMoney[OdrMoney_LocalUnfrozenCommission];
        addMoney(PosMoney_Commission, txnCommission);
        addMoney(PosMoney_FrozenCommission, -1*Math.abs(txnUnfrozenCommission) );

        //计算持仓利润
        computePositionProfit(true);
        //计算持仓方向
        computeDirection();
    }

    /**
     * 根据成交删除持仓明细
     */
    private List<PositionDetailImpl> removeDetails(Transaction txn) {
        List<PositionDetailImpl> result = new ArrayList<>();
        int detailToRemove = 0; // 0 - FIRST, 1 - TODAY, 2 - YESTERDAY
        switch(txn.getOffsetFlags()) {
        case CLOSE_TODAY:
            detailToRemove = 1;
            break;
        case CLOSE_YESTERDAY:
            detailToRemove = 2;
        case FORCE_CLOSE:
        case CLOSE:
            detailToRemove = 0;
            break;
        }
        //卖--删除多仓
        int volumeLeft = txn.getVolume();
        PosDirection posDir = txn.getDirection().toPosDirection().oppose();
        for(Iterator<PositionDetailImpl> it = details.iterator(); it.hasNext();) {
            PositionDetailImpl detail = it.next();
            boolean accepted = false;
            switch(detailToRemove) {
            case 0:
                accepted = true;
                break;
            case 1:
                accepted = detail.isToday();
                break;
            case 2:
                accepted = !detail.isToday();
                break;
            }
            if ( posDir==detail.getDirection() && accepted ) {
                if ( volumeLeft >= detail.getVolume() ) {
                    //可以完整删除一个持仓
                    volumeLeft -= detail.getVolume();
                    result.add(detail);
                    it.remove();
                } else {
                    //需要部分删除
                    detail.addVolume(-1*volumeLeft);
                    result.add(new PositionDetailImpl(detail, volumeLeft));
                    volumeLeft = 0;
                }
            }
            if ( volumeLeft<=0 ) {
                break;
            }
        }
        if ( logger.isInfoEnabled()) {
            logger.info("平仓报单 "+txn.getOrder().getRef()+" 成交 "+txn.getId()+" 删除持仓明细: "+result);
        }
        return result;
    }

    /**
     * 计算实现盈亏
     */
    private void computeTxnProfit(Transaction txn, List<PositionDetailImpl> closedDetails)
    {
        long closeAmount = txn.getPrice()*txn.getVolume();
        long openAmount = 0;
        for(PositionDetailImpl detail:closedDetails) {
            openAmount += detail.getPrice()*detail.getVolume();
        }
        long txnProfit = 0;
        if ( txn.getDirection()==OrderDirection.Sell) {
            //平多
            txnProfit = closeAmount-openAmount;
        }else {
            //平空
            txnProfit = openAmount - closeAmount;
        }
        addMoney(PosMoney_CloseProfit, txnProfit);
    }

    /**
     * 计算持仓盈亏
     */
    private void computePositionProfit(boolean updateVolumes) {
        TxnFeeEvaluator feeEval = account.getFeeEvaluator();
        int longPos = 0, shortPos = 0;
        int longTodayPos = 0, shortTodayPos=0;
        int longYdPos = 0, shortYdPos = 0;
        long posProfit = 0;
        long openCost= 0;
        long longUseMargin=0;
        long shortUseMargin=0;
        for(int i=0;i<details.size();i++) {
            PositionDetail detail = details.get(i);
            PosDirection detailDirection = detail.getDirection();
            int detailVolume = detail.getVolume();
            long[] lastMarginValue = feeEval.compute(exchangeable, detailVolume, lastPrice, detailDirection);
            long posValue = feeEval.compute(exchangeable, detailVolume, detail.getPrice(), detailDirection)[1];

            long valueDiff = lastMarginValue[1]-posValue;
            long valueDiffUnit = 1;
            if ( detailDirection==PosDirection.Short ) {
                valueDiffUnit = -1;
                shortUseMargin += lastMarginValue[0];;
            }else{
                longUseMargin += lastMarginValue[0];
            }
            posProfit += valueDiff*valueDiffUnit;
            if ( updateVolumes ) {
                openCost += detail.getPrice()*detail.getVolume();;
                if ( detail.getDirection()==PosDirection.Long ) {
                    //多仓
                    longPos += detailVolume;
                    if ( detail.isToday() ) {
                        longTodayPos += detailVolume;
                    }else {
                        longYdPos += detailVolume;
                    }
                }else {
                    shortPos += detail.getVolume();
                    if ( detail.isToday() ) {
                        shortTodayPos += detailVolume;
                    }else {
                        shortYdPos += detailVolume;
                    }
                }
            }
        }

        setMoney(PosMoney_PositionProfit, posProfit);
        setMoney(PosMoney_LongUseMargin, longUseMargin);
        setMoney(PosMoney_ShortUseMargin, shortUseMargin);
        setMoney(PosMoney_UseMargin, Math.max(longUseMargin, shortUseMargin));
        if( updateVolumes ) {
            openCost /= (longPos+shortPos);
            setMoney(PosMoney_OpenCost, openCost);

            setVolume(PosVolume_LongPosition, longPos);
            setVolume(PosVolume_LongTodayPosition, longTodayPos);
            setVolume(PosVolume_LongYdPosition, longYdPos);
            setVolume(PosVolume_ShortPosition, shortPos);
            setVolume(PosVolume_ShortTodayPosition, shortTodayPos);
            setVolume(PosVolume_ShortYdPosition, shortYdPos);
        }
    }

    /**
     * 重新计算方向
     */
    private void computeDirection() {
        int longPos = getVolume(PosVolume_LongPosition);
        int shortPos = getVolume(PosVolume_ShortPosition);

        if ( longPos==shortPos ) {
            direction = PosDirection.Net;
        }else if ( longPos>shortPos ) {
            direction = PosDirection.Long;
        }else {
            direction = PosDirection.Short;
        }
        setVolume(PosVolume_Position, longPos+shortPos);
    }

    private PositionDetailImpl txn2detail(TransactionImpl txn) {
        LocalDateTime ldt = DateUtil.long2datetime(txn.getOrder().getExchangeable().exchange().getZoneId(), txn.getTime());
        PositionDetailImpl result = new PositionDetailImpl(txn.getDirection().toPosDirection(), txn.getVolume(), txn.getPrice(), ldt, true);
        if ( logger.isInfoEnabled()) {
            logger.info("开仓报单 "+txn.getOrder().getRef()+" 成交 "+txn.getId()+" 增加持仓明细: "+result);
        }
        txn.setOpenDetail(result);
        return result;
    }
}
