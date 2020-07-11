package trader.service.repository.jpa;

import java.util.Iterator;

import trader.service.repository.AbsBOEntity;
import trader.service.repository.AbsBOEntityIterator;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;

public class JPAEntityIterator extends AbsBOEntityIterator implements BOEntityIterator {

    private Iterator<? extends AbsJPAEntity> iterator;
    private AbsJPAEntity lastData;

    public JPAEntityIterator(BORepository repository, AbsBOEntity boEntity, Iterator<? extends AbsJPAEntity> iterator) {
        super(repository, boEntity);
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String next() {
        lastData = iterator.next();
        lastId = lastData.getId();
        return lastId;
    }

    @Override
    public String getData() {
        return lastData.getAttrs();
    }

    @Override
    public void close(){
    }

}
