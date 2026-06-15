// === SmartApproval App ===
const API = 'http://localhost:8081/api';
let token = '';
let currentUser = null;

// ============ Auth ============

async function handleLogin(e) {
    e.preventDefault();
    const resp = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({
            username: document.getElementById('loginUsername').value,
            password: document.getElementById('loginPassword').value
        })
    });
    const data = await resp.json();
    if (data.code === 200) {
        token = data.data.token;
        currentUser = data.data;
        showMainPage();
    } else {
        alert(data.message || '登录失败');
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const resp = await fetch(`${API}/auth/register`, {
        method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({
            username: document.getElementById('regUsername').value,
            password: document.getElementById('regPassword').value,
            realName: document.getElementById('regRealName').value,
            department: document.getElementById('regDept').value,
            role: document.getElementById('regRole').value
        })
    });
    const data = await resp.json();
    if (data.code === 200) {
        token = data.data.token;
        currentUser = data.data;
        showMainPage();
    } else {
        alert(data.message || '注册失败');
    }
}

function switchTab(tab) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.login-form').forEach(f => f.style.display = 'none');
    if (tab === 'login') {
        document.querySelector('.tab:first-child').classList.add('active');
        document.getElementById('loginForm').style.display = 'flex';
    } else {
        document.querySelector('.tab:last-child').classList.add('active');
        document.getElementById('registerForm').style.display = 'flex';
    }
}

function logout() {
    token = '';
    currentUser = null;
    document.getElementById('mainPage').style.display = 'none';
    document.getElementById('loginPage').style.display = 'flex';
}

function showMainPage() {
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('mainPage').style.display = 'block';
    document.getElementById('userInfo').textContent =
        `👤 ${currentUser.realName} (${currentUser.role})`;
    showSection('submit');
}

// ============ Navigation ============

function showSection(name) {
    document.querySelectorAll('.section').forEach(s => s.style.display = 'none');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    document.getElementById(`section-${name}`).style.display = 'block';
    event.target.classList.add('active');

    if (name === 'pending') loadPendingList();
    if (name === 'myRequests') loadMyRequests();
}

async function api(path, method = 'GET', body = null) {
    const opts = {
        method,
        headers: {'Content-Type':'application/json'}
    };
    if (token) opts.headers['Authorization'] = `Bearer ${token}`;
    if (body) opts.body = JSON.stringify(body);
    const resp = await fetch(`${API}${path}`, opts);
    return resp.json();
}

// ============ Submit Approval ============

async function handleSubmit(e) {
    e.preventDefault();
    const payload = {
        type: document.getElementById('submitType').value,
        title: document.getElementById('submitTitle').value,
        content: document.getElementById('submitContent').value,
        amount: parseFloat(document.getElementById('submitAmount').value) || 0
    };

    try {
        const data = await api('/approval/submit', 'POST', payload);
        if (data.code === 200) {
            // 显示 AI 分析结果
            const result = data.data;
            showAIResult(result);

            // 重置表单
            document.getElementById('approvalForm').reset();

            alert('提交成功！AI已生成审批建议，请查看下方分析结果。');
        } else {
            alert(data.message || '提交失败');
            document.getElementById('aiResultPanel').style.display = 'none';
        }
    } catch (err) {
        alert('请求失败: ' + err.message);
    }
}

function showAIResult(result) {
    const panel = document.getElementById('aiResultPanel');
    panel.style.display = 'block';

    // 风险等级
    const risk = result.riskLevel || 'MEDIUM';
    const badge = document.getElementById('aiRiskBadge');
    badge.textContent = '⚠️ 风险等级: ' + risk;
    badge.className = 'ai-risk-badge risk-' + (risk || 'MEDIUM');
    badge.style.display = 'inline-block';

    // AI建议
    document.getElementById('aiSuggestion').textContent =
        result.aiSuggestion || 'AI分析中...';

    // 使用后端返回的精确分析字段
    document.getElementById('aiBudget').textContent =
        result.budgetAnalysis || '等待分析';
    document.getElementById('aiCompliance').textContent =
        result.complianceCheck || '等待分析';

    // 风险点（数组）
    const riskPoints = result.riskPoints;
    if (Array.isArray(riskPoints) && riskPoints.length > 0) {
        document.getElementById('aiRiskPoints').innerHTML =
            '<ul style="margin:0;padding-left:20px">' +
            riskPoints.map(p => '<li>' + escapeHtml(p) + '</li>').join('') +
            '</ul>';
    } else {
        document.getElementById('aiRiskPoints').textContent = '无';
    }

    // 改进建议（数组）
    const suggestions = result.suggestions;
    if (Array.isArray(suggestions) && suggestions.length > 0) {
        document.getElementById('aiSuggestions').innerHTML =
            '<ul style="margin:0;padding-left:20px">' +
            suggestions.map(s => '<li>' + escapeHtml(s) + '</li>').join('') +
            '</ul>';
    } else {
        document.getElementById('aiSuggestions').textContent = '无';
    }

    // 滚动到AI面板
    panel.scrollIntoView({behavior:'smooth'});
}

// ============ Pending List ============

async function loadPendingList() {
    const container = document.getElementById('pendingList');
    try {
        const data = await api('/approval/pending');
        if (data.code !== 200 || !data.data || data.data.length === 0) {
            container.innerHTML = '<p class="empty-hint">暂无待审批申请</p>';
            return;
        }

        container.innerHTML = data.data.map(r => `
            <div class="request-item" onclick="showDetail(${r.id})">
                <div class="request-item-left">
                    <div class="request-item-title">
                        ${typeIcon(r.type)} ${r.title}
                        <span class="badge badge-pending">待审批</span>
                    </div>
                    <div class="request-item-meta">
                        申请人: ${r.applicantName} | 金额: ¥${(r.amount||0).toFixed(2)} |
                        提交: ${formatDate(r.createdAt)} |
                        AI评级: ${riskBadge(r.riskLevel)}
                    </div>
                </div>
                <div style="text-align:right">
                    <button class="btn-sm btn-approve" onclick="event.stopPropagation();quickApprove(${r.id})">同意</button>
                </div>
            </div>
        `).join('');
    } catch (err) {
        container.innerHTML = '<p class="empty-hint">加载失败</p>';
    }
}

// ============ My Requests ============

async function loadMyRequests() {
    const container = document.getElementById('myRequestList');
    try {
        const data = await api('/approval/my-requests?page=0&size=20');
        if (data.code !== 200 || !data.data || !data.data.content || data.data.content.length === 0) {
            container.innerHTML = '<p class="empty-hint">暂无申请记录</p>';
            return;
        }

        container.innerHTML = data.data.content.map(r => `
            <div class="request-item" onclick="showDetail(${r.id})">
                <div class="request-item-left">
                    <div class="request-item-title">
                        ${typeIcon(r.type)} ${r.title}
                        <span class="badge badge-${r.status.toLowerCase()}">${statusLabel(r.status)}</span>
                    </div>
                    <div class="request-item-meta">
                        金额: ¥${(r.amount||0).toFixed(2)} |
                        当前: ${r.currentLevel}/${r.totalLevels}级 |
                        提交: ${formatDate(r.createdAt)} |
                        AI: ${riskBadge(r.riskLevel)}
                    </div>
                </div>
            </div>
        `).join('');
    } catch (err) {
        container.innerHTML = '<p class="empty-hint">加载失败</p>';
    }
}

// ============ Detail Modal ============

async function showDetail(requestId) {
    const modal = document.getElementById('detailModal');
    const content = document.getElementById('detailContent');

    try {
        const data = await api(`/approval/detail/${requestId}`);
        if (data.code !== 200) {
            alert('加载失败');
            return;
        }

        const req = data.data.request;
        const records = data.data.records || [];

        content.innerHTML = `
            <div class="card" style="margin-bottom:16px">
                <h4>${typeIcon(req.type)} ${req.title}</h4>
                <div style="margin-top:12px;font-size:14px">
                    <p><strong>类型:</strong> ${typeLabel(req.type)}</p>
                    <p><strong>申请人:</strong> ${req.applicantName}</p>
                    <p><strong>金额:</strong> ¥${(req.amount||0).toFixed(2)}</p>
                    <p><strong>状态:</strong> <span class="badge badge-${req.status.toLowerCase()}">${statusLabel(req.status)}</span></p>
                    <p><strong>AI风险评级:</strong> ${riskBadge(req.riskLevel)}</p>
                    <p><strong>当前进度:</strong> ${req.currentLevel}/${req.totalLevels} 级</p>
                    <p><strong>提交时间:</strong> ${formatDate(req.createdAt)}</p>
                </div>
                <div style="margin-top:16px;padding:16px;background:var(--gray-50);border-radius:8px">
                    <strong>申请内容:</strong>
                    <p style="margin-top:8px;white-space:pre-wrap">${req.content}</p>
                </div>
            </div>

            ${req.aiSuggestion ? `
            <div class="card ai-panel" style="margin-bottom:16px">
                <h4>🤖 AI 分析结果</h4>
                <div class="ai-risk-badge risk-${req.riskLevel || 'MEDIUM'}" style="margin:8px 0">${req.riskLevel || 'N/A'}</div>
                <div class="ai-suggestion">${req.aiSuggestion}</div>
            </div>
            ` : ''}

            <div class="card">
                <h4>📋 审批记录</h4>
                <div class="record-timeline">
                    ${records.length > 0 ? records.map((r, i) => `
                        <div class="record-item ${r.action ? r.action.toLowerCase() : 'pending'}">
                            <div class="record-action">
                                <strong>第${r.level}级审批</strong>
                                <span class="badge badge-${r.action ? r.action.toLowerCase() : 'pending'}">
                                    ${r.action ? actionLabel(r.action) : '待审批'}
                                </span>
                            </div>
                            ${r.approverName ? `<p style="font-size:13px;color:var(--gray-600)">审批人: ${r.approverName}</p>` : ''}
                            ${r.opinion ? `<p style="font-size:13px;margin-top:4px">意见: ${r.opinion}</p>` : ''}
                            ${r.aiSuggestion ? `<p style="font-size:12px;color:var(--gray-600);margin-top:4px">AI建议: ${r.aiSuggestion.substring(0,80)}...</p>` : ''}
                            ${r.createdAt ? `<p style="font-size:11px;color:var(--gray-300);margin-top:4px">${formatDate(r.createdAt)}</p>` : ''}

                            ${!r.action && currentUser && currentUser.role !== 'EMPLOYEE' ? `
                            <div class="record-approval-btns">
                                <button class="btn-sm btn-approve" onclick="executeApproval(${r.id},'APPROVE')">✓ 同意</button>
                                <button class="btn-sm btn-reject" onclick="executeApproval(${r.id},'REJECT')">✕ 驳回</button>
                                <button class="btn-sm btn-return" onclick="executeApproval(${r.id},'RETURN')">↩ 退回</button>
                            </div>
                            <label class="ai-accept-check">
                                <input type="checkbox" id="acceptAi_${r.id}" checked>
                                采纳AI建议
                            </label>
                            ` : ''}
                        </div>
                    `).join('') : '<p class="empty-hint">暂无审批记录</p>'}
                </div>
            </div>
        `;

        modal.style.display = 'flex';
    } catch (err) {
        alert('加载详情失败: ' + err.message);
    }
}

async function executeApproval(recordId, action) {
    const acceptAi = document.getElementById(`acceptAi_${recordId}`);
    const opinion = prompt('请输入审批意见（可选）:');

    try {
        const data = await api('/approval/action', 'POST', {
            recordId: recordId,
            action: action,
            opinion: opinion || '',
            acceptAiSuggestion: acceptAi ? acceptAi.checked : false
        });

        if (data.code === 200) {
            alert('操作成功！');
            closeModal();
            loadPendingList();
        } else {
            alert(data.message || '操作失败');
        }
    } catch (err) {
        alert('操作失败: ' + err.message);
    }
}

async function quickApprove(requestId) {
    // 先获取records
    const data = await api(`/approval/detail/${requestId}`);
    if (data.code !== 200) return;

    const records = data.data.records || [];
    const pendingRecord = records.find(r => !r.action);
    if (!pendingRecord) {
        alert('没有待审批的记录');
        return;
    }
    await executeApproval(pendingRecord.id, 'APPROVE');
}

function closeModal() {
    document.getElementById('detailModal').style.display = 'none';
}

// ============ Helpers ============

function typeIcon(type) {
    const icons = {
        EXPENSE:'💰', LEAVE:'🏖️', PURCHASE:'🛒', TRAVEL:'✈️',
        OVERTIME:'⏰', CONTRACT:'📄', BUDGET:'📊', OTHER:'📌'
    };
    return icons[type] || '📌';
}

function typeLabel(type) {
    const labels = {
        EXPENSE:'费用报销', LEAVE:'请假申请', PURCHASE:'采购申请',
        TRAVEL:'出差申请', OVERTIME:'加班申请', CONTRACT:'合同审批',
        BUDGET:'预算调整', OTHER:'其他'
    };
    return labels[type] || type;
}

function statusLabel(status) {
    const labels = {
        PENDING:'待审批', APPROVED:'已通过', REJECTED:'已驳回',
        RETURNED:'已退回', CANCELLED:'已撤销'
    };
    return labels[status] || status;
}

function actionLabel(action) {
    const labels = {
        APPROVE:'已同意', REJECT:'已驳回', RETURN:'已退回', DELEGATE:'已转交'
    };
    return labels[action] || action;
}

function riskBadge(level) {
    if (!level || level === 'N/A') return '<span class="badge" style="background:#E5E7EB">无评级</span>';
    const cls = `risk-${level}`;
    return `<span class="ai-risk-badge ${cls}" style="font-size:10px;padding:1px 8px">${level}</span>`;
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
