package com.xgt.zookeeper.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Competitor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Competitor.class);
    private final String name;
    private final int processTime;
    public Competitor(final String name, final int time) {
        this.name = name;
        this.processTime = time;
    }

    @Override
    public void run() {
        final LockHandler lock  = new DistributeLockHandler(name);

        try{
            lock.lockAndHandle(() -> {
                try {
                    LOGGER.info("======={} start to do something========", name);
                    Thread.sleep(processTime);
                    LOGGER.info("======= {} end to do something ========", name);
                } catch (final InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }, 6000);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
