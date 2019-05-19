package trader.service.tradlet.script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.TimeSeries;

import groovy.lang.GroovyClassLoader;
import trader.common.beans.BeansContainer;
import trader.common.beans.Discoverable;
import trader.common.beans.Lifecycle;
import trader.common.config.ConfigUtil;
import trader.common.util.StringUtil;
import trader.service.beans.DiscoverableRegistry;
import trader.service.md.MarketData;
import trader.service.plugin.Plugin;
import trader.service.plugin.PluginService;
import trader.service.ta.Bar2;
import trader.service.ta.LeveledTimeSeries;
import trader.service.ta.indicators.SimpleIndicator;
import trader.service.tradlet.Playbook;
import trader.service.tradlet.PlaybookStateTuple;
import trader.service.tradlet.Tradlet;
import trader.service.tradlet.TradletContext;
import trader.service.tradlet.TradletGroup;
import trader.service.tradlet.TradletServiceImpl;
import trader.service.tradlet.script.func.ABSFunc;
import trader.service.tradlet.script.func.CROSSFunc;
import trader.service.tradlet.script.func.EMAFunc;
import trader.service.tradlet.script.func.HHVFunc;
import trader.service.tradlet.script.func.LLVFunc;
import trader.service.tradlet.script.func.MAXFunc;
import trader.service.tradlet.script.func.MERGEFunc;
import trader.service.tradlet.script.func.REFFunc;
import trader.service.tradlet.script.func.SMAFunc;

/**
 * 基于GROOVY脚本的Tradlet
 */
@Discoverable(interfaceClass = Tradlet.class, purpose = "GROOVY")
public class GroovyTradletImpl implements Tradlet, ScriptContext {
    private static final Logger logger = LoggerFactory.getLogger(GroovyTradletImpl.class);

    private TradletGroup group;
    private BeansContainer beansContainer;
    private Map<String, Class<TradletScriptFunction>> functionClasses = new HashMap<>();
    private Map<String, TradletScriptFunction> functions = new HashMap<>();

    private Map<String, Object> variables = new HashMap<>();

    private GroovyClassLoader scriptLoader;
    private Class<GroovyScriptBase> scriptClass;
    private GroovyScriptBase script;

    private GroovyScriptMethodInfo methodOnTick;
    private GroovyScriptMethodInfo methodOnNewBar;
    private GroovyScriptMethodInfo methodOnNoopSecond;

    @Override
    public void init(TradletContext context) throws Exception {
        this.group = context.getGroup();
        this.beansContainer = context.getBeansContainer();
        this.functionClasses = loadStandardScriptFunctionClasses();
        this.functionClasses.putAll(discoverPluginScriptFunctions(beansContainer.getBean(PluginService.class)));
        logger.info("Tradlet group "+group.getId()+" discoverd functions: "+new TreeSet<>(functionClasses.keySet()));

        CompilerConfiguration scriptConfig = new CompilerConfiguration();
        scriptConfig.setTargetBytecode(CompilerConfiguration.JDK8);
        scriptConfig.setRecompileGroovySource(false);
        scriptConfig.setScriptBaseClass(GroovyScriptBase.class.getName());
        scriptLoader = new GroovyClassLoader(getClass().getClassLoader(), scriptConfig);

        reload(context);
    }

    @Override
    public void reload(TradletContext context) throws Exception
    {
        try{
            scriptClass = scriptLoader.parseClass(context.getConfigText());
            script = scriptClass.getDeclaredConstructor().newInstance();
            script.setId(group.getId());
            script.setContext(this);

            methodOnTick = new GroovyScriptMethodInfo(script, "onTick");
            methodOnNewBar = new GroovyScriptMethodInfo(script, "onNewBar");
            methodOnNoopSecond = new GroovyScriptMethodInfo(script, "onNoopSecond");
        }catch(Exception e) {
            logger.error("Tradlet group compile script "+context.getConfigText()+" failed: "+e, e);
            scriptClass = null;
            script = null;
            methodOnTick = null;
            methodOnNewBar = null;
            methodOnNoopSecond = null;
        }

        if ( script!=null ) {
            GroovyScriptMethodInfo methodOnInit = new GroovyScriptMethodInfo(script, "onInit");
            methodOnInit.invoke(new Object[] {context});
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onPlaybookStateChanged(Playbook playbook, PlaybookStateTuple oldStateTuple) {

    }

    @Override
    public void onTick(MarketData tick) {
        if ( methodOnTick!=null ) {
            methodOnTick.invoke(new Object[] {tick});
        }
    }

    @Override
    public void onNewBar(LeveledTimeSeries series) {
        //准备变量
        if ( methodOnNewBar!=null && prepareVars(series) ) {
            methodOnNewBar.invoke(new Object[] {series});
        }
    }

    @Override
    public void onNoopSecond() {
        if( methodOnNoopSecond!=null ) {
            methodOnNoopSecond.invoke(null);
        }
    }

    //--------------------- 脚本访问变量/函数的回调接口

    @Override
    public boolean varExists(String varName) {
        return variables.containsKey(varName);
    }

    @Override
    public Object varGet(String varName) {
        return getOrCreateVar(varName);
    }

    @Override
    public boolean funcExists(String funcName) {
        return functionClasses.containsKey(funcName);
    }

    @Override
    public Object funcInvoke(String funcName, Object[] args) {
        TradletScriptFunction func = getOrCreateFuncton(funcName);
        Object result;
        try {
            result = func.invoke(args);
        } catch (Exception e) {
            throw new InvokerInvocationException(e);
        }
        if ( logger.isDebugEnabled() ) {
            logger.debug("Tradlet group "+group.getId()+" invokes function "+funcName+" "+Arrays.asList(args)+" returns: "+result);
        }
        return result;
    }

    /**
     * 准备OHLC标准变量. 这个方法忽略新创建的Bar, 只返回已完成的KBAR
     */
    private boolean prepareVars(LeveledTimeSeries series) {
        if ( series.getBarCount()<=1 ) {
            variables.clear();
            return false;
        }
        TimeSeries subSeries = series.getSubSeries(series.getBeginIndex(), series.getEndIndex());
        variables.put("OPEN", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getOpenPrice();
        })));
        variables.put("CLOSE", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getClosePrice();
        })));
        variables.put("HIGH", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getMaxPrice();
        })));
        variables.put("LOW", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getMinPrice();
        })));
        variables.put("VOLUME", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getVolume();
        })));
        variables.put("AMOUNT", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getAmount();
        })));
        variables.put("AVERAGE", new GroovyIndicatorValue(SimpleIndicator.createFromSeries(subSeries, (Bar2 bar)->{
            return bar.getAvgPrice();
        })));
        return true;
    }

    /**
     * 按需访问变量
     */
    private Object getOrCreateVar(String varName) {
        return variables.get(varName);
    }

    /**
     * 按需创建函数的实现类
     */
    private TradletScriptFunction getOrCreateFuncton(String funcName) {
        TradletScriptFunction result = functions.get(funcName);
        if ( result==null ) {
            Class<TradletScriptFunction> funcClass = functionClasses.get(funcName);
            if ( funcClass!=null ) {
                try{
                    result = funcClass.getDeclaredConstructor().newInstance();
                    if ( result instanceof Lifecycle ) {
                        ((Lifecycle)result).init(beansContainer);
                    }
                    functions.put(funcName, result);
                } catch(Throwable t) {
                    logger.error("Tradlet script function "+funcName+" init failed from class "+funcClass+" : "+t, t);
                }
            }
        }
        return result;
    }

    /**
     * 加载标准函数实现
     */
    public static Map<String, Class<TradletScriptFunction>> loadStandardScriptFunctionClasses(){
        Map<String, Class<TradletScriptFunction>> funcClasses = new HashMap<>();
        //硬编码加载的函数列表
        Class<TradletScriptFunction>[] knownClasses = new Class[] {
            ABSFunc.class
            ,CROSSFunc.class
            ,EMAFunc.class
            ,HHVFunc.class
            ,LLVFunc.class
            ,SMAFunc.class
            ,REFFunc.class
            ,MAXFunc.class
            ,MERGEFunc.class
        };
        for(Class<TradletScriptFunction> knownClass: knownClasses) {
            Discoverable anno = knownClass.getAnnotation(Discoverable.class);
            if ( anno!=null ) {
                funcClasses.put(anno.purpose(), knownClass);
            } else {
                funcClasses.put(knownClass.getSimpleName(), knownClass);
            }
        }
        //从配置文件读取的函数列表
        for(String funcClazz : StringUtil.text2lines(ConfigUtil.getString(TradletServiceImpl.ITEM_TRADLETS), true, true)) {
            Class<TradletScriptFunction> clazz;
            try {
                clazz = (Class<TradletScriptFunction>)Class.forName(funcClazz);
                Discoverable anno = clazz.getAnnotation(Discoverable.class);
                if ( anno!=null ) {
                    funcClasses.put(anno.purpose(), clazz);
                } else {
                    funcClasses.put(clazz.getSimpleName(), clazz);
                }
            } catch (Throwable t) {
                logger.error("Load script func class "+funcClazz+" failed: "+t.toString(), t);
            }
        }
        //从主Classpath发现的函数列表
        Map<String, Class<TradletScriptFunction>> discoveredTradlets = DiscoverableRegistry.getConcreteClasses(TradletScriptFunction.class);
        if ( discoveredTradlets!=null ) {
            funcClasses.putAll(discoveredTradlets);
        }
        return funcClasses;
    }

    /**
     * 从插件加载函数实现类
     */
    private Map<String, Class<TradletScriptFunction>> discoverPluginScriptFunctions(PluginService pluginService){
        Map<String, Class<TradletScriptFunction>> result = new HashMap<>();
        if ( pluginService!=null ) {
            for(Plugin plugin:pluginService.getAllPlugins()) {
                Map<String, Class<TradletScriptFunction>> funcClasses = plugin.getBeanClasses(TradletScriptFunction.class);
                result.putAll(funcClasses);
            }
        }
        return result;
    }

}
