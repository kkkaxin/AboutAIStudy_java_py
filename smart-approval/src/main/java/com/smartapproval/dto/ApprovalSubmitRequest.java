package com.smartapproval.dto;

import com.smartapproval.entity.ApprovalRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 提交审批申请
 */
@Data
public class ApprovalSubmitRequest {

    /** 审批类型 */
    @NotNull(message = "审批类型不能为空")
    private ApprovalRequest.ApprovalType type;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 申请内容 */
    @NotBlank(message = "申请内容不能为空")
    private String content;

    /** 结构化业务数据 - 如 {"days":3, "reason":"年假"} */
    private Map<String, Object> businessData;

    /** 涉及金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
