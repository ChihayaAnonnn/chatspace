package top.iceclean.chatspace.infrastructure.dto;

import lombok.Data;
import top.iceclean.chatspace.infrastructure.constant.SessionType;

import javax.validation.constraints.NotNull;

/**
 * 好友和群聊申请
 * @author : Ice'Clean
 * @date : 2022-08-23
 */
@Data
public class RequestDTO {
    private SessionType type;

    @NotNull(message = "申请人不能为空")
    private Integer senderId;

    @NotNull(message = "目标 ID 不能为空")
    private Integer targetId;

    private String reqSrc;

    private String reqRemark;
}
