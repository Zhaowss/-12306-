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

package org.opengoofy.index12306.framework.starter.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;

import java.lang.reflect.Method;

/**
 * 幂等注解 AOP 拦截器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Aspect
public final class IdempotentAspect {

    /**
     * 增强方法标记 {@link Idempotent} 注解逻辑
     */
    @Around("@annotation(org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint) throws Throwable {
//        首先我们会拿到对应的幂等操作的注解的方法的注解参数
        Idempotent idempotent = getIdempotent(joinPoint);
//        获取我们的幂等处理器,我们采用的时策略模式,实现高内聚低耦合的操作
//        可以这样获取的原因是 其已经将其对应的场景和类性的bean 放到我们的容器中了
        IdempotentExecuteHandler instance = IdempotentExecuteHandlerFactory.getInstance(idempotent.scene(), idempotent.type());
        Object resultObj;
        try {
//            执行幂等的逻辑
            instance.execute(joinPoint, idempotent);
//            处理中间的业务
            resultObj = joinPoint.proceed();
            instance.postProcessing();
//            处理幂等的后置逻辑
        } catch (RepeatConsumptionException ex) {
            /**
             * 触发幂等逻辑时可能有两种情况：
             *    * 1. 消息还在处理，但是不确定是否执行成功，那么需要返回错误，方便 RocketMQ 再次通过重试队列投递
             *    * 2. 消息处理成功了，该消息直接返回成功即可
             */
            if (!ex.getError()) {
                return null;
//              此时判断我们的抛出的异常中error是否假
//                当前的这个逻辑的判断则是判定是否已完成
//                如果是则返回null,表示不需要进行进一步的处理
            }

//            当我们判断其状态还是处理中的时候:我们需要抛出异常让rocketmq进行重试
            throw ex;
        } catch (Throwable ex) {
            // 客户端消费存在异常，需要删幂等标识方便下次 RocketMQ 再次通过重试队列投递
            instance.exceptionProcessing();
//            当此时的任然还在运行中,就需要进行超时重传了
            throw ex;
//            此时抛出异常之后会使得消息队列进行重试
        } finally {
//            清理幂等的上下文容器
            IdempotentContext.clean();
        }
        return resultObj;
    }

    public static Idempotent getIdempotent(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(Idempotent.class);
    }
}
