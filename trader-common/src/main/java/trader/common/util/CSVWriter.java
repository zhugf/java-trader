package trader.common.util;

import java.util.LinkedList;
import java.util.List;

public class CSVWriter<T> {
    private String[]             columnHeader;
    private List<String[]>       rows;
    String[]                     row;
    private char                 delimiter;
    private CSVMarshallHelper<T> marshallHelper;

    public CSVWriter(CSVDataSet dataSet, CSVMarshallHelper<T> marshallHelper)
    {
        this(dataSet.getColumns());
        while (dataSet.next()) {
            append((Object[]) dataSet.getRow());
        }

        this.marshallHelper = marshallHelper;
        if (columnHeader == null || columnHeader.length == 0) {
            columnHeader = marshallHelper.getHeader();
        } else {
            if (!StringUtil.arrrayEquals(columnHeader, marshallHelper.getHeader(), true)) {
                throw new RuntimeException("Unmatched header: ");
            }
        }
    }

    public CSVWriter(CSVMarshallHelper<T> marshallHelper)
    {
        this(marshallHelper.getHeader());
        this.marshallHelper = marshallHelper;
    }

    public CSVWriter(String... columnHeader)
    {
        this.columnHeader = columnHeader;
        this.rows = new LinkedList<>();
        if ( columnHeader!=null ){
            rows.add(columnHeader);
        }
        delimiter = ',';
    }

    /**
     * 返回数据行数，不含HEADER
     * @return
     */
    public int getRowCount(){
        if ( columnHeader==null ){
            return rows.size();
        }else{
            return rows.size()-1;
        }
    }

    public void setDelimiter(char c)
    {
        this.delimiter = c;
    }

    public void next()
    {
        row = new String[columnHeader.length];
        rows.add(row);
    }

    public void marshall(T t)
    {
        row = marshallHelper.marshall(t);
    }

    public void set(String column, String value)
    {
        row[getColumnIndex(column)] = value;
    }

    public void set(int olumnIndex, String value)
    {
        row[olumnIndex] = value;
    }

    public void append(Object... values)
    {
        next();
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v != null) {
                row[i] = v.toString();
            }
        }
    }

    public int getColumnIndex(String column)
    {
        for (int i = 0; i < columnHeader.length; i++) {
            if (columnHeader[i].equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString()
    {
        StringBuilder r = new StringBuilder(256);
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    r.append(delimiter);
                }
                if (row[i] != null) {
                    appendRow(r, row[i]);
                }
            }
            r.append("\n");
        }
        return r.toString();
    }

    private void appendRow(StringBuilder builder, String cell)
    {
        builder.append('"');
        for (int i = 0; i < cell.length(); i++) {
            char c = cell.charAt(i);
            if (c == '"') {
                builder.append("\"\"");
            } else if (c == '\n') {
                builder.append("\\n");
            } else {
                builder.append(c);
            }
        }
        builder.append('"');
    }
}
