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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.NoneHealthCheckProcessor;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delegate of health check v2.x.
 * v2健康检查处理器代理
 * @author nacos
 */
@Component("healthCheckDelegateV2")
public class HealthCheckProcessorV2Delegate implements HealthCheckProcessorV2 {
    /**
     * 不同的处理器集合
     */
    private final Map<String, HealthCheckProcessorV2> healthCheckProcessorMap = new HashMap<>();

    public HealthCheckProcessorV2Delegate(HealthCheckExtendProvider provider) {
        // 初始化SPI扩展的加载，用于获取用户自定义的processor和checker
        provider.init();
    }

    @Autowired
    public void addProcessor(Collection<HealthCheckProcessorV2> processors) {
        // 添加processor到容器，以处理类别为key
        healthCheckProcessorMap.putAll(processors.stream().filter(processor -> processor.getType() != null)
                .collect(Collectors.toMap(HealthCheckProcessorV2::getType, processor -> processor)));
    }

    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
        // 从元数据中获取处理方式的类别
        String type = metadata.getHealthyCheckType();
        // 获取指定的处理器
        HealthCheckProcessorV2 processor = healthCheckProcessorMap.get(type);
        // 若未获取到，指定一个默认的处理器，默认不作处理
        if (processor == null) {
            processor = healthCheckProcessorMap.get(NoneHealthCheckProcessor.TYPE);
        }
        // 调用处理器进行处理
        processor.process(task, service, metadata);
    }

    @Override
    public String getType() {
        return null;
    }
}
