"""Pydantic 请求/响应模型"""
from datetime import datetime
from typing import Optional, List, Any
from pydantic import BaseModel, Field


# ========== Auth ==========

class LoginRequest(BaseModel):
    username: str
    password: str


class RegisterRequest(BaseModel):
    username: str
    password: str
    realName: str
    department: str = "技术部"
    role: str = "EMPLOYEE"


class LoginResponse(BaseModel):
    token: str
    userId: int
    username: str
    realName: str
    role: str


# ========== Approval Submit ==========

class ApprovalSubmitRequest(BaseModel):
    type: str  # EXPENSE/LEAVE/PURCHASE/TRAVEL/OVERTIME/CONTRACT/BUDGET/OTHER
    title: str
    content: str = ""
    amount: float = 0.0


class ApprovalSubmitResponse(BaseModel):
    id: int
    type: str
    title: str
    amount: float
    status: str
    riskLevel: Optional[str] = None
    aiSuggestion: Optional[str] = None
    aiAnalysis: Optional[Any] = None
    budgetAnalysis: Optional[str] = None
    complianceCheck: Optional[str] = None
    riskPoints: Optional[List[str]] = None
    suggestions: Optional[List[str]] = None
    currentLevel: int
    totalLevels: int
    createdAt: Optional[datetime] = None
    applicantName: Optional[str] = None


# ========== Approval Action ==========

class ApprovalActionRequest(BaseModel):
    recordId: int
    action: str  # APPROVE/REJECT/RETURN
    opinion: str = ""
    acceptAiSuggestion: bool = False


# ========== List Responses ==========

class RequestItem(BaseModel):
    id: int
    type: str
    title: str
    amount: float
    status: str
    riskLevel: Optional[str] = None
    currentLevel: int
    totalLevels: int
    applicantName: Optional[str] = None
    createdAt: Optional[datetime] = None

    class Config:
        from_attributes = True


class PageData(BaseModel):
    content: List[RequestItem]
    page: int
    size: int
    totalElements: int
    totalPages: int


class RecordItem(BaseModel):
    id: int
    level: int
    action: Optional[str] = None
    opinion: Optional[str] = None
    approverName: Optional[str] = None
    aiSuggestion: Optional[str] = None
    aiAccepted: bool = False
    createdAt: Optional[datetime] = None

    class Config:
        from_attributes = True


class DetailResponse(BaseModel):
    request: ApprovalSubmitResponse
    records: List[RecordItem]


# ========== API 统一响应 ==========

class ApiResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Any = None
