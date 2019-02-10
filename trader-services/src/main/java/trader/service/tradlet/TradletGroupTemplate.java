package trader.service.tradlet;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import trader.common.beans.BeansContainer;
import trader.common.exception.AppException;
import trader.common.exchangeable.Exchangeable;
import trader.common.util.ConversionUtil;
import trader.common.util.IniFile;
import trader.service.ServiceErrorCodes;
import trader.service.trade.Account;
import trader.service.trade.TradeService;

/**
 * 代表从配置解析后的TradletGroup的配置项.
 * <BR>只读
 */
public class TradletGroupTemplate implements ServiceErrorCodes, TradletConstants {
    String config;
    TradletGroupState state = TradletGroupState.Enabled;
    Exchangeable exchangeable;
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
            String exchangeableStr = props.getProperty("exchangeable");
            if ( props.containsKey("exchangeable")) {
                template.exchangeable = Exchangeable.fromString(exchangeableStr);
            }
            template.account = tradeService.getAccount(props.getProperty("account"));
            if (template.account==null) {
                throw new AppException(ERR_TRADLET_INVALID_ACCOUNT_VIEW, "策略组 "+group.getId()+" 账户 "+props.getProperty("account")+" 不存在");
            }
            if ( props.containsKey("state")) {
                template.state = ConversionUtil.toEnum(TradletGroupState.class, props.getProperty("state"));
            }
            if ( template.exchangeable==null ) {
                throw new AppException(ERR_TRADLET_INVALID_EXCHANGEABLE, "策略组 "+group.getId()+" 交易品种 "+exchangeableStr+" 不存在");
            }
        }
        {
            for(IniFile.Section section:groupConfig.getAllSections()) {
                if ( section.getName().equals("common")) {
                    continue;
                }
                Properties props = section.getProperties();
                TradletHolder tradletHolder = createTradlet(tradletService, group, template, props);
                template.tradletHolders.add(tradletHolder);
            }
        }
        return template;
    }

    /**
     * 创建并初始化Tradlet
     */
    private static TradletHolder createTradlet(TradletService tradletService, TradletGroupImpl group, TradletGroupTemplate template, Properties props) throws AppException
    {
        String tradletId = props.getProperty("tradletId");
        TradletInfo tradletInfo = tradletService.getTradletInfo(tradletId);
        if ( tradletInfo==null ) {
            throw new AppException(ERR_TRADLET_TRADLET_NOT_FOUND, "不存在的 Tradlet : "+tradletId);
        }
        props.remove("id");

        Tradlet tradlet = null;
        try{
            tradlet = (Tradlet)tradletInfo.getTradletClass().getConstructor().newInstance();
        }catch(Throwable t) {
            throw new AppException(t, ERR_TRADLET_TRADLET_CREATE_FAILED, "Tradlet "+tradletId+" 创建失败: "+t.toString());
        }
        return new TradletHolder(tradletId, tradlet, new TradletContextImpl(group, props));
    }

}
