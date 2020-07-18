package trader.simulator;

import com.google.gson.JsonElement;

import trader.service.repository.BOEntity;
import trader.service.repository.BOEntityIterator;
import trader.service.repository.BORepository;

public class SimBORepository implements BORepository {

    private SimBOEntity[] entities;

    public SimBORepository() {
        entities = new SimBOEntity[BOEntityType.values().length];
        for(int i=0;i<entities.length;i++) {
            entities[i] = new SimBOEntity(BOEntityType.values()[i]);
        }
    }

    @Override
    public BOEntity getBOEntity(BOEntityType entityType) {
        return entities[entityType.ordinal()];
    }

    @Override
    public String load(BOEntityType entityType, String entityId) {
        return entities[entityType.ordinal()].get(entityId);
    }

    @Override
    public Object loadEntity(BOEntityType entityType, String entityId) {
        return entities[entityType.ordinal()].data2entity(this, entityId, null);
    }

    @Override
    public BOEntityIterator search(BOEntityType entityType, String queryExpr) {
        return new SimBOEntityIterator(this, entities[entityType.ordinal()]);
    }

    @Override
    public void asynSave(BOEntityType entityType, String id, JsonElement json) {
        entities[entityType.ordinal()].put(id, json.toString());
    }

    @Override
    public void save(BOEntityType entityType, String id, JsonElement json) {
        entities[entityType.ordinal()].put(id, json.toString());
    }

    @Override
    public void beginTransaction(boolean readOnly) {
    }

    @Override
    public boolean endTransaction(boolean commit) {
        return false;
    }

}
