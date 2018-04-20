package com.xgt.zookeeper.lock;

import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributeLockHandler implements LockHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DistributeLockHandler.class);
    private static final String ROOT = "/locks"; //锁的根节点，如果有多个业务需要分布式锁，那么最好多创建几个锁节点分支，如/root/job1_lock; /root/job2_lock...
    private static final String LOCK_NAME = "testLock"; //本业务的锁名字
    private final ZkClient client;//这里使用ZkClient，避免自己维护监听事件
    private final String competitorName;//用于打印日志
    private String myLockNode;//本次获取锁时创建的节点
    private final AtomicInteger processStep = new AtomicInteger(1);//标记，在尝试执行任务超时的时候，判定该任务是否正在执行。

    public DistributeLockHandler(final String competitorName) {
        this.competitorName = competitorName;
        client = new ZkClient("172.17.50.8:2181", 5000);
        //如果ROOT不存在，则创建节点
        // 注意这里有线程安全问题，应该在所有任务执行前就应该做这一步。
        if(!client.exists(ROOT)) {
            client.create(ROOT, null, CreateMode.PERSISTENT);
        }
    }

    @Override
    public void lockAndHandle(final SafeCaller safeCaller) throws FailedToLockException {
        lockAndHandle(safeCaller, false);
    }

    /**
     * 分布式错的大概过程
     * 1. 在ROOT下面创建一个有序临时节点，ROOT节点必须是持久化节点
     * 2. 获取到ROOT下面的所有节点，判断刚刚创建的节点是否是第一个节点
     * 3.1 如果是第一个节点，那么该进程获取锁，可以执行任务
     * 3.2 如果不是第一个节点，那么可以在ROOT上注册子节点变化监听，如果有变化则重新判断本进程创建的节点是否是第一个节点
     * 4. 执行任务，并释放锁
     */
    @Override
    public void lockAndHandle(final SafeCaller safeCaller, final boolean isWaiting) throws FailedToLockException {
        //创建本进程的节点
        myLockNode = client.create(ROOT + "/" + LOCK_NAME, null, CreateMode.EPHEMERAL_SEQUENTIAL);
        LOG.info("{} created a new node: {}", competitorName, myLockNode);
        //过去ROOT下面的所有节点
        final List<String> subNodes = client.getChildren(ROOT);
        LOG.info("All locks: {}", subNodes);
        process(subNodes, safeCaller, isWaiting);
    }

    @Override
    public void lockAndHandle(final SafeCaller safeCaller, final long timeout) throws FailedToLockException, InterruptedException {
        if(timeout <= 0) {
            throw new IllegalArgumentException("Timeout should greater than 0!");
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        lockAndHandle(safeCaller, true);
        //启动定时器
        countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
        //Timeout后判断本进程期望的任务是否还没有执行，processStep=1表明任务没有执行，2表明任务正在执行，3表明任务已经执行完成
        //只有当任务还没有执行时，打断进程，抛出异常。
        if(processStep.get() == 1) {
            LOG.warn("{} get lock timeout!", competitorName);
            unlock();
            throw new FailedToLockException("Get lock timeout!");
        }
        countDownLatch = null;
    }

    private void process(final List<String> subNodes, final SafeCaller safeCaller, final boolean isWaiting) throws FailedToLockException {
        //尝试获取本进程节点的上一个节点
        final Optional<String> lastNode = getLastNode(subNodes, myLockNode);
        if(lastNode.isPresent()) {
            //上个节点存在，则说明竞争锁失败，此时判断是否需要等待
            if(isWaiting) {
                //等待获取锁
                LOG.info("{} is waiting for node: {}", competitorName, lastNode.get());
                waitingForLock(safeCaller);
            } else {
                //如果不等待，那么清除本进程节点信息，并抛出异常。
                unlock();
                throw new FailedToLockException("Thread " + competitorName + " get lock failed!");
            }
        } else {
            //本进程创建的节点是第一个节点，成功获取锁，并执行任务。
            LOG.info("{} success to get lock!", competitorName);
            processStep.getAndIncrement();
            safeCaller.call();
            unlock();
            processStep.getAndIncrement();
        }
    }

    private void waitingForLock(final SafeCaller safeCaller) {
        //注册监听
        //这里其实有些问题，监听root的子节点变化，不管子节点增加还是减少都会促发监听事件，很容易‘惊群’
        //1、应该尝试监听本进程的上个节点变化，或者监听第一个节点的变化，但是暂时没有找到ZkClient提供这个接口
        //2、需要调查下ZkClient是否会重复创建监听？/// TODO: 2018/4/20
        client.subscribeChildChanges(ROOT, (parentPath, currentChilds) -> {
            LOG.info("Nodes changed, remain nodes: {}", currentChilds);
            process(currentChilds, safeCaller, true);
        });
    }

    private void unlock() {
        LOG.info("{} release lock: {}", competitorName, myLockNode);
        client.delete(myLockNode);
        myLockNode = null;
        client.close();
    }

    private Optional<String> getLastNode(final List<String> nodes, final String myNode) {
        Collections.sort(nodes);
        final String target = myNode.substring(myNode.lastIndexOf('/') + 1);
        final int index = nodes.indexOf(target);
        if(index < 0) {
            throw new RuntimeException("No target node found!");
        }
        if(index == 0) {
            return Optional.empty();
        }

        return Optional.of(nodes.get(index - 1));
    }
}
