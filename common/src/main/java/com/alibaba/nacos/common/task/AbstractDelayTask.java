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

package com.alibaba.nacos.common.task;

/**
 * Abstract task which can delay and merge.
 *  可以延迟执行的任务。ps:nacos的健康检查都是立即执行的任务
 * @author huali
 * @author xiweng.yy
 */
public abstract class AbstractDelayTask implements NacosTask {

    /**
     * Task time interval between twice processing, unit is millisecond.
     * 任务执行间隔时长（单位：毫秒）
     */
    private long taskInterval;

    /**
     * The time which was processed at last time, unit is millisecond.
     * 上一次执行的事件（单位：毫秒）
     */
    private long lastProcessTime;

    /**
     * merge task.
     * 合并任务，关于合并任务，请查看它的子类实现
     *
     * @param task task
     */
    public abstract void merge(AbstractDelayTask task);

    public void setTaskInterval(long interval) {
        this.taskInterval = interval;
    }

    public long getTaskInterval() {
        return this.taskInterval;
    }

    public void setLastProcessTime(long lastProcessTime) {
        this.lastProcessTime = lastProcessTime;
    }

    public long getLastProcessTime() {
        return this.lastProcessTime;
    }

    @Override
    public boolean shouldProcess() {
        return (System.currentTimeMillis() - this.lastProcessTime >= this.taskInterval);
    }

}
