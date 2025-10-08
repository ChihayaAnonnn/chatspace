package top.iceclean.chatspace.infrastructure.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.po.Site;

/**
 * 用户位置响应对象
 * @author : Ice'Clean
 * @date : 2022-10-01
 */
@Data
@NoArgsConstructor
public class SiteVO {
    private UserVO user;
    private Integer spaceId;
    private Integer x;
    private Integer y;
    private Integer vision;
    private Integer exist;

    /** 是否为当前用户的好友 */
    private Boolean isFriend;

    public SiteVO(SiteDTO site, UserVO user) {
        this.user = user;
        this.spaceId = site.getSpaceId();
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    public SiteVO(Site site, UserVO user) {
        this.user = user;
        this.spaceId = site.getSpaceId();
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    /** 更新当前位置 */
    public void updateSite(SiteDTO site) {
        this.spaceId = site.getSpaceId();
        this.x = site.getX();
        this.y = site.getY();
        this.vision = site.getVision();
        this.exist = site.getExist();
    }

    /** 设置好友属性 */
    public SiteVO setFriend(Boolean friend) {
        isFriend = friend;
        return this;
    }
}
