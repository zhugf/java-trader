package trader.service.trade.broker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.exchangeable.Exchange;
import trader.common.exchangeable.Exchangeable;
import trader.common.exchangeable.ExchangeableUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.NetUtil;
import trader.common.util.StringUtil;
import trader.common.util.NetUtil.HttpMethod;
import trader.service.trade.TxnMarginRatioLoader;
import trader.service.trade.TradeConstants.MarginRatio;

/**
 * 宏源期货合约保证金比率加载
 */
@Discoverable(interfaceClass=TxnMarginRatioLoader.class, purpose="hyqh")
public class HongyuanTxnMarginRatioLoader implements TxnMarginRatioLoader {
    private final static Logger logger = LoggerFactory.getLogger(HongyuanTxnMarginRatioLoader.class);

    /**
     * 宏源期货的公告列表页面
     */
    private static final String URL_LIST_PAGE = "http://www.hongyuanqh.com/hyqhnew/public/infolist.jsp?1=1&oneMenuId=000200010008&twoMenuId=0002000100080006&biaoji=infolist";
    private static final String URL_HOST = "http://www.hongyuanqh.com";

    private static Pattern PATTERN_MARGIN_DAY = Pattern.compile("保证金标准.*\\d{8}");

    @Override
    public void init(BeansContainer beansContainer) throws Exception
    {

    }

    @Override
    public void destroy() {
    }

    @Override
    public Map<Exchangeable, double[]> load(Collection<Exchangeable> instruments) throws Exception {
        String pdfPageUrl = null;
        String pdfUrl = null;
        String pdfContent = null;
        Map<Exchangeable, Double> instrumentMarginRatios = new HashMap<>();
        //查询宏源公告页面
        String listPage = NetUtil.readHttpAsText(URL_LIST_PAGE, StringUtil.UTF8);
        if ( logger.isDebugEnabled()) {
            logger.debug("HTTP "+URL_LIST_PAGE+" 返回:\n"+listPage);
        }

        Document doc = Jsoup.parse(listPage, URL_LIST_PAGE);
        Elements links = doc.select("a[href]");
        for(Element a:links) {
            String title = a.attributes().get("title");
            String href = a.attributes().get("href");
            if (!PATTERN_MARGIN_DAY.matcher(title).matches()) {
                continue;
            }
            pdfPageUrl = URL_HOST+href;
            break;
        }
        if ( pdfPageUrl!=null ) {
            String pdfPage=NetUtil.readHttpAsText(pdfPageUrl, StringUtil.UTF8);
            if ( logger.isDebugEnabled()) {
                logger.debug("HTTP "+pdfPageUrl+" 返回:\n"+pdfPage);
            }
            doc = Jsoup.parse(pdfPage, pdfPageUrl);
            links = doc.select("a[href]");
            for(Element a:links) {
                String href = a.attributes().get("href");
                if ( href.endsWith("pdf")) {
                    pdfUrl = URL_HOST+href;
                    break;
                }
            }
        }
        if ( null!=pdfUrl ) {
            StringWriter pdfWriter = new StringWriter();
            ByteArrayOutputStream os = new ByteArrayOutputStream(65536);
            NetUtil.readHttp(pdfUrl, HttpMethod.GET, null, StringUtil.UTF8, null, os);
            PDFParser pdfParser =  new PDFParser();
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(pdfWriter);
            ParseContext context = new ParseContext();
            pdfParser.parse(new ByteArrayInputStream(os.toByteArray()), handler, metadata, context);
            pdfContent = pdfWriter.toString();
            if ( logger.isInfoEnabled()) {
                logger.info("HTTP "+pdfUrl+" 返回:\n"+pdfContent);
            }
        }
        if ( null!=pdfContent ) {
            ArrayList<String> exchangeNames = new ArrayList<>();
            for(Exchange exchange:Exchange.getInstances()) {
                exchangeNames.add(exchange.name().toUpperCase());
            }
            //CFFEX TS 2年期国债 0.01 0.01 20000
            //CZCE AP 苹果 0.14 0.14 10
            //CZCE AP012 0.16 0.16
            for(String line:StringUtil.text2lines(pdfContent, true, true)) {
                String parts[] = StringUtil.split(line, " ");
                if (!exchangeNames.contains(parts[0])) {
                    continue;
                }
                String exchangeName = parts[0];
                String instrumentId = parts[1];
                String marginRatio = null;
                if ( parts.length>=5 ) {
                    marginRatio = parts[3];
                } else if ( parts.length==4 ) {
                    marginRatio = parts[2];
                } else {
                    logger.error("保证金行解析失败: "+line);
                }
                if (!StringUtil.isEmpty(marginRatio)) {
                    Exchangeable instrument = Exchangeable.fromString(exchangeName, instrumentId);
                    instrumentMarginRatios.put(instrument, ConversionUtil.toDouble(marginRatio));
                }
            }
        }
        Map<Exchangeable, double[]> result = new HashMap<>();
        for(Exchangeable instrument:instruments) {
            Double marginRatio = instrumentMarginRatios.get(instrument);
            if ( null==marginRatio ) {
                Exchangeable contract = Exchangeable.fromString(instrument.exchange().name(), instrument.contract());
                marginRatio = instrumentMarginRatios.get(contract);
            }
            if ( null!=marginRatio ) {
                double[] marginRatios = new double[MarginRatio.values().length];
                marginRatios[MarginRatio.LongByMoney.ordinal()]= marginRatio;
                marginRatios[MarginRatio.ShortByMoney.ordinal()]= marginRatio;
                result.put(instrument, marginRatios);
            }
        }
        return result;
    }

    public static void main(String[] args) throws Throwable
    {
        HongyuanTxnMarginRatioLoader loader = new HongyuanTxnMarginRatioLoader();

        List<Exchangeable> primaryInstruments = new ArrayList<>();
        List<Exchangeable> primaryInstruments2 = new ArrayList<>();
        ExchangeableUtil.queryFuturePrimaryInstruments(primaryInstruments, primaryInstruments2);
        Object result = loader.load(primaryInstruments);
        System.out.println(result);
    }

}
