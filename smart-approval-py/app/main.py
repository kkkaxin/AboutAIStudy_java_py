"""FastAPI 主应用 —— API 路由和启动入口"""
import logging

from fastapi import FastAPI, Depends, HTTPException, status, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session

from app.config import HOST, PORT
from app.database import get_db, init_db
from app.security import get_current_user, hash_password, create_token, verify_password
from app.models import User, Department
from app.seed import init_seed_data
from app.middleware import rate_limiter
from app.services import (
    submit_approval, execute_approval_action,
    get_pending_list, get_my_requests, get_detail, re_analyze
)
from app.schemas import (
    LoginRequest, RegisterRequest,
    ApprovalSubmitRequest, ApprovalActionRequest,
    ApiResponse, LoginResponse
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="SmartApproval - AI智能审批系统 (Python版)", version="1.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ========== 启动事件 ==========

@app.on_event("startup")
def startup():
    """应用启动：初始化数据库和种子数据"""
    init_db()
    db = next(get_db())
    try:
        init_seed_data(db)
    finally:
        db.close()
    logger.info(f"SmartApproval启动成功！端口: {PORT}")


# ========== Auth 路由 ==========

@app.post("/api/auth/login", response_model=ApiResponse)
def login(req: LoginRequest, db: Session = Depends(get_db)):
    """登录"""
    user = db.query(User).filter(User.username == req.username).first()
    if not user or not verify_password(req.password, user.password):
        raise HTTPException(status_code=400, detail="用户名或密码错误")

    token = create_token(user.id, user.username, user.role)
    data = LoginResponse(
        token=token,
        userId=user.id,
        username=user.username,
        realName=user.real_name,
        role=user.role
    )
    return ApiResponse(data=data.model_dump())


@app.post("/api/auth/register", response_model=ApiResponse)
def register(req: RegisterRequest, db: Session = Depends(get_db)):
    """注册"""
    existing = db.query(User).filter(User.username == req.username).first()
    if existing:
        raise HTTPException(status_code=400, detail="用户名已存在")

    # 查找或创建部门
    dept = db.query(Department).filter(Department.name == req.department).first()
    if not dept:
        dept = Department(name=req.department, budget_total=100000)
        db.add(dept)
        db.flush()

    user = User(
        username=req.username,
        password=hash_password(req.password),
        real_name=req.realName,
        role=req.role,
        department_id=dept.id
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    token = create_token(user.id, user.username, user.role)
    data = LoginResponse(
        token=token,
        userId=user.id,
        username=user.username,
        realName=user.real_name,
        role=user.role
    )
    return ApiResponse(data=data.model_dump())


# ========== Approval 路由 ==========

@app.post("/api/approval/submit", response_model=ApiResponse)
async def submit(
    req: ApprovalSubmitRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """提交申请 + 触发 AI 分析"""
    try:
        result = await submit_approval(db, req, user)
        return ApiResponse(data=result.model_dump())
    except Exception as e:
        logger.error("提交申请失败: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/approval/action", response_model=ApiResponse)
def action(
    req: ApprovalActionRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """审批操作"""
    try:
        result = execute_approval_action(db, req, user)
        return ApiResponse(data=result)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/api/approval/pending", response_model=ApiResponse)
def pending(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """待审批列表"""
    data = get_pending_list(db, user)
    return ApiResponse(data=data)


@app.get("/api/approval/my-requests", response_model=ApiResponse)
def my_requests(
    page: int = 0,
    size: int = 20,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """我的申请列表"""
    data = get_my_requests(db, user, page, size)
    return ApiResponse(data=data)


@app.get("/api/approval/detail/{request_id}", response_model=ApiResponse)
def detail(
    request_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """申请详情（含审批记录 + AI分析）"""
    try:
        data = get_detail(db, request_id)
        return ApiResponse(data=data.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@app.get("/api/approval/records/{request_id}", response_model=ApiResponse)
def records(
    request_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """某申请的审批记录"""
    try:
        detail = get_detail(db, request_id)
        return ApiResponse(data=[r.model_dump() for r in detail.records])
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@app.post("/api/approval/ai/analyze/{request_id}")
async def ai_analyze(
    request_id: int,
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """重新触发 AI 分析（含限流）"""
    await rate_limiter.check(request)
    try:
        result = re_analyze(db, request_id, user)
        return ApiResponse(data=result)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


# ========== 健康检查 ==========

@app.get("/api/health")
def health():
    return {"status": "ok", "service": "SmartApproval-Python"}


# ========== 静态文件 ==========

app.mount("/", StaticFiles(directory="static", html=True), name="static")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=HOST, port=PORT, reload=True)
