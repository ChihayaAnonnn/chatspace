package top.iceclean.chatspace.infrastructure.utils;

import org.springframework.stereotype.Component;

/**
 * 实际距离和经纬度的转换工具
 * @author : Ice'Clean
 * @date : 2022-11-24
 */
@Component
public class GeoUtils {

    /** 弧度常量 */
    private static final double EARTH_RADIUS = 6371.393;

    /**
     * 度换成弧度
     * @param d 度数
     * @return 弧度
     */
    private static double rad(double d) {
        return d * Math.PI / 180.0;
    }

    /**
     * 根据原点向右平移指定距离（纬度不变，经度变化）
     * @param lon 经度
     * @param lat 纬度
     * @param dist 距离，单位：千米
     * @return 平移后的横纵经纬度
     */
    public static double[] right(double lon, double lat, double dist) {
        double d = dist / EARTH_RADIUS;
        double cs = Math.cos(rad(lat));
        double c = d / cs * 180.0 / Math.PI;
        return new double[]{lon + c, lat};
    }


    /**
     * 根据原点向上平移指定距离（经度不变，纬度变化）
     * @param lon 经度
     * @param lat 纬度
     * @param dist 距离，单位：千米
     * @return 平移后的经纬度  返回类型
     */
    public static double[] top(double lon, double lat, double dist) {
        double d = dist / EARTH_RADIUS;
        double c = d / Math.PI * 180.0;
        return new double[]{lon, lat + c};
    }

    /**
     * 计算相对经纬度零点横纵距离为 x 和 y 的经纬度，单位比率为 rate
     * @param x x 坐标
     * @param y y 坐标
     * @param rate 单位比率
     * @return 转化后的经纬度
     */
    public static double[] compute(double x, double y, int rate) {
        double dx = x * rate / EARTH_RADIUS;
        double dy = y * rate / EARTH_RADIUS;
        return new double[]{dx * 180.0 / Math.PI, dy / Math.PI * 180.0};
    }

    /**
     * 计算相对坐标原点经纬度为 lon 和 lat 的实际距离，单位比率为 rate
     * @param lon 经度
     * @param lat 纬度
     * @param rate 单位比率
     * @return 虚拟坐标
     */
    public static int[] recompute(double lon, double lat, int rate) {
        int vx = (int)Math.round((lon * Math.PI / 180.0) * EARTH_RADIUS / rate);
        int vy = (int)Math.round((lat / 180.0 * Math.PI) * EARTH_RADIUS / rate);
        return new int[]{vx, vy};
    }
}
