package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.po.Site;

import java.util.Objects;

/**
 * 用户位置响应对象
 * @author : Ice'Clean
 * @date : 2022-10-01
 */
@Data
@NoArgsConstructor
public class SiteVO {
    private Integer userId;
    private Integer spaceId;
    private Integer x;
    private Integer y;
    private Integer vision;
    private Integer exist;

    public SiteVO(SiteDTO site, int spaceId, int userId) {
        this.userId = userId;
        this.spaceId = spaceId;
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    public SiteVO(Site site, int userId) {
        this.userId = userId;
        this.spaceId = site.getSpaceId();
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    /** 更新当前位置 */
    public void updateSite(SiteVO site) {
        this.spaceId = site.getSpaceId();
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    public static boolean isVisionOrExistChanged(SiteVO site1, SiteVO site2) {
        return !Objects.equals(site1.getVision(), site2.getVision()) ||
                !Objects.equals(site1.getExist(), site2.getExist());
    }
}
