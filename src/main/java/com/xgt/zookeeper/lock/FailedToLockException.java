package com.xgt.zookeeper.lock;

public class FailedToLockException extends Exception {
    public FailedToLockException() {
        super();
    }

    public FailedToLockException(final String message) {
        super(message);
    }

    public FailedToLockException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FailedToLockException(final Throwable cause) {
        super(cause);
    }
}
