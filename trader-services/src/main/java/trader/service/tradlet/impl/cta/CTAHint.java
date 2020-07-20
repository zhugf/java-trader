package trader.service.tradlet.impl.cta;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.StringUtil;
import trader.service.trade.TradeConstants.PosDirection;

/**
 * 代表一条CTA策略
 */
public class CTAHint {
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
     * 手工明确启用/禁用
     */
    public final boolean disabled;

    public final CTABreakRule[] rules;

    public CTAHint(Element elem) {
        instrument = Exchangeable.fromString(elem.getAttributeValue("instrument"));
        String dayRange = elem.getAttributeValue("dayRange");
        String id = elem.getAttributeValue("id");
        if (StringUtil.isEmpty(id)) {
            id = elem.getAttributeValue("instrument")+":"+dayRange;
        }
        this.id = id;
        dayBegin = DateUtil.str2localdate(StringUtil.split(dayRange, "-")[0]);
        dayEnd = DateUtil.str2localdate(StringUtil.split(dayRange, "-")[1]);
        dir = ConversionUtil.toEnum(PosDirection.class, elem.getAttributeValue("dir"));
        disabled = ConversionUtil.toBoolean(elem.getAttributeValue("disabled"));

        List<CTABreakRule> rules = new ArrayList<>();
        int policyIdx=0;
        for(Element elem0:elem.getChildren()) {
            switch(elem0.getName()) {
            case "break":
                rules.add( new CTABreakRule(this, policyIdx, elem0) );
                policyIdx++;
                break;
            }
        }
        this.rules = rules.toArray(new CTABreakRule[rules.size()]);
    }

    /**
     * 该交易日是否有效?
     */
    public boolean isValid(LocalDate tradingDay) {
        return !disabled && tradingDay.compareTo(dayBegin)>=0 && tradingDay.compareTo(dayEnd)<=0;
    }

    /**
     * 从文件加载全部有效hint
     */
    public static List<CTAHint> loadHints(File file, LocalDate tradingDay) throws Exception
    {
        List<CTAHint> hints = new ArrayList<>();
        try(FileInputStream fis = new FileInputStream(file);){
            Document doc = (new SAXBuilder()).build(fis);
            Element root = doc.getRootElement();
            for(Element hintElem:root.getChildren("hint")) {
                CTAHint hint = new CTAHint(hintElem);
                if ( hint.isValid(tradingDay)) {
                    hints.add(hint);
                }
            }
        }
        return hints;
    }

}
