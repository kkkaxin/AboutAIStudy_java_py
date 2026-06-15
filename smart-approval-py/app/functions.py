"""4 个手动 Function Calling 业务函数（对应 ApprovalFunctions.java）"""
from datetime import datetime, timedelta
from typing import Optional
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.models import Department, ApprovalPolicy, ApprovalRequest, User


# ========== 数据类型定义 ==========

@dataclass
class BudgetCheckResult:
    department_name: str
    budget_total: float
    budget_used: float
    budget_remaining: float
    utilization_rate: float   # 使用率 %
    can_afford: bool          # 是否能负担此次申请


@dataclass
class PolicyResult:
    found: bool
    policy_name: str
    description: str
    min_amount: float
    max_amount: float


@dataclass
class HistoryResult:
    total_count: int
    approved_count: int
    rejected_count: int
    recent_summary: str   # 最近30天情况文字摘要


@dataclass
class SimilarCaseResult:
    total_count: int
    approved_count: int
    avg_amount: float
    summary: str


# ========== 4 个函数 ==========

def check_budget(db: Session, department_id: Optional[int], amount: float) -> BudgetCheckResult:
    """函数①: 检查部门预算"""
    if department_id is None:
        return BudgetCheckResult(
            department_name="未知部门",
            budget_total=0, budget_used=0, budget_remaining=0,
            utilization_rate=0, can_afford=False
        )
    dept = db.query(Department).filter(Department.id == department_id).first()
    if not dept:
        return BudgetCheckResult(
            department_name="未知部门",
            budget_total=0, budget_used=0, budget_remaining=0,
            utilization_rate=0, can_afford=False
        )
    remaining = dept.budget_total - dept.budget_used
    rate = (dept.budget_used / dept.budget_total * 100) if dept.budget_total > 0 else 0
    return BudgetCheckResult(
        department_name=dept.name,
        budget_total=dept.budget_total,
        budget_used=dept.budget_used,
        budget_remaining=remaining,
        utilization_rate=round(rate, 1),
        can_afford=remaining >= amount
    )


def get_approval_policy(db: Session, approval_type: str, amount: float) -> PolicyResult:
    """函数②: 获取适用政策"""
    policies = db.query(ApprovalPolicy).filter(ApprovalPolicy.type == approval_type).all()
    if not policies:
        return PolicyResult(found=False, policy_name="无", description="未找到相关政策",
                            min_amount=0, max_amount=0)
    # 找 amount 在区间内的政策
    for p in policies:
        if p.min_amount <= amount <= (p.max_amount or float("inf")):
            return PolicyResult(found=True, policy_name=p.name,
                                description=p.description or "",
                                min_amount=p.min_amount, max_amount=p.max_amount)
    # 返回该类型第一条
    p = policies[0]
    return PolicyResult(found=True, policy_name=p.name,
                        description=p.description or "",
                        min_amount=p.min_amount, max_amount=p.max_amount)


def get_applicant_history(db: Session, user_id: int, approval_type: str) -> HistoryResult:
    """函数③: 获取申请人历史"""
    all_requests = (
        db.query(ApprovalRequest)
        .filter(ApprovalRequest.applicant_id == user_id,
                ApprovalRequest.type == approval_type)
        .all()
    )
    total = len(all_requests)
    approved = sum(1 for r in all_requests if r.status == "APPROVED")
    rejected = sum(1 for r in all_requests if r.status == "REJECTED")

    # 最近30天申请
    cutoff = datetime.now() - timedelta(days=30)
    recent = [r for r in all_requests if r.created_at and r.created_at >= cutoff]
    summary = (
        f"该用户共提交{approval_type}类申请{total}次，"
        f"已批准{approved}次，已拒绝{rejected}次，"
        f"最近30天提交{len(recent)}次。"
    )
    return HistoryResult(
        total_count=total,
        approved_count=approved,
        rejected_count=rejected,
        recent_summary=summary
    )


def get_similar_cases(db: Session, approval_type: str) -> SimilarCaseResult:
    """函数④: 获取相似案例统计"""
    cases = (
        db.query(ApprovalRequest)
        .filter(ApprovalRequest.type == approval_type)
        .order_by(ApprovalRequest.created_at.desc())
        .limit(20)
        .all()
    )
    total = len(cases)
    approved = sum(1 for c in cases if c.status == "APPROVED")
    amounts = [c.amount for c in cases if c.amount > 0]
    avg_amount = sum(amounts) / len(amounts) if amounts else 0

    summary = (
        f"近20条{approval_type}类申请，"
        f"批准率{round(approved/total*100) if total else 0}%，"
        f"平均金额¥{avg_amount:.0f}。"
    )
    return SimilarCaseResult(total_count=total, approved_count=approved,
                             avg_amount=avg_amount, summary=summary)
