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

package org.opengoofy.index12306.framework.starter.idempotent.core.spel;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import org.opengoofy.index12306.framework.starter.idempotent.core.IdempotentAspect;
import org.opengoofy.index12306.framework.starter.idempotent.core.IdempotentContext;
import org.opengoofy.index12306.framework.starter.idempotent.core.IdempotentParamWrapper;
import org.opengoofy.index12306.framework.starter.idempotent.core.RepeatConsumptionException;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentMQConsumeStatusEnum;
import org.opengoofy.index12306.framework.starter.idempotent.toolkit.LogUtil;
import org.opengoofy.index12306.framework.starter.idempotent.toolkit.SpELUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
public final class IdempotentSpELByMQExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private final DistributedCache distributedCache;

    private final static int TIMEOUT = 600;
    private final static String WRAPPER = "wrapper:spEL:MQ";

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        String key = (String) SpELUtil.parseKey(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
//        动态的获取对应的监听的消息的信息将其作为当前的key
        return IdempotentParamWrapper.builder().lockKey(key).joinPoint(joinPoint).build();
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String uniqueKey = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
//        使用MQ的前缀标记+我们当前的这个MQ消息队列中消息key作为键值
        Boolean setIfAbsent = ((StringRedisTemplate) distributedCache.getInstance())
                .opsForValue()
                .setIfAbsent(uniqueKey, IdempotentMQConsumeStatusEnum.CONSUMING.getCode(), TIMEOUT, TimeUnit.SECONDS);
//        通过设置放入我们的redistribution中的缓存中
//        返回两种结果   如果可以放入 返回true
//        如果返回是true的时候就是说明当前的这个可以保存成功的,因此我们可以继续执行下面的业务的逻辑
//        放入失败     返回false   出现两种情况:第一种可能是当前的放入的正在执行,或者请求已经完成过一次


//        当目前的去重表中有对应的操作之后,我们需要抛出异常,在切面层进行响应的处理
        if (setIfAbsent != null && !setIfAbsent) {
//            获取幂等标识所对应的数值,判断是否已经执行成功
            String consumeStatus = distributedCache.get(uniqueKey, String.class);
//            如果已经执行成功,那么error未false.执行中error为false
//            此处的iserror的操作是判断 当前是否是正在running中 如果是则返回true,则表明已经执行过
            boolean error = IdempotentMQConsumeStatusEnum.isError(consumeStatus);
//            当前的这个函数主要被是判断此时的额状态是不是消费中,如果是则返回true,否则返回false
            LogUtil.getLog(wrapper.getJoinPoint()).warn("[{}] MQ repeated consumption, {}.", uniqueKey, error ? "Wait for the client to delay consumption" : "Status is completed");
//            进行的日志的打印,并且抛出当前的异常
            throw new RepeatConsumptionException(error);
        }

//        当其去重表中无当前的操作的时候,直接放入进行对应的操作的执行
        IdempotentContext.put(WRAPPER, wrapper);
    }

    @Override
    public void exceptionProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
//                删除当前的键值
                distributedCache.delete(uniqueKey);
//                出现异常的时候,可以删除当前在redis的set中的队列数据,
//                让其进行下一步的投递
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to del MQ anti-heavy token.", uniqueKey);
            }
        }
    }

    @Override
    public void postProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.put(uniqueKey, IdempotentMQConsumeStatusEnum.CONSUMED.getCode(), idempotent.keyTimeout(), TimeUnit.SECONDS);
//                如果是成功了则将其状态设置为已消费
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to set MQ anti-heavy token.", uniqueKey);
            }
        }
    }
}
