package trader.service.repository;

public abstract class AbsBOEntityIterator implements BOEntityIterator {

    protected BORepository repository;
    protected AbsBOEntity boEntity;
    protected String lastId;

    protected AbsBOEntityIterator(BORepository repository, AbsBOEntity boEntity) {
        this.repository = repository;
        this.boEntity = boEntity;
    }

    @Override
    public Object getEntity() {
        return boEntity.data2entity(repository, lastId, getData());
    }

}
