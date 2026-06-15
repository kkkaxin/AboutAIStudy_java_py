"""数据模型 —— 对应原项目 6 张表"""
from datetime import datetime

from sqlalchemy import Column, Integer, String, Float, Text, DateTime, ForeignKey, Boolean, JSON
from sqlalchemy.orm import relationship

from app.database import Base


class Department(Base):
    """部门表 sa_department"""
    __tablename__ = "sa_department"

    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(100), nullable=False, unique=True)
    budget_total = Column(Float, default=0.0, comment="预算总额")
    budget_used = Column(Float, default=0.0, comment="已使用预算")

    users = relationship("User", back_populates="department")


class User(Base):
    """用户表 sa_user"""
    __tablename__ = "sa_user"

    id = Column(Integer, primary_key=True, autoincrement=True)
    username = Column(String(50), nullable=False, unique=True)
    password = Column(String(200), nullable=False)
    real_name = Column(String(50), nullable=False)
    role = Column(String(20), nullable=False, default="EMPLOYEE", comment="EMPLOYEE/MANAGER/DIRECTOR/ADMIN")
    department_id = Column(Integer, ForeignKey("sa_department.id"))

    department = relationship("Department", back_populates="users")
    approval_requests = relationship("ApprovalRequest", back_populates="applicant",
                                     foreign_keys="ApprovalRequest.applicant_id")
    approval_records = relationship("ApprovalRecord", back_populates="approver",
                                    foreign_keys="ApprovalRecord.approver_id")


class ApprovalPolicy(Base):
    """审批政策表 sa_approval_policy"""
    __tablename__ = "sa_approval_policy"

    id = Column(Integer, primary_key=True, autoincrement=True)
    type = Column(String(50), nullable=False, comment="审批类型: EXPENSE/LEAVE/PURCHASE...")
    name = Column(String(100), nullable=False, comment="政策名称")
    min_amount = Column(Float, default=0.0)
    max_amount = Column(Float, default=0.0)
    description = Column(Text, comment="政策说明")
    condition = Column(JSON, comment="额外条件(JSON)")


class WorkflowTemplate(Base):
    """工作流模板表 sa_workflow_template"""
    __tablename__ = "sa_workflow_template"

    id = Column(Integer, primary_key=True, autoincrement=True)
    type = Column(String(50), nullable=False, comment="审批类型")
    name = Column(String(100), nullable=False, comment="模板名称")
    min_amount = Column(Float, default=0.0, comment="最低金额阈值")
    total_levels = Column(Integer, default=1, comment="审批层级数")


class ApprovalRequest(Base):
    """审批申请表 sa_approval_request"""
    __tablename__ = "sa_approval_request"

    id = Column(Integer, primary_key=True, autoincrement=True)
    type = Column(String(50), nullable=False, comment="EXPENSE/LEAVE/PURCHASE/TRAVEL/OVERTIME/CONTRACT/BUDGET/OTHER")
    title = Column(String(200), nullable=False)
    content = Column(Text, comment="申请内容")
    amount = Column(Float, default=0.0)
    applicant_id = Column(Integer, ForeignKey("sa_user.id"), nullable=False)
    current_level = Column(Integer, default=1, comment="当前审批层级")
    total_levels = Column(Integer, default=1, comment="总审批层级")
    status = Column(String(20), default="PENDING", comment="PENDING/APPROVED/REJECTED/RETURNED/CANCELLED")
    risk_level = Column(String(20), default="MEDIUM", comment="AI风险评级: LOW/MEDIUM/HIGH/CRITICAL")
    ai_suggestion = Column(Text, comment="AI综合建议")
    ai_analysis = Column(JSON, comment="AI完整分析结果")
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)

    applicant = relationship("User", back_populates="approval_requests",
                             foreign_keys=[applicant_id])
    records = relationship("ApprovalRecord", back_populates="request",
                           foreign_keys="ApprovalRecord.request_id")


class ApprovalRecord(Base):
    """审批记录表 sa_approval_record"""
    __tablename__ = "sa_approval_record"

    id = Column(Integer, primary_key=True, autoincrement=True)
    level = Column(Integer, nullable=False, comment="审批层级(1/2/3...)")
    request_id = Column(Integer, ForeignKey("sa_approval_request.id"), nullable=False)
    approver_id = Column(Integer, ForeignKey("sa_user.id"), nullable=True)
    action = Column(String(20), nullable=True, comment="APPROVE/REJECT/RETURN")
    opinion = Column(Text, comment="审批意见")
    ai_suggestion_snapshot = Column(Text, comment="该节点时的AI建议快照")
    ai_accepted = Column(Boolean, default=False, comment="是否采纳AI建议")
    created_at = Column(DateTime, default=datetime.now)

    request = relationship("ApprovalRequest", back_populates="records",
                           foreign_keys=[request_id])
    approver = relationship("User", back_populates="approval_records",
                            foreign_keys=[approver_id])
