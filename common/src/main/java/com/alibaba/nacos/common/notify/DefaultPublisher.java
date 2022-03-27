/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.notify;

import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.alibaba.nacos.common.notify.NotifyCenter.ringBufferSize;

/**
 * The default event publisher implementation.
 *
 * <p>Internally, use {@link ArrayBlockingQueue <Event/>} as a message staging queue.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 */
public class DefaultPublisher extends Thread implements EventPublisher {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);
    /**
     * 发布者是否初始化完毕
     */
    private volatile boolean initialized = false;
    /**
     * 是否关闭了发布者
     */
    private volatile boolean shutdown = false;
    /**
     * 事件的类型
     */
    private Class<? extends Event> eventType;
    /**
     * 订阅者列表
     */
    protected final ConcurrentHashSet<Subscriber> subscribers = new ConcurrentHashSet<Subscriber>();
    /**
     * 队列最大容量
     */
    private int queueMaxSize = -1;
    /**
     * 队列类型
     */
    private BlockingQueue<Event> queue;
    /**
     * 最后一个事件的序列号
     */
    protected volatile Long lastEventSequence = -1L;
    /**
     * 事件序列号更新对象，用于更新原子属性lastEventSequence
     */
    private static final AtomicReferenceFieldUpdater<DefaultPublisher, Long> UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(DefaultPublisher.class, Long.class, "lastEventSequence");

    /**
     * 在初始化方法中，将其设置为了守护线程，意味着它将持续运行（它需要持续监控内部的事件队列），
     * 传入的type属性为当前发布者需要处理的事件类型，设置当前线程的名称以事件类型为区分，
     * 它将会以多个线程的形式存在，每个线程代表一种事件类型的发布者,后面初始化了队列的长度。
     * 最后调用启动方法完成当前线程的启动。
     * @param type       {@link Event >}
     * @param bufferSize Message staging queue size
     */
    @Override
    public void init(Class<? extends Event> type, int bufferSize) {
        setDaemon(true);
        setName("nacos.publisher-" + type.getName());
        this.eventType = type;
        this.queueMaxSize = bufferSize;
        this.queue = new ArrayBlockingQueue<Event>(bufferSize);
        start();
    }

    public ConcurrentHashSet<Subscriber> getSubscribers() {
        return subscribers;
    }

    /**
     * 直接调用了Thread的start方法开启守护线程，并设置初始化状态为true。
     * 根据java线程的启动方式，调用start方法之后start方法是会调用run方法的。
     */
    @Override
    public synchronized void start() {
        if (!initialized) {
            // start just called once
            super.start();
            if (queueMaxSize == -1) {
                queueMaxSize = ringBufferSize;
            }
            initialized = true;
        }
    }

    @Override
    public long currentEventSize() {
        return queue.size();
    }

    @Override
    public void run() {
        openEventHandler();
    }

    /**
     * 在run方法中调用了openEventHandler()方法。
     * 那发布者的实际工作原理就存在于这个方法内部。
     * 在首次启动的时候会等待1分钟，然后再进行消息消费。
     */
    void openEventHandler() {
        try {

            // This variable is defined to resolve the problem which message overstock in the queue.
            int waitTimes = 60;
            // To ensure that messages are not lost, enable EventHandler when
            // waiting for the first Subscriber to register
            for (; ; ) {
                //线程终止条件判断
                if (shutdown || hasSubscriber() || waitTimes <= 0) {
                    break;
                }
                //线程休眠1秒
                ThreadUtils.sleep(1000L);
                //等待次数减1
                waitTimes--;
            }

            for (; ; ) {
                // 线程终止条件判断
                if (shutdown) {
                    break;
                }
                // 从队列取出事件
                final Event event = queue.take();
                // 接收事件
                receiveEvent(event);
                // 更新事件序列号
                UPDATER.compareAndSet(this, lastEventSequence, Math.max(lastEventSequence, event.sequence()));
            }
        } catch (Throwable ex) {
            LOGGER.error("Event listener exception : {}", ex);
        }
    }

    private boolean hasSubscriber() {
        return CollectionUtils.isNotEmpty(subscribers);
    }

    @Override
    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void removeSubscriber(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * 前面的发布事件是指从队列内部获取事件并通知订阅者，
     * 这里的发布事件区别在于它是开放给外部调用者，
     * 接收统一通知中心的事件并放入队列中的。
     *
     *
     * 在放入队列成功的时候直接返回，若放入队列失败，则是直接同步发送事件给订阅者，
     * 不经过队列。这里的同步我认为的是从调用者到发布者调用订阅者之间是同步的，
     * 若队列可用，则是调用者到入队列就完成了本次调用，不需要等待循环通知订阅者。
     * 使用队列解耦无疑会提升通知中心的工作效率。
     *
     * 总体来说就是一个发布者内部维护一个BlockingQueue，
     * 在实现上使用了ArrayBlockingQueue，
     * 它是一个有界阻塞队列，元素先进先出。
     * 并且使用非公平模式提升性能，
     * 意味着等待消费的订阅者执行顺序将得不到保障（业务需求没有这种顺序性要求）。
     * 同时也维护了一个订阅者集合（他们都订阅了同一个事件类型），
     * 在死循环中不断从ArrayBlockingQueue中获取数据来循环通知每一个订阅者，也就是调用订阅者的onEvent()方法。
     * @param event {@link Event}
     * @return
     */
    @Override
    public boolean publish(Event event) {
        checkIsStart();
        boolean success = this.queue.offer(event);
        if (!success) {
            LOGGER.warn("Unable to plug in due to interruption, synchronize sending time, event : {}", event);
            receiveEvent(event);
            return true;
        }
        return true;
    }

    void checkIsStart() {
        if (!initialized) {
            throw new IllegalStateException("Publisher does not start");
        }
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        this.queue.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Receive and notifySubscriber to process the event.
     * 这里的接收事件指的是接收通知中心发过来的事件，发布给订阅者。
     * @param event {@link Event}.
     */
    void receiveEvent(Event event) {
        // 获取当前事件的序列号，它是自增的
        final long currentEventSequence = event.sequence();
        //通知所有订阅了该事件的订阅者
        // Notification single event listener
        for (Subscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            //判断订阅者是否忽略事件过期，判断当前事件是否被处理过（lastEventSequence初始化的值为-1，
            // 而Event的sequence初始化的值为0）
            if (subscriber.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
                LOGGER.debug("[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                        event.getClass());
                continue;
            }

            // Because unifying smartSubscriber and subscriber, so here need to think of compatibility.
            // Remove original judge part of codes.
            //发布事件给订阅者
            notifySubscriber(subscriber, event);
        }
    }

    @Override
    public void notifySubscriber(final Subscriber subscriber, final Event event) {

        LOGGER.debug("[NotifyCenter] the {} will received by {}", event, subscriber);
        //为每个订阅者创建一个Runnable对象
        final Runnable job = () -> subscriber.onEvent(event);
        // 使用订阅者的线程执行器
        final Executor executor = subscriber.executor();
        // 若订阅者没有自己的执行器，则直接执行run方法启动订阅者消费线程
        if (executor != null) {
            executor.execute(job);
        } else {
            try {
                job.run();
            } catch (Throwable e) {
                LOGGER.error("Event callback exception: ", e);
            }
        }
    }
}
