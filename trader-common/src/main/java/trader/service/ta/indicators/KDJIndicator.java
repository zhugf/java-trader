package trader.service.ta.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

public class KDJIndicator extends CachedIndicator<Num> {

    private Num N50 = numOf(50);
    private Num N2 = numOf(2);
    private Num N3 = numOf(3);

    class KIndicator extends CachedIndicator<Num> {

        private RSVIndicator rsv;

        public KIndicator(RSVIndicator indicator) {
            super(indicator);
            this.rsv = indicator;
        }

        @Override
        protected Num calculate(int index) {
            if ( index<=0 ) {
                return N50;
            }
            Num prevValue = getValue(index -1);
            //==(K(N-1)*2+RSV)/3
            return prevValue.multipliedBy(N2).plus(rsv.getValue(index)).dividedBy(N3);
        }

    }

    class DIndicator extends CachedIndicator<Num> {

        private KIndicator k;

        public DIndicator(KIndicator k) {
            super(k);
            this.k = k;
        }

        @Override
        protected Num calculate(int index) {
            if ( index<=0 ) {
                return N50;
            }
            Num prevValue = getValue( index -1);

            return prevValue.multipliedBy(N2).plus(k.getValue(index)).dividedBy(N3);
        }

    }

    private RSVIndicator rsv;
    private KIndicator k;
    private DIndicator d;

    public KDJIndicator(RSVIndicator indicator) {
        super(indicator);
        this.rsv = indicator;
        this.k = new KIndicator(rsv);
        this.d = new DIndicator(k);
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

}
