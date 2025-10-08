package top.iceclean.chatspace.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.iceclean.chatspace.infrastructure.dto.RequestDTO;

import java.util.Date;

/**
 * @author : Ice'Clean
 * @date : 2022-06-25
 */
@Data
@NoArgsConstructor
@TableName("t_session_request")
public class SessionRequest {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer type;
    private Integer senderId;
    private Integer targetId;
    private String reqSrc;
    private String reqRemark;
    private Integer reqStatus;
    private Date createTime;

    public SessionRequest(RequestDTO requestDTO) {
        this.type = requestDTO.getType().value();
        this.senderId = requestDTO.getSenderId();
        this.targetId = requestDTO.getTargetId();
        this.reqSrc = requestDTO.getReqSrc();
        this.reqRemark = requestDTO.getReqRemark();
    }
}
