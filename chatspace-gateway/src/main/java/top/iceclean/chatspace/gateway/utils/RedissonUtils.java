package top.iceclean.chatspace.gateway.utils;

import org.redisson.Redisson;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RLock;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 使用 Redisson 实现
 * @author : Ice'Clean
 * @date : 2022-01-29
 *
 * Redis 工具类
 */
@Component
public class RedissonUtils {
    public final Redisson redisson;

    public RedissonUtils(Redisson redisson) {
        this.redisson = redisson;
    }

    public boolean keyExist(String key) {
        return redisson.getBucket(key).isExists();
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public void set(String key, Object value) {
        redisson.getBucket(key).set(value);
    }

    /**
     * 缓存基本的对象，带过期时间
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public void set(String key, Object value, Integer timeout, TimeUnit timeUnit) {
        redisson.getBucket(key).set(value, timeout, timeUnit);
    }

    /**
     * 将值原子自减 1 同时返回当前值
     * @param key 键
     * @return 自减 1 后的值
     */
    public long decrementAndGetLog(String key) {
        return redisson.getAtomicLong(key).decrementAndGet();
    }

    /**
     * 设置有效时间
     * @param key     Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(String key, long timeout, TimeUnit timeUnit) {
        return redisson.getKeys().expire(key, timeout, timeUnit);
    }

    /**
     * 获得缓存的基本对象
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public Object get(String key) {
        return redisson.getBucket(key).get();
    }

    /**
     * 删除单个对象
     * @param key   键值
     * @return 删除是否成功
     */
    public boolean delete(String key) {
        return redisson.getKeys().delete(key) == 1;
    }

    /**
     * 缓存 List 数据
     * @param key      缓存的键值
     * @param dataList 待缓存的 List 数据
     */
    public void setList(String key, Collection<?> dataList) {
        redisson.getList(key).addAll(dataList);
    }

    /**
     * 获得缓存的 List 对象
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public List<Object> getCacheList(String key) {
        return redisson.getList(key);
    }

    /**
     * 缓存 Set
     * @param key     缓存键值
     * @param dataSet 缓存的数据
     */
    public void setSet(String key, Collection<?> dataSet) {
        redisson.getSet(key).addAll(dataSet);
    }

    /**
     * 获得缓存的set
     * @param key 键值
     * @return  缓存的set
     */
    public Set<Object> getSet(String key) {
        return redisson.getSet(key);
    }

    public void hashSet(String key, Object field, Object value) {
        redisson.getMap(key).put(field, value);
    }

    public void hashIncrBy(String key, Object field, int num) {
        redisson.getMap(key).addAndGet(field, num);
    }

    public Object hashGet(String key, Object field) {
        return redisson.getMap(key).get(field);
    }

    /**
     * 获取指定 key 全部的 hash 值
     * @param key 建
     * @return 全部的值 Map
     */
    public Map<Object, Object> hashGetAll(String key) {
        return redisson.getMap(key);
    }

    /**
     * 删除 hash 指定域的值
     * @param key   键
     * @param field 域 可以多个
     */
    public Long hashDel(String key, Object... field) {
        return redisson.getMap(key).fastRemove(field);
    }

    /**
     * 判断 hash 是否存在指定域
     * @param key 键
     * @param field 域
     * @return 存在指定域返回 true
     */
    public boolean hashContains(String key, Object field) {
        return redisson.getMap(key).containsKey(field);
    }

    public Set<Object> hashScan(String key, String pattern) {
        return redisson.getMap(key).keySet(pattern);
    }

    public void geoAdd(String key, double lon, double lat, Object member) {
        redisson.getGeo(key).add(lon, lat, member);
    }

    public void geoRemove(String key, Object member) {
        redisson.getGeo(key).remove(member);
    }

    public double[] geoGet(String key, Object member) {
        Map<Object, GeoPosition> posMap = redisson.getGeo(key).pos(member);
        if (posMap != null && posMap.size() == 1) {
            GeoPosition pos = posMap.get(member);
            return new double[]{pos.getLongitude(), pos.getLatitude()};
        }
        return null;
    }

    public Map<Object, Double> geoRadius(String key, Integer member, double radius, GeoUnit unit) {
        try {
            return redisson.getGeo(key).radiusWithDistanceAsync(member, radius, unit).get();
        } catch (InterruptedException e) {
            // 被打断了，则重新计算
            System.out.println("计算 GEO 时被打断，重新计算");
            return geoRadius(key, member, radius, unit);
        } catch (ExecutionException e) {
            // 计算异常，返回空集
            System.out.println("计算 GEO 异常");
            e.printStackTrace();
        }
        return new HashMap<>(0);
    }

    /**
     * 获取两个成员的距离
     * @param key 所属的键
     * @param member1 成员 1
     * @param member2 成员 2
     * @return 单位距离
     */
    public <T> Double geoDist(String key, T member1, T member2, GeoUnit unit) {
        return redisson.getGeo(key).dist(member1, member2, unit);
    }

    /**
     * 写入位图
     * @param key 位图键
     * @param offset 偏移量
     * @param value 对应的值（T/F -> 0/1）
     */
    public void setBit(String key, int offset, boolean value) {
        redisson.getBitSet(key).set(offset, value);
    }

    /**
     * 获取位图值
     * @param key 位图键
     * @param offset 位图偏移量
     * @return 对应的值，null 表示不存在该键值对，否则为 true/false
     */
    public boolean getBit(String key, int offset) {
        return redisson.getBitSet(key).get(offset);
    }

    /**
     * 计算位图中 1 的数量
     * @param key 位图键
     * @return 数量
     */
    public long countBit(String key) {
        return redisson.getBitSet(key).cardinality();
    }

    public Set<Integer> getBitValueSet(String key) {
//        RBitSet bitSet = redisson.getBitSet(key);
//        BitSet bitSet1 = bitSet.asBitSet();
//        System.out.println(bitSet1);
//        return bitSet.
        return null;
    }


    /**
     * 向 Set 中批量添加元素
     * @param key     键
     * @param dataSet 要添加的元素集合
     */
    public void setAddAll(String key, Collection<?> dataSet) {
        System.out.println(dataSet);
        redisson.getSet(key).addAllCounted(dataSet);
    }

    /**
     * 向 Set 中添加一个值
     * @param key 键
     * @param data 值
     */
    public void setAdd(String key, Object data) {
        redisson.getSet(key).add(data);
    }

    /**
     * 获取 Set 所有成员
     * @param key 键
     * @return Set 成员集合
     */
    public Set<Object> setGet(String key) {
        return redisson.getSet(key).readAll();
    }

    /**
     * 批量移除 Set 中的元素
     * @param key 键
     * @param dataSet 要移除的元素集合
     */
    public void setRemoveAll(String key, Collection<?> dataSet) {
        redisson.getSet(key).removeAll(dataSet);
    }

    /**
     * 移除 Set 中指定元素
     * @param key 键
     * @param data 值
     * @return 是否移除成功
     */
    public boolean setRemove(String key, Object data) {
        return redisson.getSet(key).remove(data);
    }

    /**
     * 判断 Set 中是否存在指定值
     * @param key 键
     * @param data 值
     * @return 存在则返回 true
     */
    public boolean setContains(String key, Object data) {
        return redisson.getSet(key).contains(data);
    }

    /**
     * 获取 Set 集合的大小
     * @param key 键
     * @return 大小
     */
    public int setSize(String key) {
        return redisson.getSet(key).size();
    }

    /**
     * 获取列表最后一个值
     * get 的警告不用管，-1 是取最后链表的最后一个元素
     * @param key 列表键
     * @return 列表最后一个值，否则为 null
     */
    public Integer listGetLast(String key) {
        if (redisson.getKeys().countExists(key) == 0) {
            return null;
        }
        return Integer.parseInt(redisson.getList(key).get(-1).toString());
    }

    /**
     * 往列表的最后插入一个值
     * @param key 列表键
     * @param value 列表值
     * @return 是否插入成功
     */
    public boolean listPushLast(String key, int value) {
        return redisson.getList(key).add(value);
    }

    /**
     * 弹出列表的最后一个值
     * remove 的警告不用管，-1 是取最后链表的最后一个元素
     * @param key 列表键
     * @return 列表的最后一个值
     */
    public Integer listPopLast(String key) {
        if (redisson.getKeys().countExists(key) == 0) {
            return null;
        }
        return Integer.parseInt(redisson.getList(key).remove(-1).toString());
    }

    public RLock getLock(String key) {
        return redisson.getLock(key);
    }

    /**
     * 找到大于指定值的后续值
     * @param key 键
     * @param start 指定的开始值
     * @param offset 偏移量
     * @param count 取出的数量
     * @return 值数据
     */
    public List<String> sortedGetLargerKey(String key, double start, int offset, int count) {
        Collection<Object> objects = redisson.getScoredSortedSet(key)
                .valueRange(start, false, Double.MAX_VALUE, false, offset, count);
        return objects == null ? Collections.emptyList() :
                objects.stream().map(Object::toString).collect(Collectors.toList());
    }

    public List<String> sortedGetSmallerKey(String key, double end, int offset, int count) {
        Collection<Object> objects = redisson.getScoredSortedSet(key)
                .valueRange(Double.MIN_VALUE, false, end, false, offset, count);
        return objects == null ? Collections.emptyList() :
                objects.stream().map(Object::toString).collect(Collectors.toList());
    }

    public double sortedGetLastScore(String key) {
        return redisson.getScoredSortedSet(key).lastScore();
    }

    /**
     * 获取指定分数的前一个分数
     * @param key 键
     * @param end 分数
     * @return
     */
    public Double sortedGetBeforeScore(String key, double end) {
        Collection<ScoredEntry<Object>> scoredEntries = redisson.getScoredSortedSet(key)
                .entryRangeReversed(Double.MIN_VALUE, false, end, false, 0, 1);
        if (!scoredEntries.isEmpty()) {
            return scoredEntries.iterator().next().getScore();
        }
        return null;
    }

    public List<String> sortedGet(String key, double start, double end) {
        Collection<Object> objects = redisson.getScoredSortedSet(key)
                .valueRange(start, true, end, false);
        return objects == null ? Collections.emptyList() :
                objects.stream().map(Object::toString).collect(Collectors.toList());
    }

    public void sortedAdd(String key, double score, String value) {
        redisson.getScoredSortedSet(key).add(score, value);
    }

    public boolean sortedRemove(String key, Object value) {
        return redisson.getScoredSortedSet(key).remove(value);
    }

    public void sortedRemoveAll(String key, Collection<?> removeList) {
        redisson.getScoredSortedSet(key).removeAll(removeList);
    }
}