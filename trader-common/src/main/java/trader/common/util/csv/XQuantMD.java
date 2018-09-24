package trader.common.util.csv;

import java.time.LocalDateTime;

public class XQuantMD {

    public int Id;

    public LocalDateTime timestamp;

    public String instrument;

    public long PreSettlementPrice;

    public long Open;

    public long High;

    public long Low;

    public long YesterdayClosePrice;

    /**
     *昨持仓量
     */
    public int YesterdayOpenInt;

    public long TodayClosePrice;

    public long SettlementPrice;

    public int OpenInt;

    public long UpperLimitPrice;

    public long LowerLimitPrice;

    public long LatestPrice;

    /**
     * 最新成交量（累计值）
     */
    public int LatestVol;

    /**
     * 最新成交量（非累计值）
     */
    public int Vol;

    public long Turnover;

    public long AvgLatestPrice;

    public long AskPrice1;

    public int AskVol1;

    public long BidPrice1;

    public int BidVol1;
}
