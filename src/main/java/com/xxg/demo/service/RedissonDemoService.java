package com.xxg.demo.service;


import net.bytebuddy.implementation.bytecode.Throw;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
@Service
public class RedissonDemoService {

    @Autowired
    private RedissonClient redissonClient;



    // 可重入锁示例：模拟商品库存扣减（防止超卖）
    public void deductStock(String productId) {
        RLock lock = redissonClient.getLock("stock:lock:" + productId);
        try {
            boolean isLocked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (isLocked){
                System.out.println("获取锁成功");
                // 执行扣减库存仓库
                Thread.sleep(1000);
            }else {
                //抛出自定义异常 传入原因 获取锁失败
                throw new RuntimeException("获取锁超时，请稍后再试");

            }

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw  new RuntimeException("获取锁失败");
        }finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
                System.out.println("释放锁成功");
            }
        }
    }

    public void handleBusiness(String userId){
        RLock fairLock = redissonClient.getFairLock("business:lock:" + userId);
        try {
            boolean isLocked = fairLock.tryLock(5, 30, TimeUnit.SECONDS);
            if (isLocked){
                System.out.println("用户 " + userId + " 排队成功，开始办理业务...");
                Thread.sleep(2000);
            }else{
                Thread.currentThread().interrupt();
            }
        }catch (InterruptedException e){

        }finally {
            if(fairLock.isHeldByCurrentThread()){
                fairLock.unlock();
            }
        }
    }
    public void demoReadWriteLock(String productId) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock("rw:lock:" + productId);
        RLock rLock = readWriteLock.readLock();
        try {
            boolean b = rLock.tryLock(5, 20, TimeUnit.SECONDS);
            // 获取锁成功 开始执行业务
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }finally {
            if(rLock.isHeldByCurrentThread()){
                rLock.unlock();
            }
        }
        RLock rLock1 = readWriteLock.writeLock();
        try {
            boolean b = rLock1.tryLock(5, 20, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }finally {
            if(rLock1.isHeldByCurrentThread()){
                rLock1.unlock();
            }
        }



    }
    // 分布式 List 示例：共享消息列表
    public void demoDistributedList() {
        // 1. 获取分布式 List（键名："msg:list"，泛型为消息类型）
        RList<String> msgList = redissonClient.getList("msg:list");

        // 2. 添加元素（支持批量添加）
        msgList.add("用户 A 登录成功");
        msgList.add("用户 B 下单成功");
        msgList.addAll(List.of("用户 C 支付成功", "用户 D 退款成功"));

        // 3. 读取元素（与 Java List API 一致）
        System.out.println("列表大小：" + msgList.size()); // 输出：4
        System.out.println("第 2 条消息：" + msgList.get(1)); // 输出："用户 B 下单成功"

        // 4. 删除元素
        msgList.remove("用户 A 登录成功");
        System.out.println("删除后列表：" + msgList); // 输出：[用户 B 下单成功, 用户 C 支付成功, 用户 D 退款成功]
    }
    public void demoDistributedMap() {
        // 1. 获取分布式 Map（键名："user:config:1001"，泛型为 <配置项, 值>）
        RMap<String, Object> userConfigMap = redissonClient.getMap("user:config:1001");

        // 2. 存储键值对（支持原子更新）
        userConfigMap.put("notify", true); // 开启通知
        userConfigMap.put("theme", "dark"); // 深色主题
        userConfigMap.put("fontSize", 16); // 字体大小

        // 3. 读取与更新（支持原子操作）
        boolean isNotify = (boolean) userConfigMap.get("notify");
        System.out.println("是否开启通知：" + isNotify); // 输出：true

        // 原子更新字体大小（+2）
        userConfigMap.putIfAbsent("fontSize", 14); // 若不存在则设置（此处已存在，不生效）
        userConfigMap.replace("fontSize", 16, 18); // 若当前值为 16，则更新为 18
        System.out.println("更新后字体大小：" + userConfigMap.get("fontSize")); // 输出：18
    }

    // 分布式 Set 示例：用户标签管理
    public void demoDistributedSet() {
        // 1. 获取分布式 Set（键名："user:tags:1001"，泛型为标签类型）
        RSet<String> userTags = redissonClient.getSet("user:tags:1001");

        // 2. 添加标签（自动去重）
        userTags.add("学生");
        userTags.add("Java 开发者");
        userTags.add("学生"); // 重复添加，无效

        // 3. 操作集合（支持交集、并集等）
        System.out.println("用户标签：" + userTags); // 输出：[学生, Java 开发者]
        System.out.println("是否包含标签 'Java 开发者'：" + userTags.contains("Java 开发者")); // 输出：true

        // 4. 与其他 Set 计算交集（模拟另一个用户的标签）
        RSet<String> otherUserTags = redissonClient.getSet("user:tags:1002");
        otherUserTags.add("Java 开发者");
        otherUserTags.add("后端工程师");

//        Set<String> commonTags = userTags.intersection(otherUserTags);
//        System.out.println("两个用户的共同标签：" + commonTags); // 输出：[Java 开发者]
    }

    // 分布式原子类示例：统计网站访问量
    public void demoDistributedAtomicLong() {
        // 1. 获取分布式原子长整型（键名："stat:visit:count"）
        RAtomicLong visitCount = redissonClient.getAtomicLong("stat:visit:count");

        // 2. 初始化（若未设置则初始化为 0）
        visitCount.set(0);

        // 3. 原子操作（自增、自减、比较并设置）
        visitCount.incrementAndGet(); // 访问量 +1
        visitCount.incrementAndGet(); // 访问量 +1
        System.out.println("当前访问量：" + visitCount.get()); // 输出：2

        // 原子自减（-1）
        visitCount.decrementAndGet();
        System.out.println("自减后访问量：" + visitCount.get()); // 输出：1

        // 比较并设置（CAS 操作）：若当前值为 1，则设置为 100
        boolean casSuccess = visitCount.compareAndSet(1, 100);
        System.out.println("CAS 操作是否成功：" + casSuccess); // 输出：true
        System.out.println("最终访问量：" + visitCount.get()); // 输出：100
    }

    // 发布/订阅示例：订单状态通知
    public void demoPubSub() {
        // 1. 获取主题（相当于“频道”，键名："order:status:topic"）
        RTopic orderTopic = redissonClient.getTopic("order:status:topic");

        // 2. 订阅主题（多服务可同时订阅，收到相同消息）
        long subscriberId = orderTopic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String message) {
                // 收到消息后的处理逻辑（如更新订单状态、发送通知）
                System.out.println("收到订单通知：" + message);
            }
        });

        // 3. 发布消息（向主题发送消息，所有订阅者都会收到）
        orderTopic.publish("订单 10086 支付成功");
        orderTopic.publish("订单 10087 已发货");

        // 4. 取消订阅（可选，如服务下线时）
        // orderTopic.removeListener(subscriberId);
    }
}
