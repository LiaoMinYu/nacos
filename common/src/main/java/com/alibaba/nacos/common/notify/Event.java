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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An abstract class for event.
 * 在事件抽象类中定义了一个事件的序列号，它是自增的。用于区分事件执行的前后顺序。它是由DefaultPublisher来处理。
 * SlowEvent可以共享一个事件队列，也就是一个发布者可以同时管理多个事件的发布（区别于DefaultPublisher只能管理一个事件）。
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author zongtanghu
 */
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule"})
public abstract class Event implements Serializable {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final long sequence = SEQUENCE.getAndIncrement();

    /**
     * Event sequence number, which can be used to handle the sequence of events.
     * 常规事件
     * @return sequence num, It's best to make sure it's monotone.
     */
    public long sequence() {
        return sequence;
    }

}

