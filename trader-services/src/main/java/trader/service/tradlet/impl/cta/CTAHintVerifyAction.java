package trader.service.tradlet.impl.cta;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.univocity.parsers.fixed.FieldAlignment;
import com.univocity.parsers.fixed.FixedWidthFieldLengths;
import com.univocity.parsers.fixed.FixedWidthFields;
import com.univocity.parsers.fixed.FixedWidthWriter;
import com.univocity.parsers.fixed.FixedWidthWriterSettings;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.config.ConfigUtil;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
import trader.service.ta.TechnicalAnalysisService;
import trader.service.trade.TradeConstants.PosDirection;
import trader.service.tradlet.Tradlet;
import trader.service.util.CmdAction;

@Discoverable(interfaceClass = CmdAction.class, purpose = "cta.verifyHint")
public class CTAHintVerifyAction implements CmdAction {

    private File file;

    @Override
    public String getCommand() {
        return "cta.verifyHint";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("cta verifyHint --file=<hintFile>");
        writer.println("\tCTA 策略检查配置文件正确性");

    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception
    {
        parseOptions(options);
        if ( null==file ) {
            file = new File( TraderHomeUtil.getDirectory(TraderHomeUtil.DIR_ETC), "cta-hints.xml");
        }
        LocalDate tradingDay = LocalDate.now();
        List<CTAHint> finishedHints = new ArrayList<>();
        FixedWidthFields fields = new FixedWidthFields();
        fields.addField("RULE ID", 25);
        fields.addField("INSTRUMENT", 13);
        fields.addField("BEGIN TIME", 25);
        fields.addField("END TIME", 25);
        fields.addField("DIR", 5);
        fields.addField("ENTER", 9);
        fields.addField("STOP", 9);
        fields.addField("FSTOP", 9);
        fields.addField("TAKE", 9);
        fields.addField("VOL", 5);

        FixedWidthWriterSettings settings = new FixedWidthWriterSettings(fields);
        settings.setNullValue("?");

        FixedWidthWriter fwWriter = new FixedWidthWriter(writer, settings);
        fwWriter.writeHeaders();
        List<CTAHint> ctaHints = CTAHint.loadHints(file, tradingDay);
        for(CTAHint hint : ctaHints) {
            if ( hint.finished ) {
                finishedHints.add(hint);
                continue;
            }
            for(CTARule rule:hint.rules) {
                fwWriter.writeRow(
                        rule.id,
                        hint.instrument.uniqueId(),
                        DateUtil.date2str(hint.beginTime),
                        DateUtil.date2str(hint.endTime),
                        rule.dir==PosDirection.Long?"L":"S",
                        PriceUtil.long2price(rule.enter),
                        PriceUtil.long2price(rule.stop),
                        PriceUtil.long2price(rule.floatStop),
                        PriceUtil.long2price(rule.take),
                        rule.volume
                        );
            }
        }
        fwWriter.flush();

        verifyTraderConfig(writer, ctaHints);
        return 0;
    }

    /**
     * 检查trader.xml中是否有对应的TA配置
     */
    protected void verifyTraderConfig(PrintWriter writer, List<CTAHint> ctaHints) {
        List<Map> intrumentConfigs = (List<Map>)ConfigUtil.getObject(TechnicalAnalysisService.ITEM_INSTRUMENTS);
        Set<String> contracts = new TreeSet<>();
        for(Map config:intrumentConfigs) {
            Exchangeable instrument = Exchangeable.fromString((String)config.get("id"));
            contracts.add(instrument.contract());
        }
        List<Exchangeable> missedInstruments = new ArrayList<>();
        for(CTAHint hint:ctaHints) {
            if ( hint.finished ) {
                continue;
            }
            if ( !contracts.contains(hint.instrument.contract()) ){
                missedInstruments.add(hint.instrument);
            }
        }
        if ( missedInstruments.size()>0 ) {
            writer.println("trader.xml配置文件缺少 TechnicalAnalysisService 合约配置: "+missedInstruments);
        }
    }

    protected void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "file":
                file = new File(kv.v);
                break;
            }
        }
    }

}
