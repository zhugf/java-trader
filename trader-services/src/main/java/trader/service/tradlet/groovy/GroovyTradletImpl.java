package trader.service.tradlet.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import trader.common.beans.Discoverable;
import trader.service.md.MarketData;
import trader.service.ta.LeveledTimeSeries;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletContext;

/**
 * 基于GROOVY脚本的Tradlet
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "GROOVY")
public class GroovyTradletImpl implements Tradlet {

    private Binding binding;
    private GroovyClassLoader scriptLoader;
    private Class<GroovyScriptBase> scriptClass;
    private GroovyScriptBase script;

    @Override
    public void init(TradletContext context) throws Exception {

    }

    @Override
    public void reload(TradletContext context) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void destroy() {

    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {

    }

    @Override
    public void onTick(MarketData marketData) {

    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {

    }

    @Override
    public void onNoopSecond() {

    }

}
