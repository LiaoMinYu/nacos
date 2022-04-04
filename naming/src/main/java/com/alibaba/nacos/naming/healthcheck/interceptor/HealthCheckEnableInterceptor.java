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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Health check enable interceptor.
 *
 *  检查是否开启了健康检查
 * @author xiweng.yy
 */
public class HealthCheckEnableInterceptor extends AbstractHealthCheckInterceptor {

    /**
     *  用于拦截NacosHealthCheckTask类型的任务，拦截之后判断当前节点是否开启了健康检查。它的优先级最高。
     *
     * @param object need intercepted object
     * @return
     */
    @Override
    public boolean intercept(NacosHealthCheckTask object) {
        try {
            return !ApplicationUtils.getBean(SwitchDomain.class).isHealthCheckEnabled() || !ApplicationUtils
                    .getBean(UpgradeJudgement.class).isUseGrpcFeatures();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    /**
     * 通过两个实现可以看出AbstractHealthCheckInterceptor主要用于检查被拦截的
     * NacosHealthCheckTask任务是否应当执行后续的拦截逻辑。
     * 很显然优先级最高的拦截器HealthCheckEnableInterceptor直接决定了任务是否需要继续执行下去。
     */
}
