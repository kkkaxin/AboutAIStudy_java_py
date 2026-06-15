"""AI 分析服务 —— 对应 AIApprovalService.java"""
import json
import re
import logging
from typing import Optional

import httpx

from app.config import OLLAMA_BASE_URL, OLLAMA_MODEL, OLLAMA_TEMPERATURE
from app.models import ApprovalRequest, User
from app.functions import (
    check_budget, get_approval_policy,
    get_applicant_history, get_similar_cases
)
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是企业审批AI助手。请基于以下业务数据对审批申请进行专业分析，并严格按照JSON格式返回结果（不要有任何其他文字）。

输出格式（严格JSON）:
{
  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
  "recommendation": "APPROVE|REJECT|REVIEW",
  "summary": "100-200字综合意见",
  "budgetAnalysis": "预算分析简述",
  "complianceCheck": "合规检查结论",
  "riskPoints": ["风险点1", "风险点2"],
  "suggestions": ["改进建议1", "改进建议2"]
}

分析维度：
1. 预算分析：申请金额是否超出部门预算余额、历史使用率
2. 合规检查：是否符合企业政策，金额是否在政策范围内
3. 风险评估：综合风险等级（LOW<MEDIUM<HIGH<CRITICAL）
4. 改进建议：如何优化申请内容或减低风险"""


def _build_user_prompt(request: ApprovalRequest, budget, policy, history, similar) -> str:
    """构建 User Prompt，嵌入4项业务数据"""
    return f"""===== 申请信息 =====
类型: {request.type}
标题: {request.title}
内容: {request.content or '无'}
金额: ¥{request.amount:.2f}

===== 业务数据（通过 Function Calling 采集）=====

【预算数据】
部门: {budget.department_name}
预算总额: ¥{budget.budget_total:.0f}
已使用: ¥{budget.budget_used:.0f}（使用率 {budget.utilization_rate}%）
剩余: ¥{budget.budget_remaining:.0f}
能否负担此次申请: {'是' if budget.can_afford else '否（余额不足）'}

【适用政策】
政策名称: {policy.policy_name}
政策说明: {policy.description or '无具体说明'}
金额区间: ¥{policy.min_amount:.0f} ~ {'不限' if policy.max_amount == 0 else f'¥{policy.max_amount:.0f}'}

【申请人历史】
{history.recent_summary}
历史批准/拒绝: {history.approved_count}/{history.rejected_count}

【相似案例统计】
{similar.summary}

===== 请根据以上数据，以严格JSON格式输出分析结果 ====="""


def _parse_ai_response(text: str) -> dict:
    """从 LLM 输出中提取 JSON"""
    if not text:
        return _default_analysis("AI 返回为空")

    # 尝试直接解析
    try:
        return json.loads(text.strip())
    except json.JSONDecodeError:
        pass

    # 尝试从代码块提取
    match = re.search(r"```(?:json)?\s*([\s\S]+?)\s*```", text, re.IGNORECASE)
    if match:
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            pass

    # 尝试找到花括号范围
    match = re.search(r"\{[\s\S]*\}", text)
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            pass

    logger.warning("AI输出无法解析为JSON: %s", text[:200])
    return _default_analysis(text[:200])


def _default_analysis(reason: str = "") -> dict:
    return {
        "riskLevel": "MEDIUM",
        "recommendation": "REVIEW",
        "summary": f"AI分析暂时无法完成，需人工审核。{reason}",
        "budgetAnalysis": "待人工核查",
        "complianceCheck": "待人工核查",
        "riskPoints": ["AI分析异常，请人工评估"],
        "suggestions": ["建议人工审核所有细节"]
    }


async def analyze_approval(db: Session, request: ApprovalRequest, applicant: User) -> dict:
    """
    核心方法：
    1. 调用4个函数采集业务数据
    2. 构建 Prompt 请求 Ollama
    3. 解析 JSON 结果
    """
    # ① 采集业务数据（手动 Function Calling）
    budget = check_budget(db, applicant.department_id, request.amount)
    policy = get_approval_policy(db, request.type, request.amount)
    history = get_applicant_history(db, applicant.id, request.type)
    similar = get_similar_cases(db, request.type)

    user_prompt = _build_user_prompt(request, budget, policy, history, similar)

    # ② 调用 Ollama
    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(
                f"{OLLAMA_BASE_URL}/api/chat",
                json={
                    "model": OLLAMA_MODEL,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    "options": {"temperature": OLLAMA_TEMPERATURE},
                    "stream": False
                }
            )
            resp.raise_for_status()
            content = resp.json()["message"]["content"]
    except Exception as e:
        logger.error("Ollama 调用失败: %s", e)
        return _default_analysis(f"Ollama 连接失败: {type(e).__name__}")

    # ③ 解析
    return _parse_ai_response(content)
