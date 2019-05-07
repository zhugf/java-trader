package trader.service.tradlet.script;

/**
 * 脚本调用上下文, 负责访问当前可用变量, 函数等等
 */
public interface ScriptContext {

    public boolean varExists(String property);

    public Object varGet(String varName);

    public boolean funcExists(String funcName);

    public Object funcInvoke(String funcName, Object[] args) throws Exception;

}
