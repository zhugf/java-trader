package trader.simulator.trade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.JsonEnabled;
import trader.service.md.MarketData;
import trader.service.trade.TradeConstants;
import trader.simulator.trade.SimOrder.SimOrderState;

/**
 * 模拟交易所持仓
 */
public class SimPosition implements JsonEnabled, TradeConstants {

    private Exchangeable e;
    private SimTxnSession session;
    private PosDirection direction;
    private long[] money = new long[PosMoney_Count];
    private int[] volumes = new int[PosVolume_Count];
    /**
     * 持仓明细
     */
    private List<SimPositionDetail> details = new LinkedList<>();
    /**
     * 平仓报单
     */
    private List<SimOrder> orders = new LinkedList<>();

    SimPosition(SimTxnSession session, Exchangeable e){
        this.session = session;
        this.e = e;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("direction", getDirection().name());
        json.add("volumes", TradeConstants.posVolume2json(volumes));
        json.add("money", TradeConstants.posMoney2json(money));
        return json;
    }

    public PosDirection getDirection() {
        return direction;
    }

    /**
     * @see TradeConstants#PosVolume_Position
     * @see TradeConstants#PosVolume_OpenVolume
     * @see TradeConstants#PosVolume_CloseVolume
     * @see TradeConstants#PosVolume_LongFrozen
     * @see TradeConstants#PosVolume_ShortFrozen
     * @see TradeConstants#PosVolume_FrozenPosition
     * @see TradeConstants#PosVolume_TodayPosition
     * @see TradeConstants#PosVolume_YdPosition
     * @see TradeConstants#PosVolume_LongPosition
     * @see TradeConstants#PosVolume_ShortPosition
     * @see TradeConstants#PosVolume_LongTodayPosition
     * @see TradeConstants#PosVolume_ShortTodayPosition
     * @see TradeConstants#PosVolume_LongYdPosition
     * @see TradeConstants#PosVolume_ShortYdPosition
     */
    public int getVolume(int posVolumeIdx) {
        return volumes[posVolumeIdx];
    }

    public long getMoney(int posMoneyIdx) {
        return money[posMoneyIdx];
    }

    public List<SimPositionDetail> getDetails(){
        return details;
    }

    public List<SimOrder> getOrders(){
        return orders;
    }

    public void addOrder(SimOrder order){
        if ( !orders.contains(order)) {
            orders.add(order);
        }
    }

    public SimOrder removeOrder(String ref) {
        for(Iterator<SimOrder> it=orders.iterator(); it.hasNext();) {
            SimOrder o=it.next();
            if ( o.getRef().equals(ref)) {
                return o;
            }
        }
        return null;
    }

    /**
     * 有成交时更新
     */
    public void updateOnTxn(SimTxn txn, LocalDateTime time) {
        if(  txn==null ) {
            return;
        }
        long txnProfit = 0;
        SimOrder order = txn.getOrder();
        orders.remove(order);

        //手续费
        long orderValues[] = session.getFeeEvaluator().compute(e, txn.getVolume(), txn.getPrice(), order.getDirection(), order.getOffsetFlag());
        money[PosMoney_Commission] += orderValues[1];

        //修改持仓
        if ( order.getOffsetFlag()==OrderOffsetFlag.OPEN ) {
            //开
            SimPositionDetail detail = new SimPositionDetail(txn.getDirection().toPosDirection(), txn.getVolume(), txn.getPrice(), time);
            details.add(detail);
            volumes[PosVolume_OpenVolume] += txn.getVolume();
        }else {
            //平
            volumes[PosVolume_CloseVolume] += txn.getVolume();

            List<SimPositionDetail> pdsToClose = new ArrayList<>();
            int volumeLeft = txn.getVolume();
            int lastPartCloseVolume=0;
            SimPositionDetail lastPartClosePd = null;
            PosDirection pdDir = PosDirection.fromOrderDirection(txn.getDirection()).oppose();
            for(Iterator<SimPositionDetail> pdIt=details.iterator();pdIt.hasNext();){
                SimPositionDetail pd = pdIt.next();
                if ( pd.getVolume()==0 || !pd.getDirection().equals(pdDir) ) {
                    continue;
                }
                if ( pd.getVolume()<volumeLeft ){
                    volumeLeft -= pd.getVolume();
                    pdsToClose.add(pd);
                    continue;
                }
                if ( pd.getVolume()==volumeLeft ){
                    volumeLeft=0;
                    pdsToClose.add(pd);
                    break;
                }
                if ( pd.getVolume()>volumeLeft ){
                    lastPartCloseVolume = volumeLeft;
                    lastPartClosePd = pd;
                    volumeLeft=0;
                    pdsToClose.add(pd);
                    break;
                }
                if ( volumeLeft==0 ) {
                    break;
                }
            }
            txnProfit = computeCloseProfit(pdsToClose, lastPartCloseVolume, txn);
            if ( lastPartClosePd!=null ){
                lastPartClosePd.setVolume(lastPartClosePd.getVolume()-lastPartCloseVolume);
            }
            money[PosMoney_CloseProfit] += txnProfit;
        }

    }

    /**
     * 市场价格更新
     */
    public void updateOnMarketData(MarketData md) {
        long posProfit=0, longMargin = 0, shortMargin=0, longFrozenMargin=0, shortFrozenMargin=0, frozenCommission=0;
        long lastPrice = md.lastPrice;
        int longPos = 0, shortPos = 0, longTodayPos=0, longYdPos = 0, shortTodayPos=0, shortYdPos=0;
        int longFrozenPos=0, shortFrozenPos=0;
        LocalDate tradingDay = session.getTradingDay();
        for(SimPositionDetail d:details) {
            long[] posOpenValues = session.getFeeEvaluator().compute(e, d.getVolume(), d.getOpenPrice(), d.getDirection());
            long[] posValues = session.getFeeEvaluator().compute(e, d.getVolume(), lastPrice, d.getDirection());
            if ( d.getDirection()==PosDirection.Long ) {
                longPos+=d.getVolume();
                if ( tradingDay.equals(d.getOpenTime()) ) {
                    longTodayPos += d.getVolume();
                }else {
                    longYdPos += d.getVolume();
                }
                longMargin += posValues[0];
            }else {
                shortPos+=d.getVolume();
                if ( tradingDay.equals(d.getOpenTime()) ) {
                    shortTodayPos += d.getVolume();
                }else {
                    shortYdPos += d.getVolume();
                }
                shortMargin += posValues[0];
            }
            posProfit += (posValues[1] - posOpenValues[1]);
        }
        for(SimOrder o:orders) {
            if ( o.getOffsetFlag()==OrderOffsetFlag.OPEN ) {
                long[] orderValues = session.getFeeEvaluator().compute(e, o.getVolume(), o.getLimitPrice(), o.getDirection(), o.getOffsetFlag());
                frozenCommission += orderValues[1];
                //开仓
                if ( o.getDirection()==OrderDirection.Buy ) {
                    //开多
                    longFrozenMargin += orderValues[0];
                }else {
                    //开空
                    shortFrozenMargin += orderValues[0];
                }
            } else {
                if ( o.getDirection()==OrderDirection.Sell ) {
                    //平多
                    longFrozenPos += o.getVolume();
                }else {
                    //平空
                    shortFrozenPos += o.getVolume();
                }
            }
        }
        direction = PosDirection.Net;
        if ( longPos>shortPos) {
            direction = PosDirection.Long;
        }
        if ( shortPos>longPos) {
            direction = PosDirection.Short;
        }
        volumes[PosVolume_Position] = longPos+shortPos;
        //volumes[PosVolume_OpenVolume]
        //volumes[PosVolume_CloseVolume]
        volumes[PosVolume_LongFrozen] = longFrozenPos;
        volumes[PosVolume_ShortFrozen] = shortFrozenPos;
        volumes[PosVolume_TodayPosition] = longTodayPos+shortTodayPos;
        volumes[PosVolume_YdPosition] = longYdPos+shortYdPos;
        volumes[PosVolume_LongPosition] = longPos;
        volumes[PosVolume_ShortPosition] = shortPos;
        volumes[PosVolume_LongTodayPosition] = longTodayPos;
        volumes[PosVolume_ShortTodayPosition] = shortTodayPos;
        volumes[PosVolume_LongYdPosition] = longYdPos;
        volumes[PosVolume_ShortYdPosition] = shortYdPos;

        money[PosMoney_LongFrozenAmount] = longFrozenMargin;
        money[PosMoney_ShortFrozenAmount] = shortFrozenMargin;
        money[PosMoney_FrozenMargin] = longFrozenMargin+shortFrozenMargin;;
        money[PosMoney_FrozenCommission] = frozenCommission;
        money[PosMoney_PositionProfit] = posProfit;
        money[PosMoney_UseMargin] = Math.max(longMargin, shortMargin);
        money[PosMoney_LongUseMargin] = longMargin;
        money[PosMoney_ShortUseMargin] = shortMargin;
    }

    public int removeCompleteOrders() {
        int count=0;
        for(Iterator<SimOrder> it=orders.iterator();it.hasNext();) {
            SimOrder o = it.next();
            if ( o.getState()!=SimOrderState.Placed ) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    private long computeCloseProfit(List<SimPositionDetail> pds, int lastPartCloseVolume, SimTxn t)
    {
        long totalCloseProfit = 0;
        for(int i=0;i<pds.size();i++){
            boolean lastPd = i==pds.size()-1;
            SimPositionDetail pd = pds.get(i);
            int volumeToClose = pd.getVolume();
            if ( lastPd&&lastPartCloseVolume>0 ){
                volumeToClose = lastPartCloseVolume;
            }
            long pdOpenValue[] = session.getFeeEvaluator().compute(e, volumeToClose, pd.getOpenPrice(), direction);
            long pdCloseValue[] = session.getFeeEvaluator().compute(e, volumeToClose, t.getPrice(), direction);
            long pdCloseProfit = 0;
            switch(t.getDirection()){
            case Sell:
                pdCloseProfit = pdCloseValue[1]-pdOpenValue[1];
                break;
            case Buy:
                pdCloseProfit = pdOpenValue[1]-pdCloseValue[1];
                break;
            }
            totalCloseProfit += pdCloseProfit;
        }
        return totalCloseProfit;
    }
}
