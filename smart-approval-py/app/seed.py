"""种子数据初始化 —— 对应 DataInitializer.java"""
import logging
from sqlalchemy.orm import Session

from app.models import Department, User, ApprovalPolicy, WorkflowTemplate
from app.security import hash_password

logger = logging.getLogger(__name__)


def init_seed_data(db: Session):
    """首次启动时自动插入种子数据"""
    # 已有数据则跳过
    if db.query(User).count() > 0:
        logger.info("种子数据已存在，跳过初始化")
        return

    logger.info("开始初始化种子数据...")

    # ① 3 个部门
    dept_tech = Department(name="技术部", budget_total=500000, budget_used=320000)
    dept_market = Department(name="市场部", budget_total=300000, budget_used=150000)
    dept_hr = Department(name="人力资源部", budget_total=200000, budget_used=80000)
    db.add_all([dept_tech, dept_market, dept_hr])
    db.flush()

    # ② 4 个用户
    users = [
        User(username="zhangsan", password=hash_password("123456"),
             real_name="张三", role="EMPLOYEE", department_id=dept_tech.id),
        User(username="lisi", password=hash_password("123456"),
             real_name="李四", role="MANAGER", department_id=dept_tech.id),
        User(username="wangwu", password=hash_password("123456"),
             real_name="王五", role="DIRECTOR", department_id=dept_market.id),
        User(username="admin", password=hash_password("123456"),
             real_name="管理员", role="ADMIN", department_id=dept_hr.id),
    ]
    db.add_all(users)

    # ③ 6 条政策
    policies = [
        ApprovalPolicy(type="EXPENSE", name="小额费用报销",
                       min_amount=0, max_amount=500,
                       description="500元以内的费用报销，部门经理审批即可"),
        ApprovalPolicy(type="EXPENSE", name="中额费用报销",
                       min_amount=500, max_amount=5000,
                       description="500至5000元的费用报销，需部门经理+总监审批"),
        ApprovalPolicy(type="EXPENSE", name="大额费用报销",
                       min_amount=5000, max_amount=0,
                       description="5000元以上的费用报销，需三级审批"),
        ApprovalPolicy(type="LEAVE", name="短期请假",
                       min_amount=0, max_amount=3,
                       description="3天以内请假，部门经理审批"),
        ApprovalPolicy(type="LEAVE", name="长期请假",
                       min_amount=3, max_amount=7,
                       description="3至7天请假，需总监审批"),
        ApprovalPolicy(type="PURCHASE", name="采购申请",
                       min_amount=0, max_amount=10000,
                       description="1万元以内的采购申请"),
    ]
    db.add_all(policies)

    # ④ 2 个工作流
    workflows = [
        WorkflowTemplate(type="EXPENSE", name="费用报销审批流",
                         min_amount=0, total_levels=2),
        WorkflowTemplate(type="PURCHASE", name="采购审批流",
                         min_amount=0, total_levels=3),
    ]
    db.add_all(workflows)

    db.commit()
    logger.info("种子数据初始化完成 ✓")
