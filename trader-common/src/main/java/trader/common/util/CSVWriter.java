package trader.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CSVWriter<T> {

    public static interface CSVRowVisitor{
        public boolean visitRow(String[] rows);
    }

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

    public CSVWriter next()
    {
        row = new String[columnHeader.length];
        rows.add(row);
        return this;
    }

    public CSVWriter fromDataSet(CSVDataSet csvDS) {
        setRow(csvDS.getRow());
        return this;
    }

    public CSVWriter fromDataSetAll(CSVDataSet csvDS) {
        while(csvDS.next()) {
            next().setRow(csvDS.getRow());
        }
        return this;
    }

    public void marshall(T t)
    {
        String[] row0 = marshallHelper.marshall(t);
        System.arraycopy(row0, 0, row, 0, row0.length);
    }

    public void set(String column, String value)
    {
        row[getColumnIndex(column)] = value;
    }

    public void set(int olumnIndex, String value)
    {
        row[olumnIndex] = value;
    }

    public void setRow(String[] rowData) {
        for(int i=0;i<rowData.length;i++) {
            row[i] = rowData[i];
        }
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

    public void forEach(CSVRowVisitor visitor) {
        for(String[] row:rows) {
            if ( !visitor.visitRow(row) ) {
                break;
            }
        }
    }

    /**
     * 根据 关键字段值合并和排序
     */
    public CSVWriter merge(boolean ascend, String ...keyColumns) {
        int[] keyIdxs = new int[keyColumns.length];
        for(int i=0;i<keyColumns.length;i++){
            keyIdxs[i] = getColumnIndex(keyColumns[i]);
        }
        Map<String, Integer> key2rows = new TreeMap<>();
        for(int i=1;i<rows.size();i++) {
            String[] currRow = rows.get(i);
            key2rows.put(getRowKey(currRow, keyIdxs), i);
        }
        List<Integer> rowIdxs = new ArrayList<>(key2rows.values());
        if ( !ascend ) {
            Collections.reverse(rowIdxs);
        }
        List<String[]> rows2 = new ArrayList<>(rowIdxs.size());
        rows2.add(rows.get(0));
        for(Integer idx:rowIdxs) {
            rows2.add(rows.get(idx));
        }
        this.rows = rows2;
        return this;
    }

    private String getRowKey(String[] currRow, int[] keyIdxs) {
        StringBuilder result = new StringBuilder();
        for(int i=0;i<keyIdxs.length;i++) {
            if ( i>0 ) {
                result.append("-");
            }
            result.append(currRow[keyIdxs[i]]);
        }
        return result.toString();
    }

    @Override
    public String toString()
    {
        StringBuilder r = new StringBuilder(rows.size()*512);
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
