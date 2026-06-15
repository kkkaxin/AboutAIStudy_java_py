# Java 并发编程指南

## 一、线程基础

### 1.1 创建线程的方式

**继承 Thread 类**
```java
public class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("线程执行中...");
    }
}

// 启动线程
MyThread thread = new MyThread();
thread.start();
```

**实现 Runnable 接口**
```java
public class MyRunnable implements Runnable {
    @Override
    public void run() {
        System.out.println("线程执行中...");
    }
}

// 启动线程
Thread thread = new Thread(new MyRunnable());
thread.start();
```

**使用线程池**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> {
    System.out.println("线程池中的线程执行任务");
});
executor.shutdown();
```

### 1.2 线程状态

- NEW：新建状态
- RUNNABLE：可运行状态
- BLOCKED：阻塞状态
- WAITING：等待状态
- TIMED_WAITING：超时等待状态
- TERMINATED：终止状态

## 二、线程同步

### 2.1 synchronized 关键字

```java
public synchronized void synchronizedMethod() {
    // 同步方法
}

public void synchronizedBlock() {
    synchronized(this) {
        // 同步代码块
    }
}
```

### 2.2 ReentrantLock

```java
private final ReentrantLock lock = new ReentrantLock();

public void doSomething() {
    lock.lock();
    try {
        // 临界区代码
    } finally {
        lock.unlock();
    }
}
```

### 2.3 volatile 关键字

volatile 保证变量的可见性和禁止指令重排。

```java
private volatile boolean flag = false;

public void changeFlag() {
    flag = true; // 保证立即对其他线程可见
}
```

## 三、并发工具类

### 3.1 CountDownLatch

允许一个或多个线程等待其他线程完成操作。

```java
CountDownLatch latch = new CountDownLatch(3);

// 等待其他线程完成
latch.await();

// 完成一个任务
latch.countDown();
```

### 3.2 CyclicBarrier

让一组线程到达一个屏障时被阻塞，直到最后一个线程到达屏障。

```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("所有线程都到达屏障");
});

barrier.await();
```

### 3.3 Semaphore

控制同时访问特定资源的线程数量。

```java
Semaphore semaphore = new Semaphore(5);

semaphore.acquire(); // 获取许可
try {
    // 访问资源
} finally {
    semaphore.release(); // 释放许可
}
```

## 四、线程池

### 4.1 创建线程池

```java
// 固定大小线程池
ExecutorService fixedPool = Executors.newFixedThreadPool(10);

// 缓存线程池
ExecutorService cachedPool = Executors.newCachedThreadPool();

// 单线程池
ExecutorService singlePool = Executors.newSingleThreadExecutor();

// 定时任务线程池
ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(5);
```

### 4.2 ThreadPoolExecutor

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,  // 核心线程数
    10, // 最大线程数
    60, // 空闲线程存活时间
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(100), // 工作队列
    new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
);
```

### 4.3 线程池参数说明

1. **corePoolSize**：核心线程数
2. **maximumPoolSize**：最大线程数
3. **keepAliveTime**：线程空闲时间
4. **workQueue**：工作队列
5. **threadFactory**：线程工厂
6. **handler**：拒绝策略

## 五、最佳实践

1. 优先使用线程池，避免频繁创建和销毁线程
2. 合理设置线程池大小
3. 使用线程安全的集合类
4. 避免死锁
5. 正确处理异常
6. 及时释放资源
