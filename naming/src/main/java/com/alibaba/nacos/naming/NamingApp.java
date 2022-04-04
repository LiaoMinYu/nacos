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

package com.alibaba.nacos.naming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nacos naming starter.
 *
 * @author xxc
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.alibaba.nacos.naming", "com.alibaba.nacos.core"})
public class NamingApp {

    public static void main(String[] args) {
        SpringApplication.run(NamingApp.class, args);
    }

    /**
     * TcpHealthCheckProcessor：负责启动任务，并创建多个TaskProcessor。
     * TaskProcessor：负责处理心跳任务，并创建一个TimeOutTask。
     * TimeOutTask：负责处理心跳检测超时。
     * PostProcessor：负责检查心跳是否成功。
     *
     * 从NIO网络连接角度来看：
     *
     * TcpHealthCheckProcessor：相当于NIO中的Selector，确实它内部带有一个Selector。
     * TaskProcessor：相当于NIO中的Channel, 每个TaskProcessor都具有一个独立的Channel。它只负责创建和连接，并不负责检查连接的结果。
     * TimeOutTask：相当于NIO中的Channel, 它被TaskProcessor创建，并和TaskProcessor持有相同的Channel，负责检查Channel连接是否超时。
     * PostProcessor：相当于NIO中的Channel, 它主动获取已经准备好的Channel，获取的Channel就是TaskProcessor创建的Channel，负责检查连接是否成功。
     */
}
