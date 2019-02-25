package trader.service.util;

import java.io.PrintWriter;
import java.util.List;

import trader.common.beans.BeansContainer;
import trader.common.util.StringUtil.KVPair;

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
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception;

}
