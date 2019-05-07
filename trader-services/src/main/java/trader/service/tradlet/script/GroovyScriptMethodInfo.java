package trader.service.tradlet.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.MetaMethod;

class GroovyScriptMethodInfo {
    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptMethodInfo.class);

    private static final Object[] EMPTY_ARGUMENTS = {};

    private GroovyScriptBase script;
    private boolean methodChecked = false;
    MetaMethod method;
    private String name;

    public GroovyScriptMethodInfo(GroovyScriptBase script, String name) {
        this.script = script;
        this.name = name;
    }

    public Object invoke(Object[] args) {
        if ( args==null ) {
            args = EMPTY_ARGUMENTS;
        }
        if ( !methodChecked ) {
            method = script.getMetaClass().getMetaMethod(name, args);
            methodChecked = true;
        }
        Object result = null;
        if ( method!=null ) {
            try{
                result = method.doMethodInvoke(script, args);
            }catch(Throwable t) {
                logger.error("Tradlet script "+script.getId()+" "+name+"() failed: "+t, t.toString());
            }
        }
        return result;
    }

}