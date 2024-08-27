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

package org.opengoofy.index12306.frameworks.starter.user.core;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.opengoofy.index12306.framework.starter.bases.constant.UserConstant;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 用户信息传输过滤器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class UserTransmitFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String userId = httpServletRequest.getHeader(UserConstant.USER_ID_KEY);
        if (StringUtils.hasText(userId)) {
            String userName = httpServletRequest.getHeader(UserConstant.USER_NAME_KEY);
            String realName = httpServletRequest.getHeader(UserConstant.REAL_NAME_KEY);
            if (StringUtils.hasText(userName)) {
                userName = URLDecoder.decode(userName, UTF_8);
            }
            if (StringUtils.hasText(realName)) {
                realName = URLDecoder.decode(realName, UTF_8);
            }
            String token = httpServletRequest.getHeader(UserConstant.USER_TOKEN_KEY);
            UserInfoDTO userInfoDTO = UserInfoDTO.builder()
                    .userId(userId)
                    .username(userName)
                    .realName(realName)
                    .token(token)
                    .build();
            UserContext.setUser(userInfoDTO);
        }
        try {
//            将其传递给下一个的在过滤器中，你可以选择在处理完自己的逻辑后调用 FilterChain.doFilter() 方法，
//            将请求和响应对象继续传递给链中的下一个过滤器。
//            执行玩当前的过滤器的操作逻辑之后，将请求和响应传递给下一个过滤器进行处理
            filterChain.doFilter(servletRequest, servletResponse);
//            如果说这个执行完毕
        } finally {
//完成响应的业务之后，从我们de ThreadLocal的上下文中将其移除，这个是因为其属于弱引用
// 如果不进行remove的操作，可能会导致OOM
            UserContext.removeUser();
        }
    }
}

//用户请求到达: 用户发送一个 HTTP 请求到服务器。

//过滤器链执行: 请求首先经过一系列过滤器，每个过滤器可以对请求进行预处理或后处理。

//调用 doFilter: 每个过滤器调用 filterChain.doFilter(servletRequest, servletResponse)，
// 将请求和响应传递给链中的下一个过滤器。

//请求到达 DispatcherServlet: 当最后一个过滤器调用 filterChain.doFilter 时，
// 请求最终到达 DispatcherServlet。

//DispatcherServlet 处理请求: DispatcherServlet 处理请求，
// 调用相应的控制器方法，生成响应，并将响应返回给客户端。

//需要特别注意的是，
//filterChain.doFilter(servletRequest, servletResponse) 是将请求和响应对象传递给
// 过滤器链中的下一个元素的操作。在过滤器链的末尾，这个调用将请求传递到 DispatcherServlet。
// DispatcherServlet 作为前端控制器，将请求分发到适当的处理器进行处理，并生成最终的响应。
// 因此，filterChain.doFilter 是确保请求能够到达 DispatcherServlet 以便进行后续处理的关键步骤。


//需要特别注意的是，当我们的请求来的时候，其本质还是使用我们的Java的selevct,使用其
//filetrchain连对每一层的过滤对请求进行过滤设置，每一层又会调用 filterChain.doFilter(servletRequest, servletResponse);
//继续往下一层的过滤器的设置进行处理，当所有的处理的设置都完成之后，即当我们的filtercahin执行到最后一个的目的地就是
//dispactedselevcted前端控制器，继续进行请求的分发，分发到多对应的handlermapping与inceptor 构成链式处理链条中
//通过多个链式的拦截器的处理之后继续进入我们的 adapter中进行具体的业务代码的执行，并且返返回结果给对应的对象视图解析器
//执行完毕之后，返回执行我们的filter中的未执行的代码