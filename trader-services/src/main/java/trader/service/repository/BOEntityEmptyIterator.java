package trader.service.repository;

public class BOEntityEmptyIterator implements BOEntityIterator {

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public String next() {
        return null;
    }

    @Override
    public void close(){
    }

    @Override
    public String getValue() {
        return null;
    }

}
