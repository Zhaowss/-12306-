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

package org.opengoofy.index12306.biz.ticketservice.mq.consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.biz.ticketservice.common.constant.TicketRocketMQConstant;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.biz.ticketservice.mq.domain.MessageWrapper;
import org.opengoofy.index12306.biz.ticketservice.mq.event.DelayCloseOrderEvent;
import org.opengoofy.index12306.biz.ticketservice.remote.TicketOrderRemoteService;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.SeatService;
import org.opengoofy.index12306.biz.ticketservice.service.TrainStationService;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.common.toolkit.BeanUtil;
import org.opengoofy.index12306.framework.starter.convention.result.Result;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentSceneEnum;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 延迟关闭订单消费者
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.ORDER_DELAY_CLOSE_TOPIC_KEY,
        selectorExpression = TicketRocketMQConstant.ORDER_DELAY_CLOSE_TAG_KEY,
        consumerGroup = TicketRocketMQConstant.TICKET_DELAY_CLOSE_CG_KEY
)
public class DelayCloseOrderConsumer implements RocketMQListener<MessageWrapper<DelayCloseOrderEvent>> {

    private final SeatService seatService;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final TrainStationService trainStationService;
    private final DistributedCache distributedCache;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;

    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:delay_close_order:",
            key = "#delayCloseOrderEventMessageWrapper.getKeys()+'_'+#delayCloseOrderEventMessageWrapper.hashCode()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Override
    public void onMessage(MessageWrapper<DelayCloseOrderEvent> delayCloseOrderEventMessageWrapper) {
//        延时关闭订单的操作
        log.info("[延迟关闭订单] 开始消费：{}", JSON.toJSONString(delayCloseOrderEventMessageWrapper));
        DelayCloseOrderEvent delayCloseOrderEvent = delayCloseOrderEventMessageWrapper.getMessage();
        String orderSn = delayCloseOrderEvent.getOrderSn();
        Result<Boolean> closedTickOrder;
        try {
            closedTickOrder = ticketOrderRemoteService.closeTickOrder(new CancelTicketOrderReqDTO(orderSn));
//            远程调用车票服务进行当前车票订单的取消
        } catch (Throwable ex) {
            log.error("[延迟关闭订单] 订单号：{} 远程调用订单服务失败", orderSn, ex);
            throw ex;
        }
        if (closedTickOrder.isSuccess() && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            if (!closedTickOrder.getData()) {
                log.info("[延迟关闭订单] 订单号：{} 用户已支付订单", orderSn);
                return;
            }
            String trainId = delayCloseOrderEvent.getTrainId();
            String departure = delayCloseOrderEvent.getDeparture();
            String arrival = delayCloseOrderEvent.getArrival();
            List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = delayCloseOrderEvent.getTrainPurchaseTicketResults();
            try {
                seatService.unlock(trainId, departure, arrival, trainPurchaseTicketResults);
//                拿到当前的延迟消息的订单信息,然后对其进行解锁的操作,也就是恢复到当前的状态
//                会对沿途的所有同类型的座位进行锁定
            } catch (Throwable ex) {
                log.error("[延迟关闭订单] 订单号：{} 回滚列车DB座位状态失败", orderSn, ex);
                throw ex;
            }
//            对当前锁定的座位进行解锁的操作
            try {
                StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
                Map<Integer, List<TrainPurchaseTicketRespDTO>> seatTypeMap = trainPurchaseTicketResults.stream()
                        .collect(Collectors.groupingBy(TrainPurchaseTicketRespDTO::getSeatType));
//             按照座位的类型进行分类
                List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
//              找到沿途的需要进行扣减缓存的车站信息(当前这个站点的信息回滚之后需要扣减对应当前的这个支线上所有的站点之间的票的缓存信息都需进行回滚)
                routeDTOList.forEach(each -> {
                    String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                    seatTypeMap.forEach((seatType, trainPurchaseTicketRespDTOList) -> {
                        stringRedisTemplate.opsForHash()
                                .increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(seatType), trainPurchaseTicketRespDTOList.size());
                    });
                });

                TicketOrderDetailRespDTO ticketOrderDetail = BeanUtil.convert(delayCloseOrderEvent, TicketOrderDetailRespDTO.class);
                ticketOrderDetail.setPassengerDetails(BeanUtil.convert(delayCloseOrderEvent.getTrainPurchaseTicketResults(), TicketOrderPassengerDetailRespDTO.class));
                ticketAvailabilityTokenBucket.rollbackInBucket(ticketOrderDetail);
//                我们用于消峰的令牌桶的方式来进行
            } catch (Throwable ex) {
                log.error("[延迟关闭订单] 订单号：{} 回滚列车Cache余票失败", orderSn, ex);
                throw ex;
            }
        }
    }
}
