package trader.service.tradlet;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.tick.PriceLevel;
import trader.common.util.ConversionUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil;
import trader.service.ServiceErrorCodes;
import trader.service.md.MarketDataService;
import trader.service.trade.Account;
import trader.service.trade.TradeService;

/**
 * 代表从配置解析后的TradletGroup的配置项.
 * <BR>只读
 */
public class TradletGroupTemplate implements ServiceErrorCodes, TradletConstants {
    String config;
    TradletGroupState state = TradletGroupState.Enabled;
    List<Exchangeable> instruments;
    List<PriceLevel> priceLevels;
    List<TradletHolder> tradletHolders = new ArrayList<>();
    Account account;

    public static TradletGroupTemplate parse(BeansContainer beansContainer, TradletGroupImpl group, String configText) throws AppException
    {
        TradeService tradeService = beansContainer.getBean(TradeService.class);
        TradletService tradletService = beansContainer.getBean(TradletService.class);
        TradletGroupTemplate template = new TradletGroupTemplate();
        template.config = configText;
        IniFile groupConfig = null;
        try{
            groupConfig = new IniFile(new StringReader(configText));
        }catch(Throwable t){
            throw new AppException(t, ERR_TRADLET_TRADLETGROUP_INVALID_CONFIG, "策略组 "+group.getId()+" 加载配置文本失败: "+configText);
        }
        IniFile.Section commonSection = groupConfig.getSection("common");
        {
            Properties props = commonSection.getProperties();
            String accountStr = props.getProperty("account");
            template.account = tradeService.getAccount(accountStr);
            String instrumentStr = props.getProperty("instrument");
            if ( props.containsKey("instruments")) {
                instrumentStr = props.getProperty("instruments");
            }
            if ( !StringUtil.isEmpty(instrumentStr)){
                List<Exchangeable> instruments = new ArrayList<>();
                for(String instrument:StringUtil.split(instrumentStr, ",|;")) {
                    instruments.add(resolveInstrument(beansContainer,instrument));
                }
                Collections.sort(instruments);
                template.instruments = instruments;
            }
            template.priceLevels = new ArrayList<>();
            if ( props.containsKey("priceLevels")) {
                for(String level:StringUtil.split(props.getProperty("priceLevels"), ",|;")) {
                    template.priceLevels.add(PriceLevel.valueOf(level));
                }
            } else {
                //缺省1分钟
                template.priceLevels.add(PriceLevel.MIN1);
            }
            if (!StringUtil.isEmpty(accountStr) && template.account==null) {
                throw new AppException(ERR_TRADLET_INVALID_ACCOUNT_VIEW, "策略组 "+group.getId()+" 账户 "+props.getProperty("account")+" 不存在");
            }
            if ( props.containsKey("state")) {
                template.state = ConversionUtil.toEnum(TradletGroupState.class, props.getProperty("state"));
            }
            if ( template.instruments==null ) {
                throw new AppException(ERR_TRADLET_INVALID_EXCHANGEABLE, "策略组 "+group.getId()+" 交易品种不存在");
            }
        }
        {
            for(IniFile.Section section:groupConfig.getAllSections()) {
                if ( section.getName().equals("common")) {
                    continue;
                }
                TradletHolder tradletHolder = createTradlet(tradletService, group, template, section);
                template.tradletHolders.add(tradletHolder);
            }
        }
        return template;
    }

    /**
     * 创建并初始化Tradlet
     */
    private static TradletHolder createTradlet(TradletService tradletService, TradletGroupImpl group, TradletGroupTemplate template, IniFile.Section section) throws AppException
    {
        String tradletId = section.getName();
        TradletInfo tradletInfo = tradletService.getTradletInfo(tradletId);
        if ( tradletInfo==null ) {
            throw new AppException(ERR_TRADLET_TRADLET_NOT_FOUND, "不存在的 Tradlet : "+tradletId);
        }

        Tradlet tradlet = null;
        try{
            tradlet = (Tradlet)tradletInfo.getTradletClass().getConstructor().newInstance();
        }catch(Throwable t) {
            throw new AppException(t, ERR_TRADLET_TRADLET_CREATE_FAILED, "Tradlet "+tradletId+" 创建失败: "+t.toString());
        }
        return new TradletHolder(tradletId, tradlet, new TradletContextImpl(group, section.getText()));
    }

    private static Exchangeable resolveInstrument(BeansContainer beansContainer, String instrumentId) throws AppException
    {
        Exchangeable result = null;
        if ( instrumentId.startsWith("$")) {
            MarketDataService mdService= beansContainer.getBean(MarketDataService.class);
            result = mdService.getPrimaryInstrument(null, instrumentId.substring(1));
            if ( result==null ) {
                throw new AppException(ERR_TRADLET_INVALID_INSTRUMENT, "不存在的合约 : "+instrumentId);
            }
        } else {
            try{
                result = Exchangeable.fromString(instrumentId);
            }catch(RuntimeException re) {
                throw new AppException(ERR_TRADLET_INVALID_INSTRUMENT, "不存在的合约 : "+instrumentId);
            }
        }
        return result;
    }
}
