package trader.tool;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import trader.common.beans.BeansContainer;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.service.util.CmdAction;

public abstract class AbsCmdAction implements CmdAction {

    protected LocalDate beginDate;
    protected LocalDate endDate;
    protected List<PriceLevel> levels = new ArrayList<>();
    protected List<Exchangeable> instruments = new ArrayList<>();
    protected String outputFile;

    protected void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "instrument":
                Exchangeable instrument = Exchangeable.fromString(kv.v);
                instruments.add(instrument);
                break;
            case "instruments":
                for(String e:StringUtil.split(kv.v, ",|;")) {
                    instruments.add(Exchangeable.fromString(e));
                }
                break;
            case "begindate":
                beginDate = DateUtil.str2localdate(kv.v);
                break;
            case "enddate":
                endDate = DateUtil.str2localdate(kv.v);
                break;
            case "level":
                PriceLevel level = PriceLevel.valueOf(kv.v.toLowerCase());
                levels.add(level);
                break;
            case "levels":
                for(String levelStr:StringUtil.split(kv.v, ",|;")) {
                    levels.add(PriceLevel.valueOf(levelStr));
                }
                break;
            case "outputfile":
                this.outputFile = kv.v;
                break;
            }
        }
        if (beginDate==null) {
            beginDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
        if (endDate==null) {
            endDate = MarketDayUtil.lastMarketDay(Exchange.SHFE, false);
        }
    }

}
