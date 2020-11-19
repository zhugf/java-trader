package trader.tool;

import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.h2.tools.Server;

import trader.common.beans.BeansContainer;
import trader.common.config.ConfigUtil;
import trader.common.util.ConversionUtil;
import trader.common.util.DateUtil;
import trader.common.util.FileUtil;
import trader.common.util.IOUtil;
import trader.common.util.IniFile;
import trader.common.util.StringUtil;
import trader.common.util.StringUtil.KVPair;
import trader.common.util.SystemUtil;
import trader.common.util.TraderHomeUtil;
import trader.service.util.CmdAction;

public class H2DBQueryAction implements CmdAction {

    private PrintWriter writer;
    private String sql;

    @Override
    public String getCommand() {
        return "h2db.query";
    }

    @Override
    public void usage(PrintWriter writer) {
        writer.println("h2db query --sql=<SQL Query>");
        writer.println("\t查询H2DB数据库");
    }

    @Override
    public int execute(BeansContainer beansContainer, PrintWriter writer, List<KVPair> options) throws Exception {
        parseOptions(options);
        if ( StringUtil.isEmpty(sql)) {
            return 1;
        }
        DataSource ds = H2DBStartAction.createH2DBDataSource();
        try {
            try(Connection conn = ds.getConnection(); Statement stmt = conn.createStatement();){
                ResultSet rs = stmt.executeQuery(sql);
                dumpResultSet(rs);
                IOUtil.close(rs);
            }
        }finally {
            if ( ds instanceof AutoCloseable ) {
                IOUtil.close((AutoCloseable)ds);
            }
        }
        return 0;
    }

    protected void parseOptions(List<KVPair> options) {
        for(KVPair kv:options) {
            if ( StringUtil.isEmpty(kv.v)) {
                continue;
            }
            switch(kv.k.toLowerCase()) {
            case "sql":
                sql = kv.v;
                break;
            }
        }
    }

    private void dumpResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData resultSetMetaData = rs.getMetaData();
        // 获取列数
        int ColumnCount = resultSetMetaData.getColumnCount();
        // 保存当前列最大长度的数组
        int[] columnMaxLengths = new int[ColumnCount];
        // 缓存结果集,结果集可能有序,所以用ArrayList保存变得打乱顺序.
        ArrayList<String[]> results = new ArrayList<>();
        // 按行遍历
        while (rs.next()) {
            // 保存当前行所有列
            String[] columnStr = new String[ColumnCount];
            // 获取属性值.
            for (int i = 0; i < ColumnCount; i++) {
                // 获取一列
                columnStr[i] = rs.getString(i + 1);
                // 计算当前列的最大长度
                columnMaxLengths[i] = Math.max(columnMaxLengths[i], (columnStr[i] == null) ? 0 : columnStr[i].length());
            }
            // 缓存这一行.
            results.add(columnStr);
        }
        printSeparator(columnMaxLengths);
        printColumnName(resultSetMetaData, columnMaxLengths);
        printSeparator(columnMaxLengths);
        // 遍历集合输出结果
        Iterator<String[]> iterator = results.iterator();
        String[] columnStr;
        while (iterator.hasNext()) {
            columnStr = iterator.next();
            for (int i = 0; i < ColumnCount; i++) {
                // System.out.printf("|%" + (columnMaxLengths[i] + 1) + "s", columnStr[i]);
                System.out.printf("|%" + columnMaxLengths[i] + "s", columnStr[i]);
            }
            System.out.println("|");
        }
        printSeparator(columnMaxLengths);
    }

    /**
     * 输出列名.
     *
     * @param resultSetMetaData 结果集的元数据对象.
     * @param columnMaxLengths  每一列最大长度的字符串的长度.
     * @throws SQLException
     */
    private void printColumnName(ResultSetMetaData resultSetMetaData, int[] columnMaxLengths) throws SQLException
    {
        int columnCount = resultSetMetaData.getColumnCount();
        for (int i = 0; i < columnCount; i++) {
            // System.out.printf("|%" + (columnMaxLengths[i] + 1) + "s", resultSetMetaData.getColumnName(i + 1));
            writer.printf("|%" + columnMaxLengths[i] + "s", resultSetMetaData.getColumnName(i + 1));
        }
        writer.println("|");
    }

    /**
     * 输出分隔符.
     *
     * @param columnMaxLengths 保存结果集中每一列的最长的字符串的长度.
     */
    private void printSeparator(int[] columnMaxLengths) {
        for (int i = 0; i < columnMaxLengths.length; i++) {
            writer.print("+");
            // for (int j = 0; j < columnMaxLengths[i] + 1; j++) {
            for (int j = 0; j < columnMaxLengths[i]; j++) {
                writer.print("-");
            }
        }
        writer.println("+");
    }

}
