package trader.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class CSVUtil {

    public static void writeCSVLine(Writer writer, String[] row, char delimiter) throws IOException
    {
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                writer.write(delimiter);
            }
            writer.write(row[i]);
        }
        writer.write('\n');
    }

    public static String[] parseLine(String csvLine, char delimiter)
    {
        if (csvLine == null) {
            return null;
        }
        List<String> result = new LinkedList<>();

        int i = 0;
        StringBuilder cell = new StringBuilder();
        boolean inQuote = false;
        while (i < csvLine.length()) {
            char c = csvLine.charAt(i++);
            if (c == '"') {
                if (!inQuote) {
                    inQuote = true;
                    continue;
                }
                char nextc = 0;
                if (i < csvLine.length()) {
                    nextc = csvLine.charAt(i);
                }
                if (nextc == '"') {
                    cell.append('"');
                    i++;
                    continue;
                }
                inQuote = false;
                continue;
            }
            if (inQuote) {
                cell.append(c);
                continue;
            }
            if (c == delimiter) {
                result.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        //if (cell.length() > 0)
        {
            result.add(cell.toString());
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * 缺省方式解析CSV: 逗号分隔",", 第一行列名
     */
    public static CSVDataSet parse(String csvText)
    {
        return parse(csvText, ',', true);
    }

    public static CSVDataSet parse(String csvText, char delimiter, boolean hasHeader)
    {
        try {
            StringReader reader = new StringReader(csvText);
            return parse(reader, delimiter, hasHeader);
        } catch (IOException ioe) {
        }
        ;
        return null;
    }

    public static CSVDataSet parse(File csvFile) throws IOException
    {
        return parse(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"), ',', true);
    }

    public static CSVDataSet parse(Reader reader, char delimiter, boolean hasHeader) throws IOException
    {
        BufferedReader breader = null;
        if (reader instanceof BufferedReader) {
            breader = (BufferedReader) reader;
        } else {
            breader = new BufferedReader(reader);
        }
        return new CSVDataSet(breader, delimiter, hasHeader);
    }

    public static String merge(String originalCsv, String toMergeCsv, String keyColumn, boolean overwrite)
    {
        CSVDataSet original = parse(originalCsv);
        CSVDataSet toMerge = parse(toMergeCsv);
        final int originalKeyIndex = original.getColumnIndex(keyColumn);
        final int toMergeKeyIndex = toMerge.getColumnIndex(keyColumn);
        if (originalKeyIndex < 0 || toMergeKeyIndex < 0) {
            throw new RuntimeException(
                    "Key column " + keyColumn + " doesn't exists in " + "original " + StringUtil.array2str(original.getColumns(), ",")
                    + " or to merge " + StringUtil.array2str(toMerge.getColumns(), ",") + " columns");
        }
        ArrayList<String[]> originalRows = new ArrayList<>();
        while (original.next()) {
            originalRows.add(original.getRow());
        }
        while (toMerge.next()) {
            final String[] row = toMerge.getRow();
            final String key = row[toMergeKeyIndex];
            int originalIndex = -1;
            //row is passed as o1
            originalIndex = Collections.binarySearch(originalRows, row, new Comparator<String[]>(){
                @Override
                public int compare(String[] o1, String[] o2) {
                    return o1[toMergeKeyIndex].compareTo(o2[originalKeyIndex]);
                }
            });
            if (originalIndex < 0) {
                originalRows.add(row);
            } else {
                for (String toMergeColumn : toMerge.getColumns()) {
                    String toMergeValue = toMerge.get(toMergeColumn);
                    if (toMergeValue == null) {
                        continue;
                    }
                    int originalColumnIndex = original.getColumnIndex(toMergeColumn);
                    if (originalColumnIndex < 0) {
                        continue;
                    }
                    String originalColumnValue = row[originalColumnIndex];
                    if (originalColumnValue != null && !overwrite) {
                        continue;
                    }
                    row[originalColumnIndex] = toMergeValue;
                }
            }
        }
        // Sort and create dataset
        Collections.sort(originalRows, (o1, o2) -> {
            return o1[originalKeyIndex].compareTo(o2[originalKeyIndex]);
        });
        CSVWriter csvWriter = new CSVWriter(original.getColumns());
        for(String[] row: originalRows){
            Object[] orow = new Object[row.length];
            System.arraycopy(row, 0, orow, 0, row.length);
            csvWriter.append(orow);
        }
        return csvWriter.toString();
    }

}
