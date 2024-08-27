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

package org.opengoofy.index12306.framework.starter.web;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.framework.starter.convention.errorcode.BaseErrorCode;
import org.opengoofy.index12306.framework.starter.convention.exception.AbstractException;
import org.opengoofy.index12306.framework.starter.convention.result.Result;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@RestControllerAdvice
//增加该注解之后可以对全局的异常进行捕获和处理
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex)
    {
        //Spring 框架使用反射机制来调用带有 @ExceptionHandler 注解的方法
        // 并将异常类型与方法签名进行匹配。框架会自动识别并注入与方法参数类型匹配的对象。
        // 如果方法参数类型是 HttpServletRequest，Spring 会提供当前的 HttpServletRequest 实例。
        // 如果方法参数类型是 MethodArgumentNotValidException，Spring 会提供发生的异常实例。
        BindingResult bindingResult = ex.getBindingResult();
        //当我们使用@valid或者@validated进行验证参数的时候，
        // 如果不匹配的时候会出现一个异常MethodArgumentNotValidException
        // 这个异常包含了一个 BindingResult 对象，里面存储了详细的验证错误信息。
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截应用内抛出的异常
     * 三种异常，我们传入其异常的公工父类 AbstarctExecption
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            //u异常的处理 一般也是链式的设计模型，
            //即也就是 判断根本异常是否为空，
            //获取异常的根本的原因
//  try {
//  代码可能抛出 IOException
//} catch (IOException e) {
//   捕获 IOException 并抛出 RuntimeException，将 IOException 作为原因
//    throw new RuntimeException("Error processing file", e);
//}
// 也就是JAVA的异常的抛出的机制采用异常链条的设计的模式
//可以记录之前的异常的，方便我们确定异常的发生的根本的原因
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex.toString(), ex.getCause());
//            ex.toString(), ex.getCause()我们通过日志记录日常发生的根本的信息，附加更加详细的错误日志
            return Results.failure(ex);
        }
        log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex.toString());
        return Results.failure(ex);
    }

    /**
     * 拦截未捕获异常
     * 前面的异常都是我们自己定义的异常，即当抛出特定的异常的时候，其会全局捕获进行处理
     * 但是被捕获的异常还有其他的并不只是有我们的Runtimeexecption的异常信息
     * 因此我们对其公工的异常类进行捕获，增加一个兜底的方案
     */
    @ExceptionHandler(value = Throwable.class)
    public Result defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StringUtils.isEmpty(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
