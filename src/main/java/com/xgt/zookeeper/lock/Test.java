package com.xgt.zookeeper.lock;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        clear();
        start(5);
        Thread.sleep(Integer.MAX_VALUE);
    }

    private static void start(final int count) {
        for(int i=1; i<=count; i++) {
            new Thread(new Competitor("competitor" + i, 5000)).start();
        }
    }

    private static void clear() {
        final ZkClient client = new ZkClient("172.17.50.8:2181", 5000);
        client.getChildren("/locks")
            .stream()
            .forEach(node -> client.delete("/locks/" + node));
    }
}
