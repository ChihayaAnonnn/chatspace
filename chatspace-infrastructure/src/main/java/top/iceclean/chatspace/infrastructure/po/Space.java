package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 空间实体
 * @author : Ice'Clean
 * @date : 2022-10-01
 */
@Data
@NoArgsConstructor
@TableName("t_space")
public class Space {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String spaceName;
    private Integer sizeX;
    private Integer sizeY;
}
