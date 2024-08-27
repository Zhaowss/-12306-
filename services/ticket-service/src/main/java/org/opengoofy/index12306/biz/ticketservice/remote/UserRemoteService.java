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

package org.opengoofy.index12306.biz.ticketservice.remote;

import org.opengoofy.index12306.biz.ticketservice.remote.dto.PassengerRespDTO;
import org.opengoofy.index12306.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户远程服务调用
 * 这段代码定义了一个使用 Spring Cloud Feign 的客户端接口 UserRemoteService。
 * Feign 是一个用于简化 HTTP 请求的声明式 HTTP 客户端，
 * 它可以让你通过接口的方式定义远程服务调用，Feign 会自动实现这些接口，
 * 并处理 HTTP 请求和响应的细节。
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */


/*
@FeignClient 注解用于标记一个 Feign 客户端，value 属性指定了服务的名称或 ID。

url 属性指定了服务的 URL。如果 aggregation.remote-url 这个配置项存在，
它将被用作 URL。如果配置项不存在，则使用默认值。

index12306-user${unique-name:}-service 是服务名称的一部分，
其中 ${unique-name:} 是一个可选的动态占位符，可能会在实际运行时被具体值替代。
 */
@FeignClient(value = "index12306-user${unique-name:}-service", url = "${aggregation.remote-url:}")
public interface UserRemoteService {

    /**
     * 根据乘车人 ID 集合查询乘车人列表
     * GetMapping 注解指定了 HTTP GET 请求的路径
     * /api/user-service/inner/passenger/actual/query/ids。
     */
    @GetMapping("/api/user-service/inner/passenger/actual/query/ids")
    Result<List<PassengerRespDTO>> listPassengerQueryByIds(@RequestParam("username") String username, @RequestParam("ids") List<String> ids);
}
