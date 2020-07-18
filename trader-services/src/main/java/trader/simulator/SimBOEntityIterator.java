package trader.simulator;

import java.util.Iterator;
import java.util.Map.Entry;

import trader.service.repository.AbsBOEntityIterator;
import trader.service.repository.BORepository;

public class SimBOEntityIterator extends AbsBOEntityIterator {

    private Iterator<Entry<String, String>> iter;
    private Entry<String, String> entry;

    protected SimBOEntityIterator(BORepository repository, SimBOEntity boEntity) {
        super(repository, boEntity);
        this.iter = boEntity.getData().entrySet().iterator();
    }

    @Override
    public String getData() {
        return entry.getValue();
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public String next() {
        entry = iter.next();
        return entry.getKey();
    }

    @Override
    public void close(){
    }

}
