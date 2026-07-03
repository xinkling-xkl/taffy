<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import GeneralNav from '../nav/GeneralNav.vue'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()
const route = useRoute()

// 消息列表
const messageList = ref([])
// 加载状态
const loading = ref(true)
// 登录状态
const isLoggedIn = ref(false)
// 用户信息
const userInfo = ref({})

// 检查登录状态
const checkLoginStatus = () => {
  const currentUser = SessionManager.getCurrentUser()
  
  if (currentUser) {
    isLoggedIn.value = true
    userInfo.value = currentUser.user
  } else {
    isLoggedIn.value = false
    router.push('/userlogin')
  }
}

// 获取消息列表
const fetchMessageList = async () => {
  if (!userInfo.value.id) return
  
  loading.value = true
  try {
    const response = await axios.get('/api/message/list', {
      params: { userId: userInfo.value.id }
    })
    if (response.data.success) {
      messageList.value = response.data.data
    }
  } catch (error) {
    console.error('获取消息列表失败:', error)
    ElMessage.error('获取消息列表失败')
  } finally {
    loading.value = false
  }
}

// 标记单条消息为已读
const markAsRead = async (message) => {
  if (message.isRead === 0) {
    try {
      await axios.post('/api/message/read', null, {
        params: { id: message.id }
      })
      message.isRead = 1
      ElMessage.success('已标记为已读')
    } catch (error) {
      console.error('标记已读失败:', error)
      ElMessage.error('标记已读失败')
    }
  }
}

// 标记全部消息为已读
const markAllAsRead = async () => {
  try {
    await axios.post('/api/message/readAll', null, {
      params: { userId: userInfo.value.id }
    })
    messageList.value.forEach(m => m.isRead = 1)
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    console.error('标记全部已读失败:', error)
    ElMessage.error('标记全部已读失败')
  }
}

// 删除消息
const deleteMessage = async (message) => {
  try {
    await axios.delete(`/api/message/${message.id}`)
    messageList.value = messageList.value.filter(m => m.id !== message.id)
    ElMessage.success('消息已删除')
  } catch (error) {
    console.error('删除消息失败:', error)
    ElMessage.error('删除消息失败')
  }
}

// 确认删除
const confirmDelete = (message) => {
  ElMessageBox.confirm('确定要删除这条消息吗？', '删除消息', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleteMessage(message)
  }).catch(() => {})
}

// 点击消息处理
const onMessageClick = (message) => {
  markAsRead(message)
  // 跳转到消息详情页面
  router.push(`/message/detail/${message.id}`)
}

// 格式化消息类型
const formatMessageType = (type) => {
  const typeMap = {
    'apply_job': '兼职申请',
    'invite_student': '邀请参与',
    'contact_student': '联系消息',
    'contact_merchant': '联系消息',
    'application_status': '申请状态',
    'reply': '回复消息',
    'system': '系统通知',
    'credit_change': '信用变动',
    'evaluation_reply': '评价回复',
    'student_resign': '辞职申请',
    'student_fired': '解雇通知',
    'work_cancelled': '工作取消'
  }
  return typeMap[type] || '通知'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 生命周期钩子
onMounted(() => {
  checkLoginStatus()
  if (isLoggedIn.value) {
    fetchMessageList()
  }
})
</script>

<template>
  <div class="message-list-container">
    <GeneralNav />
    
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <button class="back-btn" @click="router.push('/parttime')">
          <span class="back-icon">←</span> 返回
        </button>
        <h1 class="page-title">消息列表</h1>
        <div class="header-actions">
          <button v-if="messageList.length > 0" class="action-btn" @click="markAllAsRead">
            全部已读
          </button>
        </div>
      </div>
    </div>
    
    <!-- 消息列表内容 -->
    <div class="content">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-icon">⏳</span>
        <p class="loading-text">加载中...</p>
      </div>
      
      <!-- 消息列表 -->
      <div v-else-if="messageList.length > 0" class="message-list">
        <div
          v-for="message in messageList"
          :key="message.id"
          class="message-item"
          :class="{ unread: message.isRead === 0 }"
          @click="onMessageClick(message)"
        >
          <div class="message-icon">{{ message.type === 'apply_job' ? '📋' : message.type === 'invite_student' ? '👥' : '📢' }}</div>
          <div class="message-content">
            <div class="message-header">
              <h3 class="message-title">{{ formatMessageType(message.type) }}</h3>
              <span class="message-time">{{ formatTime(message.createdAt) }}</span>
            </div>
            <div class="message-text">{{ message.content }}</div>
          </div>
          <div class="message-actions">
            <button class="action-btn small" @click.stop="markAsRead(message)" v-if="message.isRead === 0">
              标记已读
            </button>
            <button class="action-btn small danger" @click.stop="confirmDelete(message)">
              删除
            </button>
          </div>
          <div v-if="message.isRead === 0" class="unread-dot"></div>
        </div>
      </div>
      
      <!-- 空状态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">📭</div>
        <p class="empty-text">暂无消息</p>
        <p class="empty-subtext">您的消息会显示在这里</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* 页面标题 */
.page-header {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 20px 30px;
  margin-bottom: 30px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f5f7fa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: #e8f0ff;
  border-color: #667eea;
  color: #667eea;
}

.back-icon {
  font-size: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.action-btn:hover {
  background: #5a6fd8;
}

.action-btn.small {
  padding: 4px 8px;
  font-size: 12px;
}

.action-btn.danger {
  background: #f56c6c;
}

.action-btn.danger:hover {
  background: #f78989;
}

/* 内容区域 */
.content {
  flex: 1;
  padding: 0 30px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #666;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 16px;
  margin: 0;
}

/* 消息列表 */
.message-list {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.message-item {
  display: flex;
  align-items: flex-start;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.message-item:hover {
  background: #f9faff;
}

.message-item.unread {
  background: #f0f7ff;
}

.message-icon {
  width: 48px;
  height: 48px;
  background: #e8f0ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
  margin-right: 16px;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.message-time {
  font-size: 12px;
  color: #999;
}

.message-text {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin: 0;
}

.message-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-left: 16px;
  flex-shrink: 0;
}

.unread-dot {
  width: 10px;
  height: 10px;
  background: #f56c6c;
  border-radius: 50%;
  position: absolute;
  top: 24px;
  right: 20px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
  text-align: center;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.empty-text {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin: 0 0 8px 0;
}

.empty-subtext {
  font-size: 14px;
  color: #999;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    padding: 15px 20px;
  }
  
  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .content {
    padding: 0 20px;
  }
  
  .message-item {
    padding: 15px;
  }
  
  .message-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
  
  .message-actions {
    flex-direction: row;
    gap: 8px;
  }
}
</style>