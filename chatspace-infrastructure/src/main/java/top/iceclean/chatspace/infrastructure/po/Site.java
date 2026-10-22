package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

/**
 * 用户位置实体
 * @author : Ice'Clean
 * @date : 2022-10-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_site")
public class    Site {
    /** 主键由应用显式写入（id = userId），不能交给数据库自增 */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;
    private Integer spaceId;
    private Integer x;
    private Integer y;
    private Integer vision;
    private Integer exist;

    public Site(SiteVO siteVO) {
        this.id = siteVO.getUserId();
        this.spaceId = siteVO.getSpaceId();
        this.x = siteVO.getX();
        this.y = siteVO.getY();
        this.vision = siteVO.getVision();
        this.exist = siteVO.getExist();
    }
}
