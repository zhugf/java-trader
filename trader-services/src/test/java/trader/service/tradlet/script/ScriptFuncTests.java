package trader.service.tradlet.script;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import trader.common.util.ConversionUtil;
import trader.common.util.PriceUtil;
import trader.common.util.StringUtil;
import trader.service.ta.LongNum;
import trader.service.ta.indicators.SimpleIndicator;
import trader.service.tradlet.script.func.CROSSFunc;
import trader.service.tradlet.script.func.MAXFunc;
import trader.service.tradlet.script.func.REFFunc;

public class ScriptFuncTests {

    @Test
    public void test_MAX() throws Exception
    {
        MAXFunc max = new MAXFunc();
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = string2value("200, 201, 202");

            GroovyIndicatorValue v3 = (GroovyIndicatorValue)max.invoke(new Object[] {v1, v2});
            assertTrue(v3.getValue().intValue()==202);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = string2value("201, 202");

            GroovyIndicatorValue v3 = (GroovyIndicatorValue)max.invoke(new Object[] {v1, v2});
            assertTrue(v3.getValue().intValue()==202);
            assertTrue(v3.getIndicator().getValue(0).intValue()==100);
        }
    }

    @Test
    public void test_CROSS() throws Exception
    {
        CROSSFunc cross = new CROSSFunc();
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            Object r = cross.invoke(new Object[] {v1, 100});
            assertTrue(ConversionUtil.toBoolean(r)==true);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            Object r = cross.invoke(new Object[] {v1, 200});
            assertTrue(ConversionUtil.toBoolean(r)==false);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = string2value("100, 100, 100");
            Object r = cross.invoke(new Object[] {v1, v2});
            assertTrue(ConversionUtil.toBoolean(r)==true);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = string2value("105, 105, 105");
            Object r = cross.invoke(new Object[] {v1, v2});
            assertTrue(ConversionUtil.toBoolean(r)==false);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = string2value("101, 101");
            Object r = cross.invoke(new Object[] {v1, v2});
            assertTrue(ConversionUtil.toBoolean(r)==true);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            Object r = cross.invoke(new Object[] {101, v1});
            assertTrue(ConversionUtil.toBoolean(r)==true);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            Object r = cross.invoke(new Object[] {102, v1});
            assertTrue(ConversionUtil.toBoolean(r)==false);
        }
    }

    @Test
    public void test_REF() throws Exception
    {
        REFFunc ref = new REFFunc();
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = (GroovyIndicatorValue)ref.invoke(new Object[] {v1, 1});
            assertTrue(v2.getValue().intValue()==101);
        }
        {
            GroovyIndicatorValue v1 = string2value("100, 101, 102");
            GroovyIndicatorValue v2 = (GroovyIndicatorValue)ref.invoke(new Object[] {v1, 2});
            assertTrue(v2.getValue().intValue()==100);
        }
    }


    private static GroovyIndicatorValue string2value(String str) {
        final List<Num> values = new ArrayList<>();
        for(String v:StringUtil.split(str, ",|;")) {
            values.add(new LongNum(PriceUtil.price2long(ConversionUtil.toDouble(v))));
        }
        TimeSeries series = new BaseTimeSeries() {
            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return values.size()-1;
            }

            @Override
            public int getBarCount() {
                return values.size();
            }

            @Override
            public Num numOf(Number number){
                return LongNum.valueOf(number);
            }
        };

        return new GroovyIndicatorValue( new SimpleIndicator(series, values));
    }
}
