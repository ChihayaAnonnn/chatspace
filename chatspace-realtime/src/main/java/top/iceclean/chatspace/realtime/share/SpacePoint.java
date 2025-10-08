package top.iceclean.chatspace.realtime.share;

import lombok.Data;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间中的点
 * @author : Ice'Clean
 * @date : 2022-10-28
 */
@Data
public class SpacePoint {
    /** 享元模式，已缓存的点数据 */
    private static final ConcurrentHashMap<String, SpacePoint> POINT_MAP = new ConcurrentHashMap<>();

    private Integer x;
    private Integer y;

    public SpacePoint(Integer x, Integer y) {
        this.x = x;
        this.y = y;
    }

    /** 根据指定的坐标获取点 */
    public static SpacePoint get(int x, int y) {
        SpacePoint spacePoint = POINT_MAP.get(x + "," + y);
        if (spacePoint == null) {
            spacePoint = new SpacePoint(x, y);
            POINT_MAP.put(x + "," + y, spacePoint);
        }
        return spacePoint;
    }

    /** 根据指定的位置获取点 */
    public static SpacePoint get(SiteVO siteVO) {
        return get(siteVO.getX(), siteVO.getY());
    }

    public String toValue(int id) {
        return "(" + x + ", " + y + ", " + id + ")";
    }

    public String toPoint() {
        return "(" + x + ", " + y + ")";
    }
}
