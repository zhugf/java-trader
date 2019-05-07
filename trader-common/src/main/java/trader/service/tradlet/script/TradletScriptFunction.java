package trader.service.tradlet.script;

/**
 * 脚本函数实现.
 * <BR>函数的实现代码需要通过annotation Discoverable注册, 并运行时自动发现.
 */
public interface TradletScriptFunction {

    public Object invoke(Object[] args) throws Exception;

}
