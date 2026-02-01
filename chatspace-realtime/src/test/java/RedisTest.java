import lombok.AllArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.GeoUnit;
import org.redisson.api.RBatch;
import org.redisson.api.RGeoAsync;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.util.StopWatch;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.common.BatchExecutor;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.utils.GeoUtils;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.realtime.processor.site.GeoProcessor;

import java.util.*;

/**
 * @author : Ice'Clean
 * @date : 2023-04-07
 */
public class RedisTest {

    private Redisson redisson;
    private StopWatch stopWatch;

    @BeforeEach
    public void init() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://119.91.208.25:6000")
                .setPassword("xiaoche");
        config.setCodec(new StringCodec());
        redisson = (Redisson) Redisson.create(config);
        this.stopWatch = new StopWatch();
    }

    @AfterEach
    public void printResult() {
        StringBuilder result = new StringBuilder();
        for (StopWatch.TaskInfo task : stopWatch.getTaskInfo()) {
            result.append(task.getTaskName()).append("\t")
                    .append(task.getTimeMillis()).append("ms\n");
        }
        System.out.println(result);
    }

    //@Test
    public void writeTest() {
//        writeTest1();
//        writeTest1();
//        writeTest1();
//        writeTest1();
//        writeTest1();
//        writeTest1();
//        writeTest1();
    }

    public void writeTest1() {
        stopWatch.start("writeTest");
        redisson.getBucket("test").set("test value");
        stopWatch.stop();
        stopWatch.start("readTest");
        Object test = redisson.getBucket("test").get();
        stopWatch.stop();
        System.out.println("读取到的值：" + test);
    }

    @Test
    public void batchCoverTest() {
        SiteVO oldSiteVO = new SiteVO();
        oldSiteVO.setUserId(1);
        oldSiteVO.setX(10);
        oldSiteVO.setY(10);
        oldSiteVO.setExist(4);
        oldSiteVO.setVision(6);
        SiteVO newSiteVO = new SiteVO();
        newSiteVO.setUserId(1);
        newSiteVO.setX(10);
        newSiteVO.setY(11);
        newSiteVO.setExist(4);
        newSiteVO.setVision(6);
        int zoneId = 0;
        stopWatch.start("batchCoverTest");
        List<Set<Integer>> execute = batchGetCover(4)
                .addVisionTask(oldSiteVO, zoneId)
                .addExistTask(oldSiteVO, zoneId)
                .addUpdate(newSiteVO, zoneId)
                .addVisionTask(newSiteVO, zoneId)
                .addExistTask(newSiteVO, zoneId)
                .execute();
        stopWatch.stop();
        System.out.println(execute);
    }

    public CoverTaskBuilder batchGetCover(int num) {
        return new CoverTaskBuilder(num, this);
    }
    protected List<Set<Integer>> batchGetCover(List<CoverTask> coverTaskList, List<Integer> extraList) {
        List<Map<String, Double>> maps = batchGetCover0(coverTaskList, extraList);
        for (Map<String, Double> map : maps) {
            System.out.println(map);
        }
        return null;
    }

    /** 地图比例 */
    private static final int DEFAULT_RATE = 1;
    /** 误差允许 */
    private static final double ERROR_RATE = 1e-2;
    /** 默认单位 */
    private static final GeoUnit DEFAULT_UNIT = GeoUnit.KILOMETERS;
    @SuppressWarnings("unchecked")
    public List<Map<String, Double>> batchGetCover0(List<CoverTask> coverTaskList, List<Integer> extraList) {
        RBatch batch = redisson.createBatch();
        coverTaskList.forEach(coverTask -> {
            RGeoAsync<Object> geo = batch.getGeo(RedisKey.USER_SITE_HASH.apply(coverTask.site.getSpaceId(), coverTask.zoneId));
            if (coverTask.extra) {
                // 如果是额外操作，则处理（目前只有更新位置信息）
                // userState.setSite(coverTask.site, batch);
                double[] geoSite = GeoUtils.compute(coverTask.site.getX(), coverTask.site.getY(), DEFAULT_RATE);
                geo.addAsync(geoSite[0], geoSite[1], coverTask.site.getUserId());
            } else {
                // 否则就是正常的查询覆盖集
                double radius = DEFAULT_RATE * coverTask.getCoverValue() + ERROR_RATE;
                geo.searchWithDistanceAsync(GeoSearchArgs.from(
                        coverTask.site.getUserId()).radius(radius, DEFAULT_UNIT));
            }
        });
        List<?> responses = batch.execute().getResponses();
        extraList.forEach(idx -> responses.remove((int)idx));
        return (List<Map<String, Double>>) responses;
    }

    /** 批量获取覆盖集任务 */
    @AllArgsConstructor
    public static class CoverTask {
        /** 覆盖任务的额外标识（如更新位置） */
        private final boolean extra;
        /** 用于获取覆盖集或者更新位置信息 */
        private final SiteVO site;
        /** 所处区域 */
        private final Integer zoneId;
        /** 是否获取视野覆盖集（反之获取存在感覆盖集） */
        private final boolean vision;

        public int getCoverValue() {
            return vision ? site.getVision() : site.getExist();
        }
    }

    private static class CoverTaskBuilder {
        private final List<CoverTask> coverTaskList;
        private final RedisTest geoProcessor;
        /** 存储额外操作的下标，用于将额外操作的结果从结果集中移除掉 */
        private final List<Integer> extraList;

        public CoverTaskBuilder(int num, RedisTest geoProcessor) {
            this.coverTaskList = new ArrayList<>(num);
            this.extraList = new ArrayList<>();
            this.geoProcessor = geoProcessor;
        }

        public CoverTaskBuilder addVisionTask(SiteVO site, int zoneId) {
            coverTaskList.add(new CoverTask(false, site, zoneId, true));
            return this;
        }

        public CoverTaskBuilder addExistTask(SiteVO site, int zoneId) {
            coverTaskList.add(new CoverTask(false, site, zoneId, false));
            return this;
        }

        public CoverTaskBuilder addUpdate(SiteVO site, int zoneId) {
            extraList.add(coverTaskList.size());
            coverTaskList.add(new CoverTask(true, site, zoneId, true));
            return this;
        }

        public List<Set<Integer>> execute() {
            return geoProcessor.batchGetCover(coverTaskList, extraList);
        }
    }
}
