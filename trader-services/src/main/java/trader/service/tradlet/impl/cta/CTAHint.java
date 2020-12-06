package trader.service.tradlet.impl.cta;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.JsonEnabled;
import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 代表一条CTA策略, 可以有多条具体的规则.
 */
public class CTAHint implements JsonEnabled {
    private final static Logger logger = LoggerFactory.getLogger(CTAHint.class);
    /**
     * 指定ID, 如未指定,使用 instrument:dayRange格式
     */
    public final String id;
    /**
     * instrument
     */
    public final Exchangeable instrument;
    /**
     * dayRange: dayBegin-dayEnd;
     */
    public final LocalDate dayBegin;
    public final LocalDate dayEnd;
    /**
     * 方向: long/short
     */
    public final PosDirection dir;
    /**
     * 手工明确启用/禁用. 禁用的策略只能平仓, 不可开仓
     */
    public final boolean disabled;

    /**
     * 明确结束, 结束的策略不会被加载
     */
    public final boolean finished;

    public final CTARule[] rules;

    public CTAHint(Element elem, LocalDate tradingDay) {
        String instrumentId = elem.getAttributeValue("instrument");
        instrument = Exchangeable.fromString(instrumentId);
        String dayRange = elem.getAttributeValue("dayRange");
        String id = elem.getAttributeValue("id");
        dayBegin = DateUtil.str2localdate(StringUtil.split(dayRange, "-")[0]);
        dayEnd = DateUtil.str2localdate(StringUtil.split(dayRange, "-")[1]);
        if (StringUtil.isEmpty(id)) {
            id = instrumentId+"-"+DateUtil.date2str(dayBegin);
        }
        this.id = id;
        dir = ConversionUtil.toEnum(PosDirection.class, elem.getAttributeValue("dir"));
        disabled = ConversionUtil.toBoolean(elem.getAttributeValue("disabled"));
        finished = ConversionUtil.toBoolean(elem.getAttributeValue("finished"));
        List<CTARule> rules = new ArrayList<>();
        int policyIdx=0;
        for(Element elem0:elem.getChildren("rule")) {
            rules.add( new CTARule(this, policyIdx, elem0, tradingDay) );
            policyIdx++;
        }
        this.rules = rules.toArray(new CTARule[rules.size()]);
    }

    /**
     * 该交易日是否有效?
     */
    public boolean isValid(LocalDate tradingDay) {
        return !disabled && tradingDay.compareTo(dayBegin)>=0 && tradingDay.compareTo(dayEnd)<=0;
    }

    /**
     * 从文件加载全部hint
     */
    public static List<CTAHint> loadHints(File file, LocalDate tradingDay) throws Exception
    {
        List<CTAHint> hints = new ArrayList<>();
        try(FileInputStream fis = new FileInputStream(file);){
            Document doc = (new SAXBuilder()).build(fis);
            Element root = doc.getRootElement();
            for(Element hintElem:root.getChildren("hint")) {
                hints.add(new CTAHint(hintElem, tradingDay));
            }
        }
        return hints;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("instrument", instrument.toString());
        json.addProperty("dir", dir.name());
        json.addProperty("disabled", disabled);
        json.addProperty("dayBegin", DateUtil.date2str(dayBegin));
        json.addProperty("dayEnd", DateUtil.date2str(dayEnd));
        json.add("rules", JsonUtil.object2json(rules));
        return json;
    }

}
