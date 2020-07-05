package trader.service.repository.jpa;

import java.util.Iterator;

import trader.service.repository.BOEntityIterator;

public class JPAEntityIterator implements BOEntityIterator {

    private Iterator<? extends AbsJPAEntity> iterator;
    private AbsJPAEntity entity;
    public JPAEntityIterator(Iterator<? extends AbsJPAEntity> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String next() {
        entity = iterator.next();
        return entity.getId();
    }

    @Override
    public void close(){
    }

    @Override
    public String getValue() {
        return entity.getAttrs();
    }

}
