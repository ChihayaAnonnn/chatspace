package top.iceclean.chatspace.infrastructure.utils;

/**
 * 通过雪花算法生成 ID
 * @author : Ice'Clean
 * @date : 2022-12-17
 */
public class IdWorker {

    /** 下面两个每个 5 位，加起来就是 10 位的工作机器 ID（工作 ID + 数据 ID） */
    private final long workerId;
    private final long datacenterId;
    /** 12 位的序列号 */
    private long sequence;

    public IdWorker(long workerId, long datacenterId, long sequence){
        // sanity check for workerId
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0",MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0",MAX_DATACENTER_ID));
        }
        System.out.printf("worker starting. timestamp left shift %d, datacenter id bits %d, worker id bits %d, sequence bits %d, workerid %d",
                TIMESTAMP_LEFT_SHIFT, DATACENTER_ID_BITS, WORKER_ID_BITS, SEQUENCE_BITS, workerId);

        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.sequence = sequence;
    }

    /** 初始时间戳 */
    private static final long TW_EPOCH = 1288834974657L;

    /** 工作 ID 和数据 ID 长度为 5 位 */
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    /** 工作 ID 和数据 ID 的最大值 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    /** 序列号 ID 长度为 12 位 */
    private static final long SEQUENCE_BITS = 12L;
    /** 序列号最大值 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 工作 ID 需要左移的位数，12 位 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 数据 ID 需要左移位数 12+5=17 位 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 时间戳需要左移位数 12+5+5=22 位 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 上次时间戳，初始值为负数 */
    private long lastTimestamp = -1L;

    /** 下一个 ID 生成算法 */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 获取当前时间戳如果小于上次时间戳，则表示时间戳获取出现异常
        if (timestamp < lastTimestamp) {
            System.err.printf("clock is moving backwards.  Rejecting requests until %d.", lastTimestamp);
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds",
                    lastTimestamp - timestamp));
        }

        //获取当前时间戳如果等于上次时间戳（同一毫秒内），则在序列号加一；否则序列号赋值为0，从0开始。
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        //将上次时间戳值刷新
        lastTimestamp = timestamp;

        /*
         * 返回结果：
         * ((timestamp - TW_EPOCH) << TIMESTAMP_LEFT_SHIFT) 表示将时间戳减去初始时间戳，再左移相应位数
         * (datacenterId << DATACENTER_ID_SHIFT) 表示将数据 ID 左移相应位数
         * (workerId << WORKER_ID_SHIFT) 表示将工 ID 左移相应位数
         * | 是按位或运算符，例如：x | y，只有当 x，y 都为 0 的时候结果才为 0，其它情况结果都为 1
         * 因为个部分只有相应位上的值有意义，其它位上都是 0，所以将各部分的值进行 | 运算就能得到最终拼接好的id
         */
        return ((timestamp - TW_EPOCH) << TIMESTAMP_LEFT_SHIFT) |
                (datacenterId << DATACENTER_ID_SHIFT) |
                (workerId << WORKER_ID_SHIFT) |
                sequence;
    }

    /** 获取时间戳，并与上次时间戳比较 */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /** 获取系统时间戳 */
    private long timeGen(){
        return System.currentTimeMillis();
    }

    /** ---------------测试--------------- */
    public static void main(String[] args) throws InterruptedException {
        IdWorker worker = new IdWorker(1,1,1);
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1);
            System.out.println(worker.nextId());
        }
        System.out.println(Integer.MAX_VALUE);

    }

}