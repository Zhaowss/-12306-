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

package org.opengoofy.index12306.framework.starter.idempotent.core.param;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.opengoofy.index12306.framework.starter.convention.exception.ClientException;
import org.opengoofy.index12306.framework.starter.idempotent.core.AbstractIdempotentExecuteHandler;
import org.opengoofy.index12306.framework.starter.idempotent.core.IdempotentContext;
import org.opengoofy.index12306.framework.starter.idempotent.core.IdempotentParamWrapper;
import org.opengoofy.index12306.frameworks.starter.user.core.UserContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于方法参数验证请求幂等性
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
public final class IdempotentParamExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentParamService {

    private final RedissonClient redissonClient;

    private final static String LOCK = "lock:param:restAPI";

    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
//        构建幂等包装器
//        通过设置当前的servlet的路径+当前的用户ID,从用户上下文中获取对应的用户的ID以及当前的切入点
        String lockKey = String.format("idempotent:path:%s:currentUserId:%s:md5:%s", getServletPath(), getCurrentUserId(), calcArgsMD5(joinPoint));
//        幂等
        return IdempotentParamWrapper.builder().lockKey(lockKey).joinPoint(joinPoint).build();
    }

    /**
     * @return 获取当前线程上下文 ServletPath
     */
    private String getServletPath() {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return sra.getRequest().getServletPath();
    }

    /**
     * @return 当前操作用户 ID
     */
    private String getCurrentUserId() {
        String userId = UserContext.getUserId();
        if(StrUtil.isBlank(userId)){
            throw new ClientException("用户ID获取失败，请登录");
        }
        return userId;
    }

    /**
     * @return joinPoint md5
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(JSON.toJSONBytes(joinPoint.getArgs()));
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
//        基于参宿的幂等的校验,主要就是通过当前的请求,当前的请求的ID,当前的切点的方法,构建分布式锁
        String lockKey = wrapper.getLockKey();
//        这个拿到锁,起始拿的是一个对应的字符
        RLock lock = redissonClient.getLock(lockKey);
//        利用当前的请求构成的参数字符串获取到一个redisson实现分布式锁
        if (!lock.tryLock()) {
//        如果此时别的请求来了,一定无法获取锁,此时显示操作过快
            throw new ClientException(wrapper.getIdempotent().message());
//            当当前的线程获取到锁之后进行对应的逻辑的处理工厂中
//            如果出现其他的线程来对当前的切点进行访问则进行拒绝,抛出异常
        }
//        将该锁存入到当前线程的Threadlocal中
        IdempotentContext.put(LOCK, lock);
//        将当前的锁放在对应的线程的thread localmap中,这样做的目的是执行完其操作后将其锁释放
    }

    @Override
    public void postProcessing() {
//        这个在执行完对应的切点的方法之后进行执行,即也就是完成我们的业务代码之后进行对应的锁的释放
//        将其锁置空
        RLock lock = null;
        try {
            lock = (RLock) IdempotentContext.getKey(LOCK);
//          此时从幂等上下文进行锁的获取
        } finally {
            if (lock != null) {
                lock.unlock();
//                如果获取到了,进行解锁
            }
        }
    }

    @Override
    public void exceptionProcessing() {
        postProcessing();
    }
}
