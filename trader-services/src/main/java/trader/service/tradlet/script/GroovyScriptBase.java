package trader.service.tradlet.script;

import java.util.Formatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.Binding;
import groovy.lang.Script;


/**
 * Groovy脚本的基类
 */
public abstract class GroovyScriptBase extends Script {
    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptBase.class);
    private String id;
    private String lastPrintText="";
    private ScriptContext context;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setContext(ScriptContext context) {
        this.context = context;
    }

    @Override
    public Object getProperty(String property) {
        Object result = null;
        Binding binding = getBinding();
        //先判断本地局部变量是否存在
        if ( binding.hasVariable(property) ) {
            result = binding.getVariable(property);
            return result;
        }
        //再判断标准变量是否存在
        result = context.varGet(property);
        //再回头找一遍
        if ( result==null ) {
            result = super.getProperty(property);
        }
        return result;
    }

    /**
     * 对于部分只读变量, 需要保护起来
     */
    @Override
    public void setProperty(String property, Object newValue) {
        Binding binding = getBinding();
        //先判断本地局部变量是否存在
        if ( binding.hasVariable(property) ) {
            binding.setVariable(property, newValue);
            return;
        }
        //标准变量不允许设置
        if ( context.varExists(property)) {
            return;
        }
        //旧的设置变量方式
        super.setProperty(property, newValue);
    }

    /**
     * 在这里调用函数
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        if ( context.funcExists(name)) {
            return context.funcInvoke(name, (Object[])args);
        }
        return super.invokeMethod(name, args);
    }

    @Override
    public void print(Object value) {
        lastPrintText += value.toString();
    }

    @Override
    public void println(Object value) {
        logger.info(id+" : "+lastPrintText+value.toString());
        lastPrintText = "";
    }

    @Override
    public void println() {
        logger.info(id+" : "+lastPrintText);
        lastPrintText = "";
    }

    @Override
    public void printf(String format, Object value) {
        StringBuilder builder = new StringBuilder(128);
        builder.append(id).append(" : ");
        Formatter formatter = new Formatter(builder);
        formatter.format(Locale.getDefault(), format, value);
        formatter.close();
        logger.info(builder.toString());
    }

    @Override
    public void printf(String format, Object[] values) {
        StringBuilder builder = new StringBuilder(128);
        builder.append(id).append(" : ");
        Formatter formatter = new Formatter(builder);
        formatter.format(Locale.getDefault(), format, values);
        formatter.close();
        logger.info(builder.toString());
    }

}
