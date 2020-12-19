package trader.service.trade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private Exchangeable instrument;
    private PosDirection direction;
    private long[] money = new long[PosMoney.values().length];
    private int[] volumes = new int[PosVolume.values().length];
    private LinkedList<PositionDetailImpl> details = new LinkedList<>();

    /**
     * 当前在途报单
     */
    private LinkedHashMap<String, OrderImpl> activeOrders = new LinkedHashMap<>();

    private long lastPrice;

    public PositionImpl(AccountImpl account, Exchangeable e, PosDirection direction, long[] money, int[] volumes, List<PositionDetailImpl> details) {
        this(account, e);
        this.direction = direction;
        this.money = money;
        this.volumes = volumes;
        this.details.addAll(details);
        Collections.sort(this.details);
    }

    public PositionImpl(AccountImpl account, Exchangeable e) {
        this.account = account;
        this.instrument = e;
        direction = PosDirection.Net;
        logger = LoggerFactory.getLogger(account.getLoggerCategory());
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public Exchangeable getInstrument() {
        return instrument;
    }

    @Override
    public PosDirection getDirection() {
        return direction;
    }

    public long[] getMoneys() {
        long result[] = new long[money.length];
        System.arraycopy(money, 0, result, 0, result.length);
        return result;
    }

    @Override
    public long getMoney(PosMoney mny) {
        return money[mny.ordinal()];
    }

    @Override
    public int getVolume(PosVolume vol) {
        return volumes[vol.ordinal()];
    }

    @Override
    public Collection<Order> getActiveOrders() {
        return (Collection)activeOrders.values();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instrument", instrument.toString());
        json.addProperty("direction", direction.name());

        json.add("money", TradeConstants.posMoney2json(money));
        json.add("volumes", TradeConstants.posVolume2json(volumes));
        json.add("details", JsonUtil.object2json(details));
        if (!activeOrders.isEmpty()) {
            json.add("activeOrders", JsonUtil.object2json(activeOrders.keySet()));
        }
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    long addMoney(PosMoney mny, long toadd) {
        money[mny.ordinal()] += toadd;
        return money[mny.ordinal()];
    }

    long setMoney(PosMoney mny, long value) {
        long result = money[mny.ordinal()];
        money[mny.ordinal()] = value;
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

    int addVolume(PosVolume vol, int toadd) {
        volumes[vol.ordinal()] += toadd;
        return volumes[vol.ordinal()];
    }

    int setVolume(PosVolume vol, int toset) {
        int result = volumes[vol.ordinal()];
        volumes[vol.ordinal()] = toset;
        return result;
    }

    /**
     * 本地计算和冻结仓位. 非线程安全
     */
    public void localFreeze(OrderImpl order) {
        activeOrders.put(order.getRef(), order);
        long orderFrozenCommission = order.getMoney(OdrMoney.LocalFrozenCommission);
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            //开仓冻结资金
            long orderFrozenMargin = order.getMoney(OdrMoney.LocalFrozenMargin);
            addMoney(PosMoney.FrozenMargin, orderFrozenMargin);
            if ( order.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney.LongFrozenAmount, orderFrozenMargin);
            }else{
                addMoney(PosMoney.ShortFrozenAmount, orderFrozenMargin);
            }
        } else {
            //平仓冻结已有仓位
            int odrVol = order.getVolume(OdrVolume.ReqVolume);
            if ( order.getDirection()==OrderDirection.Sell ) {
                //多仓冻结
                addVolume(PosVolume.LongFrozen, odrVol);
            }else {
                addVolume(PosVolume.ShortFrozen, odrVol);
            }
        }
        addMoney(PosMoney.FrozenCommission, orderFrozenCommission);
    }

    /**
     * 报单取消时, 本地计算和解冻仓位, 非线程安全
     */
    public void localUnfreeze(OrderImpl order, long[] localUnfreezeFees) {
        activeOrders.remove(order.getRef());
        long marginToUnfreeze = 0;
        long commissionToUnfreeze = 0;
        if ( localUnfreezeFees!=null ) {
            marginToUnfreeze = localUnfreezeFees[0];
            commissionToUnfreeze = localUnfreezeFees[1];
        }
        if ( order.getOffsetFlags()==OrderOffsetFlag.OPEN ) {
            //开仓取消冻结资金
            addMoney(PosMoney.FrozenMargin, -1*marginToUnfreeze);
            if ( order.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney.LongFrozenAmount, -1*marginToUnfreeze);
            }else{
                addMoney(PosMoney.ShortFrozenAmount, -1*marginToUnfreeze);
            }
        } else {
            //平仓冻结已有仓位
            int odrVol = order.getVolume(OdrVolume.ReqVolume) - order.getVolume(OdrVolume.TradeVolume);
            if ( order.getDirection()==OrderDirection.Sell ) {
                //多仓冻结
                addVolume(PosVolume.LongFrozen, -1*odrVol);
            }else {
                addVolume(PosVolume.ShortFrozen, -1*odrVol);
            }
        }
        addMoney(PosMoney.FrozenCommission, -1*commissionToUnfreeze);
    }

    boolean onMarketData(MarketData marketData) {
        boolean result = false;
        if ( marketData.lastPrice!=lastPrice ) {
            lastPrice = marketData.lastPrice;
            if ( details.size()>0 ) {
                computePositionProfit(false);
                result = true;
            }
        }
        return result;
    }

    /**
     * 报单成交
     */
    void onTransaction(OrderImpl order, TransactionImpl txn, long[] txnFees, long[] odrFees)
    {
        long odrUnfreezeMargin = odrFees[0];
        long odrUnfreezeCommission = odrFees[1];
        long txnMargin = txnFees[0];
        long txnCommission = txnFees[1];
        int txnVolume = txn.getVolume();

        if ( order.getVolume(OdrVolume.ReqVolume)==order.getVolume(OdrVolume.TradeVolume)) {
            activeOrders.remove(order.getRef());
        }

        if (txn.getOffsetFlags()==OrderOffsetFlag.OPEN) {
            //开仓-更新仓位
            addVolume(PosVolume.OpenVolume, txnVolume);
            //增加持仓明细
            details.add( txn2detail(txn) );
            //解除保证金冻结, 增加保证金占用
            if ( txn.getDirection()==OrderDirection.Buy ) {
                addMoney(PosMoney.LongFrozenAmount, -1*odrUnfreezeMargin);
                addMoney(PosMoney.LongUseMargin, txnMargin);
            }else {
                addMoney(PosMoney.ShortFrozenAmount, -1*odrUnfreezeMargin);
                addMoney(PosMoney.ShortUseMargin, txnMargin);
            }
            addMoney(PosMoney.FrozenMargin, -1*odrUnfreezeMargin);
        }else {
            //平仓-更新仓位
            addVolume(PosVolume.CloseVolume, txnVolume);
            if( txn.getDirection()==OrderDirection.Sell) {
                addVolume(PosVolume.LongFrozen, -1*txnVolume);
            }else {
                addVolume(PosVolume.ShortFrozen, -1*txnVolume);
            }
            //删除持仓明细
            List<PositionDetailImpl> closedDetails = removeDetails(txn);
            txn.setClosedDetails((List)closedDetails);
            //降低保证金占用, 计算仓位实现盈利
            computeTxnProfit(txn, txnFees, closedDetails);
        }

        //更新手续费, 解除手续费冻结
        addMoney(PosMoney.Commission, txnCommission);
        addMoney(PosMoney.FrozenCommission, -1*odrUnfreezeCommission );

        //计算持仓利润
        computePositionProfit(true);
        //计算持仓方向
        computeDirection();
    }

    /**
     * 根据成交删除持仓明细
     */
    private List<PositionDetailImpl> removeDetails(Transaction txn) {
        Exchangeable e = txn.getInstrument();
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
            logger.info("合约 "+e+" 报单 "+txn.getOrderId()+" 平 "+txn.getId()+" 明细: "+" "+result);
        }
        return result;
    }

    /**
     * 计算实现盈亏
     */
    private void computeTxnProfit(Transaction txn, long txnFees[], List<PositionDetailImpl> closedDetails)
    {
        TxnFeeEvaluator feeEval = account.getFeeEvaluator();

        long closeAmount = txnFees[2];
        long openAmount = 0;
        for(PositionDetailImpl detail:closedDetails) {
            //今仓使用开仓价格, 昨仓使用结算价格
            long txnPrice = detail.getPrice();
            if (!detail.isToday() ) {
                txnPrice = getMoney(PosMoney.PreSettlementPrice);
            }
            long detailFees[] = feeEval.compute(instrument, detail.getVolume(), txnPrice, detail.getDirection());
            openAmount += detailFees[1];
        }
        long txnProfit = 0;
        if ( txn.getDirection()==OrderDirection.Sell) {
            //平多
            txnProfit = closeAmount-openAmount;
        }else {
            //平空
            txnProfit = openAmount - closeAmount;
        }
        addMoney(PosMoney.CloseProfit, txnProfit);
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
        long posProfitToday = 0;
        long openCost= 0;
        long longUseMargin=0;
        long shortUseMargin=0;
        for(int i=0;i<details.size();i++) {
            PositionDetail detail = details.get(i);
            PosDirection detailDirection = detail.getDirection();
            int detailVolume = detail.getVolume();
            long[] lastMarginValue = feeEval.compute(instrument, detailVolume, lastPrice, detailDirection);

            long posValue = feeEval.compute(instrument, detailVolume, detail.getPrice(), detailDirection)[1];
            long posValueToday = posValue;
            if ( !detail.isToday() ) {
                posValueToday = feeEval.compute(instrument, detailVolume, getMoney(PosMoney.PreSettlementPrice), detailDirection)[1];
            }
            long valueDiff = lastMarginValue[1]-posValue;
            long valueDiffToday = lastMarginValue[1]-posValueToday;
            long valueDiffUnit = 1;
            if ( detailDirection==PosDirection.Short ) {
                valueDiffUnit = -1;
                shortUseMargin += lastMarginValue[0];;
            }else{
                longUseMargin += lastMarginValue[0];
            }
            posProfit += valueDiff*valueDiffUnit;
            posProfitToday += valueDiffToday*valueDiffUnit;
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

        setMoney(PosMoney.PositionProfit, posProfit);
        setMoney(PosMoney.PositionProfitToday, posProfitToday);
        setMoney(PosMoney.LongUseMargin, longUseMargin);
        setMoney(PosMoney.ShortUseMargin, shortUseMargin);
        setMoney(PosMoney.UseMargin, Math.max(longUseMargin, shortUseMargin));
        if( updateVolumes ) {
            if ( (longPos+shortPos)!=0) {
                openCost /= (longPos+shortPos);
            }
            setMoney(PosMoney.OpenCost, openCost);

            setVolume(PosVolume.LongPosition, longPos);
            setVolume(PosVolume.LongTodayPosition, longTodayPos);
            setVolume(PosVolume.LongYdPosition, longYdPos);
            setVolume(PosVolume.ShortPosition, shortPos);
            setVolume(PosVolume.ShortTodayPosition, shortTodayPos);
            setVolume(PosVolume.ShortYdPosition, shortYdPos);
            setVolume(PosVolume.TodayPosition, Math.max(longTodayPos, shortTodayPos));
            setVolume(PosVolume.YdPosition, Math.max(longYdPos, shortYdPos));
            setVolume(PosVolume.Position, Math.max(longPos, shortPos));
        }
    }

    /**
     * 重新计算方向
     */
    private void computeDirection() {
        int longPos = getVolume(PosVolume.LongPosition);
        int shortPos = getVolume(PosVolume.ShortPosition);

        if ( longPos==shortPos ) {
            direction = PosDirection.Net;
        }else if ( longPos>shortPos ) {
            direction = PosDirection.Long;
        }else {
            direction = PosDirection.Short;
        }
        setVolume(PosVolume.Position, longPos+shortPos);
    }

    private PositionDetailImpl txn2detail(TransactionImpl txn) {
        Exchangeable e = txn.getInstrument();
        LocalDateTime ldt = DateUtil.long2datetime(e.exchange().getZoneId(), txn.getTime());
        PositionDetailImpl result = new PositionDetailImpl(txn.getDirection().toPosDirection(), txn.getVolume(), txn.getPrice(), ldt, true);
        if ( logger.isInfoEnabled()) {
            logger.info("合约 "+e+" 报单 "+txn.getOrderId()+" 开 "+txn.getId()+" 明细: "+result);
        }
        txn.setOpenDetail(result);
        return result;
    }

}
