package trader.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.*;

public class CSVDataSet {
    private boolean afterLast = false;
    private boolean beforeFirst=true;
    private boolean hasHeader;
    private char delimiter;
    private String[] columns;
    private String line;
    private int dataIndex=-1;
    private String[] row;
    private BufferedReader reader;

    CSVDataSet(BufferedReader reader, char delimiter, boolean hasHeader) throws IOException
    {
        this.hasHeader = hasHeader;
        this.delimiter = delimiter;
        this.reader = reader;
        if ( hasHeader ){
            line = reader.readLine();
            dataIndex++;
            columns = CSVUtil.parseLine(line, delimiter);
        }
    }

    public void close(){
        if ( reader!=null ){
            try {
                reader.close();
            } catch (IOException e) {}
            reader = null;
        }
        row = null;
        line = null;
        columns = null;
    }

    public boolean isBeforeFirst(){
        return beforeFirst;
    }

    public boolean isAfterLast(){
        return afterLast;
    }

    public int getRowIndex(){
        if (hasHeader) {
            return dataIndex - 1;
        } else {
            return dataIndex;
        }
    }

    public boolean next(){
        try {
            if ( afterLast ) {
                return false;
            }
            if ( beforeFirst ){
                beforeFirst = false;
            }
            line = reader.readLine();
            if ( line==null ){
                afterLast = true;
                return false;
            }
            dataIndex++;
            row = CSVUtil.parseLine(line, delimiter);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasNext(){
        return !afterLast;
    }

    public String getLine(){
        return line;
    }

    public String[] getRow(){
        return row;
    }

    public String get(String column){
        return get(getColumnIndex(column));
    }

    public String get(int columnIndex){
        if ( columnIndex>=row.length ){
            return null;
        }
        return row[columnIndex];
    }

    public int getInt(int columnIndex, int defaultValue){
        String val = get(columnIndex);
        if ( val==null || val.trim().length()==0 ){
            return defaultValue;
        }
        return Integer.parseInt(val.trim());
    }

    public int getInt(String column){
        return Integer.parseInt(get(column));
    }

    public long getLong(String column){
        return Long.parseLong(get(column));
    }

    public long getLong(int columnIndex){
        return Long.parseLong(get(columnIndex));
    }

    public boolean getBoolean(String column){
        String v= get(column);
        return "true".equalsIgnoreCase(v) || "1".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    public double getDouble(String column){
        String str = get(column);
        if ( str==null || str.length()==0 ) {
            return Double.MAX_VALUE;
        }
        return Double.parseDouble(str);
    }

    public double getDouble(int columnIndex){
        String str = get(columnIndex);
        if ( str==null || str.length()==0 ) {
            return Double.MAX_VALUE;
        }
        return Double.parseDouble(str);
    }

    public long getPrice(String column){
        return PriceUtil.price2long(getDouble(column));
    }

    public long getPrice(int columnIndex){
        return PriceUtil.price2long(getDouble(columnIndex));
    }

    public LocalTime getTime(String column){
        String str = get(column);
        if ( str==null || str.length()==0 ) {
            return null;
        }
        return DateUtil.str2localtime(str);
    }

    public LocalTime getTime(int columnIndex){
        String str = get(columnIndex);
        if ( str==null || str.length()==0 ) {
            return null;
        }
        return DateUtil.str2localtime(str);
    }

    public LocalDateTime getDateTime(String column){
    	return getDateTime(DateUtil.getDefaultZoneId(), getColumnIndex(column));
    }

    public LocalDateTime getDateTime(int columnIndex){
    	return getDateTime(DateUtil.getDefaultZoneId(), columnIndex);
    }

    public LocalDateTime getDateTime(ZoneId zoneId, String column){
    	return getDateTime(zoneId, getColumnIndex(column));
    }

    public LocalDateTime getDateTime(ZoneId zoneId, int columnIndex){
        String str = get(columnIndex);
        if ( str==null || str.length()==0 ) {
            return null;
        }
        long val = 0;
        if ( (val=ConversionUtil.toLong(str, true))!=0 ) {
        	return DateUtil.long2datetime(zoneId, val);
        }
        return DateUtil.str2localdatetime(str);
    }

    public LocalDate getDate(String column){
        return getDate(getColumnIndex(column));
    }

    public LocalDate getDate(int columnIndex){
        String str = get(columnIndex);
        if ( str==null || str.length()==0 ) {
            return null;
        }
        long val = 0;
        if ( (val=ConversionUtil.toLong(str, true))!=0 ) {
        	return DateUtil.long2datetime(val).toLocalDate();
        }
        return DateUtil.str2localdate(str);
    }

    public boolean hasColumn(String column){
        return getColumnIndex(column)>=0;
    }

    public int getColumnIndex(String column){
        for(int i=0;i<columns.length;i++){
            if ( columns[i].equalsIgnoreCase(column) ){
                return i;
            }
        }
        return -1;
    }

    public boolean hasValue(String column){
        int columnIndex = getColumnIndex(column);
        return row.length>columnIndex && row[columnIndex]!=null;
    }

    public boolean hasValue(int columnIndex){
        return row.length>columnIndex && row[columnIndex]!=null;
    }

    public String[] getColumns(){
        return columns;
    }

}
