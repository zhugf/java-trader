package trader.service.ta.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

public class KDJIndicator extends CachedIndicator<Num> {

    private Num N50 = numOf(50);
    private Num N2 = numOf(2);
    private Num N3 = numOf(3);

    class KIndicator extends CachedIndicator<Num> {

        private RSVIndicator rsv;
        private Num emaCount;
        private Num emaCount_1;

        public KIndicator(RSVIndicator indicator, int emaCount) {
            super(indicator);
            this.rsv = indicator;
            this.emaCount = numOf(emaCount);
            this.emaCount_1 = numOf(emaCount-1);
        }

        @Override
        protected Num calculate(int index) {
            if ( index<=0 ) {
                return N50;
            }
            Num prevValue = getValue(index -1);
            //==(K(N-1)*2+RSV)/3
            return prevValue.multipliedBy(emaCount_1).plus(rsv.getValue(index)).dividedBy(emaCount);
        }

    }

    class DIndicator extends CachedIndicator<Num> {

        private KIndicator k;
        private Num emaCount;
        private Num emaCount_1;

        public DIndicator(KIndicator k, int emaCount) {
            super(k);
            this.k = k;
            this.emaCount = numOf(emaCount);
            this.emaCount_1 = numOf(emaCount-1);
        }

        @Override
        protected Num calculate(int index) {
            if ( index<=0 ) {
                return N50;
            }
            Num prevValue = getValue( index -1);
            return prevValue.multipliedBy(emaCount_1).plus(k.getValue(index)).dividedBy(emaCount);
        }

    }

    private RSVIndicator rsv;
    private KIndicator k;
    private DIndicator d;

    public KDJIndicator(RSVIndicator indicator, int kCount, int dCount) {
        super(indicator);
        this.rsv = indicator;
        this.k = new KIndicator(rsv, kCount);
        this.d = new DIndicator(k, dCount);
    }

    public Indicator<Num> getRSVIndicator(){
        return rsv;
    }

    public Indicator<Num> getKIndicator(){
        return k;
    }

    public Indicator<Num> getDIndicator(){
        return d;
    }

    @Override
    protected Num calculate(int index) {
        Num k3 = k.getValue(index).multipliedBy(N3);
        Num d2 = d.getValue(index).multipliedBy(N2);
        return k3.minus(d2);
    }

    public static KDJIndicator create(BarSeries series, int rsvCount) {
        return create(series, rsvCount, 3, 3);
    }

    public static KDJIndicator create(BarSeries series, int rsvCount, int kCount, int dCount) {
        RSVIndicator rsv = new RSVIndicator(series, rsvCount);
        KDJIndicator result = new KDJIndicator(rsv, kCount, dCount);

        return result;
    }

}
