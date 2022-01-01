package trader.service.ta;

import static org.ta4j.core.num.NaN.NaN;

import java.util.function.Function;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import trader.common.util.PriceUtil;

/**
 * long表示价格, 4位小数
 */
public class LongNum implements Num {
    private static final long serialVersionUID = -6389115676116240242L;

    public static final LongNum D0 = LongNum.valueOf(0);
    public static final LongNum D1 = LongNum.valueOf(1);
    public static final LongNum D2 = LongNum.valueOf(2);
    public static final LongNum D3 = LongNum.valueOf(3);
    public static final LongNum D4 = LongNum.valueOf(4);
    public static final LongNum D5 = LongNum.valueOf(5);
    public static final LongNum D6 = LongNum.valueOf(6);
    public static final LongNum D7 = LongNum.valueOf(7);
    public static final LongNum D8 = LongNum.valueOf(8);
    public static final LongNum D9 = LongNum.valueOf(9);
    public static final LongNum D10 = LongNum.valueOf(10);

    public static final LongNum _1 = LongNum.valueOf(-1);
    public static final LongNum _2 = LongNum.valueOf(-2);

    /**
     * 0
     */
    public static final LongNum ZERO = D0;
    /**
     * 1
     */
    public static final LongNum ONE = D1;
    /**
     * -1
     */
    public static final LongNum NEG_ONE = _1;
    /**
     * 2
     */
    public static final LongNum TWO = D2;
    /**
     * -2
     */
    public static final LongNum NEG_TWO = _2;
    /**
     * 3
     */
    public static final LongNum THREE = D3;

    private long value;

    private LongNum(long rawValue) {
        this.value = rawValue;
    }

    @Override
    public int compareTo(Num o) {
        return Long.compare(value, ((LongNum)o).value);
    }

    @Override
    public Number getDelegate() {
        return PriceUtil.long2price(value);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Num plus(Num augend) {
        long result = value+((LongNum)augend).value;
        return new LongNum(result);
    }

    @Override
    public Num minus(Num subtrahend) {
        long result = value-((LongNum)subtrahend).value;
        return new LongNum(result);
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)multiplicand).value);
        double r = v*v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num dividedBy(Num divisor) {
        if ( divisor.isZero() ) {
            return new LongNum(Long.MAX_VALUE);
        }
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)divisor).value);
        double r = v/v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num remainder(Num divisor) {
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)divisor).value);
        double r = v%v2;
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num pow(int n) {
        double v = PriceUtil.long2price(value);
        double r = Math.pow(v, n);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num pow(Num n) {
        double v = PriceUtil.long2price(value);
        double v2 = PriceUtil.long2price(((LongNum)n).value);
        double r = Math.pow(v, v2);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num sqrt() {
        double v = PriceUtil.long2price(value);
        double r = Math.sqrt(v);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num log() {
        if (value<=0) {
            return NaN;
        }
        double v = PriceUtil.long2price(value);
        double r = Math.log(v);
        return new LongNum(PriceUtil.price2long(r));
    }

    @Override
    public Num sqrt(int precision) {
        return sqrt();
    }

    @Override
    public Num abs() {
        return new LongNum(Math.abs(value));
    }

    @Override
    public boolean isZero() {
        return value==0;
    }

    @Override
    public boolean isPositive() {
        return value>0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return value>=0;
    }

    @Override
    public boolean isNegative() {
        return value<0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return value<=0;
    }

    @Override
    public boolean isEqual(Num other) {
        return value == ((LongNum)other).value;
    }

    @Override
    public boolean isGreaterThan(Num other) {
        return value > ((LongNum)other).value;
    }

    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return value >= ((LongNum)other).value;
    }

    @Override
    public boolean isLessThan(Num other) {
        return value < ((LongNum)other).value;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return value <= ((LongNum)other).value;
    }

    @Override
    public Num min(Num other) {
        if ( value<=((LongNum)other).value) {
            return this;
        }
        return other;
    }

    @Override
    public Num max(Num other) {
        if ( value>=((LongNum)other).value) {
            return this;
        }
        return other;
    }

    @Override
    public Function<Number, Num> function() {
        return LongNum::valueOf;
    }

    public long rawValue() {
        return value;
    }

    public static LongNum valueOf(String str) {
        return new LongNum(PriceUtil.str2long(str));
    }

    public static LongNum valueOf(Number i) {
        return new LongNum(PriceUtil.price2long(i.doubleValue()));
    }

    public static LongNum fromRawValue(long rawValue) {
        return new LongNum(rawValue);
    }

    public static LongNum fromNum(Num num) {
        if ( num instanceof LongNum ) {
            return (LongNum)num;
        }else {
            return new LongNum(PriceUtil.price2long(num.doubleValue()));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj==null ) {
            return false;
        }
        if ( obj instanceof LongNum ) {
            return ((LongNum)obj).value==value;
        }
        return toString().equals(obj.toString());
    }

    @Override
    public String toString() {
        return PriceUtil.long2str(value);
    }

    public Num floor() {
        return valueOf(Math.floor(doubleValue()));
    }

    public Num ceil() {
        return valueOf(Math.ceil(doubleValue()));
    }

    public Num negate() {
        return new LongNum(-1*value);
    }
}
