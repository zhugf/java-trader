package trader.service.tradlet.impl.cta;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.univocity.parsers.fixed.FieldAlignment;
import com.univocity.parsers.fixed.FixedWidthFieldLengths;
import com.univocity.parsers.fixed.FixedWidthFields;
import com.univocity.parsers.fixed.FixedWidthWriter;
import com.univocity.parsers.fixed.FixedWidthWriterSettings;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.MarketDayUtil;
import trader.common.tick.PriceLevel;
import trader.common.util.DateUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.TraderHomeUtil;
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
        fields.addField("HINT ID", 17);
        fields.addField("RULE ID", 25);
        fields.addField("ENABLED", 8);
        fields.addField("INSTRUMENT", 13);
        fields.addField("DIR", 5);
        fields.addField("ENTER", 8);
        fields.addField("STOP", 8);
        fields.addField("TAKE", 8);
        fields.addField("VOL", 5);

        FixedWidthWriterSettings settings = new FixedWidthWriterSettings(fields);
        settings.setNullValue("?");

        FixedWidthWriter fwWriter = new FixedWidthWriter(writer, settings);
        fwWriter.writeHeaders();

        for(CTAHint hint : CTAHint.loadHints(file, tradingDay)) {
            if ( hint.finished ) {
                finishedHints.add(hint);
                continue;
            }
            for(CTARule rule:hint.rules) {
                fwWriter.writeRow(
                        hint.id,
                        rule.id,
                        rule.disabled?"N":"Y",
                        hint.instrument.uniqueId(),
                        rule.dir==PosDirection.Long?"L":"S",
                        PriceUtil.long2price(rule.enter),
                        PriceUtil.long2price(rule.stop),
                        PriceUtil.long2price(rule.take),
                        rule.volume
                        );
            }
        }
        fwWriter.flush();
        return 0;
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
