package trader.common;

import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import org.junit.Test;

import trader.common.util.CSVDataSet;
import trader.common.util.CSVUtil;

public class TestCSVUtil {

    @Test
    public void test1() {
        String csv="col1,col2\nv1,v2\nv1,v2\nv1,v2";
        CSVDataSet dataSet = CSVUtil.parse(csv);
        int rowCount = 0;
        while(dataSet.next()){
            String v = dataSet.get("COL1");
            assertTrue(v.equals("v1"));
            v = dataSet.get("COL2");
            assertTrue(v.equals("v2"));
            rowCount++;
        }
        assertTrue(rowCount==3);
    }

    @Test
    public void test2() throws Exception {
        String csv="Bob,144,4.0,Great at everything!\n"
                    +"Alice,124,3.9,\"Great, very talented!\"\n"
                    +"Steve,119,3.5,\"He is \"\"good\"\", but could be better\"\n"
                    +"David,100,3.0,He needs to attend more classes!\n";
        CSVDataSet dataSet = CSVUtil.parse(new StringReader(csv),',',false);
        int rowCount = 0;
        while(dataSet.next()){
            rowCount++;
        }
        assertTrue(rowCount==4);
    }

}
