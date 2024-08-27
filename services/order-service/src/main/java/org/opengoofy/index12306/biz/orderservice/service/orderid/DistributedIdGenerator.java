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

package org.opengoofy.index12306.biz.orderservice.service.orderid;

/**
 * 全局唯一订单号生成器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class DistributedIdGenerator {

    private static final long EPOCH = 1609459200000L;
    private static final int NODE_BITS = 5;
    private static final int SEQUENCE_BITS = 7;

    private final long nodeID;
//    当前的节点的ID使用redis进行分布式的用户设备ID的申城产生的
//    因此这个redis中我们可以设置一个专门表示设设备的ID，这样话我们在初始化每个设别的ID的生成器的时候
//    其都会取redis中获取当前设备多对应的一个ID，此外为了防止ID的重复发放，我们还设设置了分布式的锁
//    通过这种设计就可以使得每个节点的服务器获取de 设备的ID都是唯一的，且每一个都会拿这个唯一的设备ID创建属于该设备的一个ID申城其
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public DistributedIdGenerator(long nodeID) {
        this.nodeID = nodeID;
    }



    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        //获取当前的时间戳，并以当前的时间戳减去一个基准的时间作为其正式的时间错
        if (timestamp < lastTimestamp) {
        //时间戳小于最后的时间戳，则拒绝生成唯一的ID
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        if (timestamp == lastTimestamp) {
            //当同一个时可进行下单的时候，就将其序列号进行加1，防止并发 的时候出现同意时间的用户订单号吗的一个重复
            //主要针对并发的时候 多个线程具备相同的时间的时候
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
             //如果说序列号已用完，则异步到下一个时间戳
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        //最终拼接处的是一个时间戳+节点的ID+序列号的一个订单号
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (nodeID << SEQUENCE_BITS) | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - EPOCH;
        }
        return timestamp;
    }
}
