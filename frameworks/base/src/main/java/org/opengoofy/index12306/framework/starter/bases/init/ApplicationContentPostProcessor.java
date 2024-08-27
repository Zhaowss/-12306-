/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengoofy.index12306.framework.starter.bases.init;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用初始化后置处理器，防止Spring事件被多次执行
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
//使用构造函数的方式进行注入实列对象
public class ApplicationContentPostProcessor implements ApplicationListener<ApplicationReadyEvent> {
    //主要是设置一个监听的事件，用于对ApplicationReadyyEvent的事件进行监听的处理

    //监听springboot应用的已经启动完毕的事件
    //通过对该事件的监听可以执行应用程序的后续的操作

    private final ApplicationContext applicationContext;

    /**
     * 执行标识，确保Spring事件 {@link ApplicationReadyEvent} 有且执行一次
     */
    private final AtomicBoolean executeOnlyOnce = new AtomicBoolean(false);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        //当spring启动完成之后，applicationReadyEvent事件触发该事件的发生
        //executeOnlyOnce.compareAndSet(false, true)
        // 检查和更新 executeOnlyOnce 标志，确保这段代码只执行一次。
        //也就是只执行当前的代码一次，采用一个线程安全的原子类实现该操作。CAS 原子类+CAS的操作实现线程安全
        if (!executeOnlyOnce.compareAndSet(false, true)) {
            return;
        }
        //防止事件被重复的发布
        applicationContext.publishEvent(new ApplicationInitializingEvent(this));
    }
}
