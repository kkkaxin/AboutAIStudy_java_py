"""审批业务逻辑 —— 对应 ApprovalService.java"""
import logging
from datetime import datetime
from typing import Optional, List, Tuple

from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.models import (
    ApprovalRequest, ApprovalRecord, User, Department,
    WorkflowTemplate, ApprovalPolicy
)
from app.schemas import (
    ApprovalSubmitRequest, ApprovalActionRequest,
    ApprovalSubmitResponse, RequestItem, RecordItem, DetailResponse
)
from app.ai_service import analyze_approval

logger = logging.getLogger(__name__)


# ========== 辅助 ==========

def _build_request_response(request: ApprovalRequest) -> ApprovalSubmitResponse:
    """将实体转为 API 响应"""
    ai = request.ai_analysis or {}
    return ApprovalSubmitResponse(
        id=request.id,
        type=request.type,
        title=request.title,
        amount=request.amount,
        status=request.status,
        riskLevel=request.risk_level,
        aiSuggestion=request.ai_suggestion,
        aiAnalysis=ai,
        budgetAnalysis=ai.get("budgetAnalysis"),
        complianceCheck=ai.get("complianceCheck"),
        riskPoints=ai.get("riskPoints"),
        suggestions=ai.get("suggestions"),
        currentLevel=request.current_level,
        totalLevels=request.total_levels,
        createdAt=request.created_at,
        applicantName=request.applicant.real_name if request.applicant else None
    )


def _match_workflow(db: Session, approval_type: str, amount: float) -> WorkflowTemplate:
    """按 type + amount >= threshold 匹配工作流模板"""
    templates = (
        db.query(WorkflowTemplate)
        .filter(WorkflowTemplate.type == approval_type)
        .order_by(WorkflowTemplate.min_amount.desc())
        .all()
    )
    for t in templates:
        if amount >= t.min_amount:
            return t
    # 没匹配到返回该类型最小阈值模板
    if templates:
        return templates[-1]
    # 默认 1 级
    return WorkflowTemplate(type=approval_type, name="默认工作流", total_levels=1)


def _create_record(db: Session, request_id: int, level: int,
                   ai_suggestion: Optional[str] = None):
    """创建审批记录"""
    record = ApprovalRecord(
        level=level,
        request_id=request_id,
        ai_suggestion_snapshot=ai_suggestion
    )
    db.add(record)
    db.commit()
    return record


# ========== 提交申请 ==========

async def submit_approval(
    db: Session,
    req: ApprovalSubmitRequest,
    user: User
) -> ApprovalSubmitResponse:
    """提交审批申请并触发 AI 分析"""

    # ① 匹配工作流模板
    workflow = _match_workflow(db, req.type, req.amount)

    # ② 创建申请记录
    request = ApprovalRequest(
        type=req.type,
        title=req.title,
        content=req.content,
        amount=req.amount,
        applicant_id=user.id,
        current_level=1,
        total_levels=workflow.total_levels,
        status="PENDING",
        risk_level="MEDIUM"
    )
    db.add(request)
    db.flush()  # 获取 id

    # ③ AI 分析
    ai_result = await analyze_approval(db, request, user)
    request.risk_level = ai_result.get("riskLevel", "MEDIUM")
    request.ai_suggestion = ai_result.get("summary", "")
    request.ai_analysis = ai_result
    db.commit()

    # ④ 创建第一级审批记录
    _create_record(db, request.id, 1, request.ai_suggestion)

    return _build_request_response(request)


# ========== 审批动作 ==========

def execute_approval_action(
    db: Session,
    req: ApprovalActionRequest,
    approver: User
) -> dict:
    """执行审批动作（同意/驳回/退回）"""
    record = db.query(ApprovalRecord).filter(ApprovalRecord.id == req.recordId).first()
    if not record:
        raise ValueError("审批记录不存在")
    if record.action is not None:
        raise ValueError("该节点已处理，不能重复审批")

    # 更新记录
    record.approver_id = approver.id
    record.action = req.action
    record.opinion = req.opinion
    record.ai_accepted = req.acceptAiSuggestion
    db.commit()

    # 更新申请状态
    request = db.query(ApprovalRequest).filter(ApprovalRequest.id == record.request_id).first()
    if not request:
        raise ValueError("申请记录不存在")

    if req.action == "APPROVE":
        if request.current_level < request.total_levels:
            # 流转到下一级
            request.current_level += 1
            db.commit()
            _create_record(db, request.id, request.current_level,
                           request.ai_suggestion)
        else:
            request.status = "APPROVED"
            db.commit()
    elif req.action == "REJECT":
        request.status = "REJECTED"
        db.commit()
    elif req.action == "RETURN":
        request.status = "RETURNED"
        db.commit()

    return _build_request_response(request).model_dump()


# ========== 查询 ==========

def get_pending_list(db: Session, user: User) -> List[dict]:
    """获取当前用户的待审批列表"""
    # 找到当前用户作为审批人的待处理记录
    records = (
        db.query(ApprovalRecord)
        .filter(
            ApprovalRecord.action.is_(None),
            ApprovalRecord.level == 1  # 第一级审批（简化：第一级所有人可批）
        )
        .join(ApprovalRequest)
        .filter(ApprovalRequest.status == "PENDING")
        .order_by(desc(ApprovalRecord.created_at))
        .all()
    )

    result = []
    seen = set()
    for record in records:
        request = record.request
        if request.id in seen:
            continue
        seen.add(request.id)
        result.append({
            "id": request.id,
            "type": request.type,
            "title": request.title,
            "amount": request.amount,
            "status": request.status,
            "riskLevel": request.risk_level,
            "currentLevel": request.current_level,
            "totalLevels": request.total_levels,
            "applicantName": request.applicant.real_name if request.applicant else None,
            "createdAt": request.created_at.isoformat() if request.created_at else None
        })
    return result


def get_my_requests(db: Session, user: User, page: int = 0, size: int = 20) -> dict:
    """获取我的申请列表（分页）"""
    query = (
        db.query(ApprovalRequest)
        .filter(ApprovalRequest.applicant_id == user.id)
        .order_by(desc(ApprovalRequest.created_at))
    )
    total = query.count()
    items = query.offset(page * size).limit(size).all()

    content = [
        {
            "id": r.id,
            "type": r.type,
            "title": r.title,
            "amount": r.amount,
            "status": r.status,
            "riskLevel": r.risk_level,
            "currentLevel": r.current_level,
            "totalLevels": r.total_levels,
            "applicantName": r.applicant.real_name if r.applicant else None,
            "createdAt": r.created_at.isoformat() if r.created_at else None
        }
        for r in items
    ]
    return {
        "content": content,
        "page": page,
        "size": size,
        "totalElements": total,
        "totalPages": (total + size - 1) // size if size > 0 else 0
    }


def get_detail(db: Session, request_id: int) -> DetailResponse:
    """获取申请详情"""
    request = db.query(ApprovalRequest).filter(ApprovalRequest.id == request_id).first()
    if not request:
        raise ValueError("申请不存在")

    records = (
        db.query(ApprovalRecord)
        .filter(ApprovalRecord.request_id == request_id)
        .order_by(ApprovalRecord.level)
        .all()
    )

    record_items = [
        RecordItem(
            id=r.id,
            level=r.level,
            action=r.action,
            opinion=r.opinion,
            approverName=r.approver.real_name if r.approver else None,
            aiSuggestion=r.ai_suggestion_snapshot,
            aiAccepted=r.ai_accepted,
            createdAt=r.created_at
        ) for r in records
    ]

    return DetailResponse(
        request=_build_request_response(request),
        records=record_items
    )


def re_analyze(db: Session, request_id: int, user: User) -> dict:
    """重新触发 AI 分析"""
    request = db.query(ApprovalRequest).filter(ApprovalRequest.id == request_id).first()
    if not request:
        raise ValueError("申请不存在")

    # 简单同步包装异步调用
    import asyncio
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

    ai_result = loop.run_until_complete(analyze_approval(db, request, user))
    request.risk_level = ai_result.get("riskLevel", "MEDIUM")
    request.ai_suggestion = ai_result.get("summary", "")
    request.ai_analysis = ai_result
    request.updated_at = datetime.now()
    db.commit()

    return ai_result
