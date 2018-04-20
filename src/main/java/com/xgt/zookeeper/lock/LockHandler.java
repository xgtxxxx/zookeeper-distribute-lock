package com.xgt.zookeeper.lock;

public interface LockHandler {
    /**
     * Try to get zk lock first.
     * If get lock successfully, then call safeCaller.
     * Else throw FailedToLockException.
     * @param safeCaller
     * @throws FailedToLockException
     */
    void lockAndHandle(SafeCaller safeCaller) throws FailedToLockException;

    /**
     * Try to get zk lock first.
     * If get lock successfully, then call safeCaller.
     * Else if isAlwaysWaiting is true, waiting until to get the lock successfully, else throw FailedToLockException.
     * @param safeCaller
     * @param isAlwaysWaiting
     * @throws FailedToLockException
     */
    void lockAndHandle(SafeCaller safeCaller, boolean isAlwaysWaiting) throws FailedToLockException;

    /**
     * Try to get zk lock first.
     * If get lock successfully, then call safeCaller.
     * Else waiting to get the lock until timeout.
     * @param safeCaller
     * @param timeout
     * @throws FailedToLockException
     * @throws InterruptedException
     */
    void lockAndHandle(SafeCaller safeCaller, long timeout) throws FailedToLockException, InterruptedException;
}
