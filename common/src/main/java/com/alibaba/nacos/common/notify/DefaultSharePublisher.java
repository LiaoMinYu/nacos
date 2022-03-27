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
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The default share event publisher implementation for slow event.
 * 用于发布SlowEvent事件并通知所有订阅了该事件的订阅者。
 *
 * 继承了DefaultPublisher，意味着它将拥有其所有的特性。
 * 从subMappings属性来看，这个发布器是支持多个SlowEvent事件的。
 * DefaultSharePublisher重载了DefaultPublisher的addSubscriber()和removeSubscriber()方法，
 * 用于处理多事件类型的情形。
 * @author zongtanghu
 */
public class DefaultSharePublisher extends DefaultPublisher {

    /**
     * 用于保存事件类型为SlowEvent的订阅者，一个事件类型对应多个订阅者
     */
    private final Map<Class<? extends SlowEvent>, Set<Subscriber>> subMappings = new ConcurrentHashMap<Class<? extends SlowEvent>, Set<Subscriber>>();
    /**
     * 可重入锁
     */
    private final Lock lock = new ReentrantLock();

    /**
     * Add listener for default share publisher.
     *
     * Set newSet = new ConcurrentHashSet(); 它这里实际上使用的是自己实现的ConcurrentHashSet
     * ，它内部使用了ConcurrentHashMap来实现存储。在ConcurrentHashSet.add()方法的实现上，
     * 它以当前插入的Subscriber对象为key，以一个Boolean值占位：map.putIfAbsent(o, Boolean.TRUE)。
     * @param subscriber    {@link Subscriber}
     * @param subscribeType subscribe event type, such as slow event or general event.
     */
    public void addSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        // Actually, do a classification based on the slowEvent type.
        //将事件类型转换为当前发布者支持的类型
        Class<? extends SlowEvent> subSlowEventType = (Class<? extends SlowEvent>) subscribeType;
        // For adding to parent class attributes synchronization.
        //添加到父类的订阅者列表中，为何要添加呢？因为它需要使用父类的队列消费逻辑
        subscribers.add(subscriber);
        //为多个操作加锁
        lock.lock();
        try {
            //首先从事件订阅列表里面获取当前事件对应的订阅者集合
            Set<Subscriber> sets = subMappings.get(subSlowEventType);
            if (sets == null) {
                //若没有订阅者，则新增当前订阅者
                Set<Subscriber> newSet = new ConcurrentHashSet<Subscriber>();
                newSet.add(subscriber);
                subMappings.put(subSlowEventType, newSet);
                return;
            }
            // 若当前事件订阅者列表不为空，则插入，因为使用的是Set集合因此可以避免重复数据
            sets.add(subscriber);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove listener for default share publisher.
     *
     * @param subscriber    {@link Subscriber}
     * @param subscribeType subscribe event type, such as slow event or general event.
     */
    public void removeSubscriber(Subscriber subscriber, Class<? extends Event> subscribeType) {
        // Actually, do a classification based on the slowEvent type.
        // 转换类型
        Class<? extends SlowEvent> subSlowEventType = (Class<? extends SlowEvent>) subscribeType;
        // For removing to parent class attributes synchronization.
        //先移除父类中的订阅者
        subscribers.remove(subscriber);
        // 加锁
        lock.lock();
        try {
            // 移除指定事件的指定订阅者
            Set<Subscriber> sets = subMappings.get(subSlowEventType);

            if (sets != null) {
                sets.remove(subscriber);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 接收事件
     *
     * @param event {@link Event}.
     */
    @Override
    public void receiveEvent(Event event) {
        //获取当前事件的序列号
        final long currentEventSequence = event.sequence();
        //获取事件的类型，转换为当前发布器支持的事件
        // get subscriber set based on the slow EventType.
        final Class<? extends SlowEvent> slowEventType = (Class<? extends SlowEvent>) event.getClass();
        //获取当前事件的订阅者列表
        // Get for Map, the algorithm is O(1).
        Set<Subscriber> subscribers = subMappings.get(slowEventType);
        if (null == subscribers) {
            LOGGER.debug("[NotifyCenter] No subscribers for slow event {}", slowEventType.getName());
            return;
        }
        //循环通知所有订阅者
        // Notification single event subscriber
        for (Subscriber subscriber : subscribers) {
            // Whether to ignore expiration events
            if (subscriber.ignoreExpireEvent() && lastEventSequence > currentEventSequence) {
                LOGGER.debug("[NotifyCenter] the {} is unacceptable to this subscriber, because had expire",
                        event.getClass());
                continue;
            }
            //通知逻辑和父类是共用的
            // Notify single subscriber for slow event.
            notifySubscriber(subscriber, event);
        }
    }
    //DefaultPublisher是一个发布器只负责发布一个事件，并通知订阅了这个事件的所有订阅者；
    //DefaultSharePublisher则是一个发布器可以发布多个事件，并通知订阅了这个事件的所有订阅者。
}
