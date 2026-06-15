const API_BASE = '';

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    loadDocuments();
    
    // 文件上传处理
    document.getElementById('fileInput').addEventListener('change', handleFileUpload);
});

// 加载文档列表
async function loadDocuments() {
    try {
        const response = await fetch(`${API_BASE}/api/documents`);
        const result = await response.json();
        const documents = result.data || [];
        
        const documentList = document.getElementById('documentList');
        documentList.innerHTML = '';
        
        if (documents.length === 0) {
            documentList.innerHTML = '<div class="document-item empty">暂无文档</div>';
            return;
        }
        
        documents.forEach(doc => {
            const item = document.createElement('div');
            item.className = 'document-item';
            item.innerHTML = `
                <span class="document-icon">${getFileIcon(doc.fileType)}</span>
                <span class="document-name">${doc.fileName}</span>
                <span class="document-date">${formatDate(doc.uploadTime)}</span>
            `;
            item.onclick = () => deleteDocument(doc.id);
            documentList.appendChild(item);
        });
    } catch (error) {
        console.error('加载文档列表失败:', error);
    }
}

// 获取文件图标
function getFileIcon(fileType) {
    const icons = {
        'pdf': '📄',
        'txt': '📝',
        'md': '📑',
        'docx': '📃'
    };
    return icons[fileType] || '📁';
}

// 格式化日期
function formatDate(dateStr) {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now - date;
    
    if (diff < 60000) {
        return '刚刚';
    } else if (diff < 3600000) {
        return `${Math.floor(diff / 60000)}分钟前`;
    } else if (diff < 86400000) {
        return `${Math.floor(diff / 3600000)}小时前`;
    } else {
        return `${date.getMonth() + 1}/${date.getDate()}`;
    }
}

// 处理文件上传
async function handleFileUpload(event) {
    const files = event.target.files;
    if (!files || files.length === 0) return;
    
    const uploadBtn = document.querySelector('.upload-btn');
    const originalText = uploadBtn.innerHTML;
    uploadBtn.innerHTML = '<span>⏳</span> 上传中...';
    uploadBtn.disabled = true;
    
    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const formData = new FormData();
        formData.append('file', file);
        formData.append('kbId', '1'); // 默认知识库 ID
        
        try {
            console.log('开始上传文件:', file.name);
            
            const response = await fetch(`${API_BASE}/api/documents/upload`, {
                method: 'POST',
                body: formData
            });
            
            console.log('响应状态:', response.status);
            
            const result = await response.json();
            console.log('响应结果:', result);
            
            if (result.code === 200) {
                console.log('上传成功:', result.data);
                alert(`✅ 成功上传：${file.name}\n状态：${result.data.status}\n分块数：${result.data.chunkCount || 0}`);
            } else {
                console.error('上传失败:', result);
                alert(`❌ 上传失败：${result.message || '未知错误'}`);
            }
        } catch (error) {
            console.error('上传异常:', error);
            alert(`❌ 上传失败：${error.message}`);
        }
    }
    
    uploadBtn.innerHTML = originalText;
    uploadBtn.disabled = false;
    loadDocuments(); // 刷新文档列表
    event.target.value = '';
}

// 删除文档
async function deleteDocument(docId) {
    if (!confirm('确定要删除这个文档吗？')) return;
    
    try {
        const response = await fetch(`${API_BASE}/api/documents/${docId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            alert('文档已删除');
            loadDocuments();
        }
    } catch (error) {
        console.error('删除失败:', error);
        alert('删除失败');
    }
}

// 发送消息
async function sendMessage() {
    const input = document.getElementById('questionInput');
    const question = input.value.trim();
    
    if (!question) return;
    
    input.value = '';
    
    // 添加用户消息
    showMessage('user', question);
    
    // 添加加载状态
    const messagesDiv = document.getElementById('chatMessages');
    const loadingMsg = document.createElement('div');
    loadingMsg.className = 'message bot';
    loadingMsg.innerHTML = `
        <div class="avatar bot">🤖</div>
        <div class="message-content">
            <div class="message-text loading">思考中...</div>
        </div>
    `;
    messagesDiv.appendChild(loadingMsg);
    scrollToBottom();
    
    try {
        const response = await fetch(`${API_BASE}/api/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ message: question, kbId: 1 })
        });
        
        // 移除加载状态
        messagesDiv.removeChild(loadingMsg);
        
        if (response.ok) {
            const result = await response.json();
            showMessage('bot', result.data?.answer || '未收到回复');
        } else {
            const error = await response.json();
            showMessage('bot', `错误：${error.message || '服务器异常'}`);
        }
    } catch (error) {
        messagesDiv.removeChild(loadingMsg);
        showMessage('bot', `连接失败：${error.message}`);
    }
}

// 显示消息
function showMessage(sender, text) {
    const messagesDiv = document.getElementById('chatMessages');
    const message = document.createElement('div');
    message.className = `message ${sender}`;
    
    const avatar = sender === 'user' ? '👤' : '🤖';
    const avatarClass = sender === 'user' ? 'user' : 'bot';
    
    message.innerHTML = `
        <div class="avatar ${avatarClass}">${avatar}</div>
        <div class="message-content">
            <div class="message-text">${formatMessage(text)}</div>
        </div>
    `;
    
    messagesDiv.appendChild(message);
    scrollToBottom();
}

// 格式化消息（支持 Markdown 简单渲染）
function formatMessage(text) {
    // 代码块
    text = text.replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
    
    // 行内代码
    text = text.replace(/`([^`]+)`/g, '<code>$1</code>');
    
    // 换行
    text = text.replace(/\n/g, '<br>');
    
    return text;
}

// 滚动到底部
function scrollToBottom() {
    const messagesDiv = document.getElementById('chatMessages');
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}
