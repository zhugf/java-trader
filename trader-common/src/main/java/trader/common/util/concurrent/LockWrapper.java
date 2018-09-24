package trader.common.util.concurrent;

import java.util.concurrent.locks.Lock;

public class LockWrapper implements AutoCloseable
{
    private final Lock _lock;

    public LockWrapper(Lock lock) {
       lock.lock();
       this._lock = lock;
    }

    public void lock() {
        this._lock.lock();
    }

    @Override
    public void close() {
        this._lock.unlock();
    }
}