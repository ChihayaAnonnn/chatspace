package top.iceclean.chatspace.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.Zone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : Ice'Clean
 * @date : 2022-10-29
 */
public class ZoneInOutVO {

    @Data
    @AllArgsConstructor
    public static class ZoneIn {
        /** 区域信息 */
        private ZoneVO zone;
    }

    @Data
    @AllArgsConstructor
    public static class ZoneOut {
        /** 从哪些区域退出了 */
        private List<Integer> zoneOutList;
        /** 本来所在的区域有哪些子区域，都需要移除 */
        private List<Integer> zoneRemoveList;

        public ZoneOut(List<Integer> zoneOutList) {
            this.zoneOutList = zoneOutList;
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
