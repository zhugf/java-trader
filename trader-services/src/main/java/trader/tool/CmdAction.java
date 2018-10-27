package trader.tool;

import java.io.PrintWriter;
import java.util.List;

/**
 * 一个命令行命令的参数接口
 */
public interface CmdAction {

    /**
     * 返回命令字符, 用于匹配
     */
    public String getCommand();

    /**
     * 输出使用帮助
     */
    public void usage(PrintWriter writer);

    /**
     * 实际执行命令
     */
    public int execute(PrintWriter writer, List<String> options) throws Exception;

}
