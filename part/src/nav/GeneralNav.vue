<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()

// 显示个人中心菜单
const showUserMenu = ref(false)
// 显示消息弹窗
const showMessagePopup = ref(false)
// 登录状态
const isLoggedIn = ref(false)
// 用户信息
const userInfo = ref({
  name: '',
  id: '',
  phone: '',
  identity: '',
  credit: 0,
  image: ''
})
// 消息列表
const messageList = ref([])
// 未读消息数量
const unreadCount = ref(0)
// 消息弹窗是否在hover
const isMessagePopupHover = ref(false)
// 定时器
let messageTimer = null

// 切换个人中心菜单
const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
  if (showUserMenu.value) {
    showMessagePopup.value = false
  }
}

// 关闭个人中心菜单
const closeUserMenu = () => {
  showUserMenu.value = false
}

// 切换消息弹窗
const toggleMessagePopup = () => {
  showMessagePopup.value = !showMessagePopup.value
  if (showMessagePopup.value) {
    showUserMenu.value = false
    fetchMessageList()
  }
}

// 鼠标进入消息弹窗
const onMessagePopupMouseEnter = () => {
  isMessagePopupHover.value = true
}

// 鼠标离开消息弹窗
const onMessagePopupMouseLeave = () => {
  isMessagePopupHover.value = false
  setTimeout(() => {
    if (!isMessagePopupHover.value) {
      showMessagePopup.value = false
    }
  }, 200)
}

// 获取消息列表
const fetchMessageList = async () => {
  if (!userInfo.value.id) return
  try {
    const response = await axios.get('/api/message/list', {
      params: { userId: userInfo.value.id }
    })
    if (response.data.success) {
      messageList.value = response.data.data
      updateUnreadCount()
    }
  } catch (error) {
    console.error('获取消息列表失败:', error)
  }
}

// 获取未读消息数量
const fetchUnreadCount = async () => {
  if (!userInfo.value.id) return
  try {
    const response = await axios.get('/api/message/count', {
      params: { userId: userInfo.value.id }
    })
    if (response.data.success) {
      unreadCount.value = response.data.data
    }
  } catch (error) {
    console.error('获取未读消息数量失败:', error)
  }
}

// 更新未读消息数量
const updateUnreadCount = () => {
  unreadCount.value = messageList.value.filter(m => m.isRead === 0).length
}

// 标记单条消息为已读
const markAsRead = async (message) => {
  if (message.isRead === 0) {
    try {
      await axios.post('/api/message/read', null, {
        params: { id: message.id }
      })
      message.isRead = 1
      updateUnreadCount()
    } catch (error) {
      console.error('标记已读失败:', error)
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
    updateUnreadCount()
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

// 删除消息
const deleteMessage = async (message) => {
  try {
    await axios.delete(`/api/message/${message.id}`)
    messageList.value = messageList.value.filter(m => m.id !== message.id)
    updateUnreadCount()
    ElMessage.success('消息已删除')
  } catch (error) {
    console.error('删除消息失败:', error)
  }
}

// 格式化消息类型
const formatMessageType = (type) => {
  const typeMap = {
    'apply_job': '兼职申请',
    'invite_student': '邀请参与',
    'contact_student': '联系消息',
    'application_status': '申请状态',
    'system': '系统通知',
    'credit_change': '信用变动',
    'evaluation_reply': '评价回复'
  }
  return typeMap[type] || '通知'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

// 点击消息处理
const onMessageClick = (message) => {
  markAsRead(message)
  // 跳转到消息详情页面
  router.push(`/message/detail/${message.id}`)
  showMessagePopup.value = false
}



// 显示的红点数量（最高99+）
const displayUnreadCount = computed(() => {
  return unreadCount.value > 99 ? '99+' : unreadCount.value
})

// 跳转到登录页面
const goToLogin = () => {
  router.push('/userlogin')
  closeUserMenu()
}

// 跳转到注册页面
const goToRegistration = () => {
  router.push('/userregistration')
  closeUserMenu()
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/profile')
  closeUserMenu()
}

// 退出登录
const logout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    SessionManager.logout()
    isLoggedIn.value = false
    userInfo.value = { name: '', id: '', phone: '', identity: '', credit: 0, image: '' }
    messageList.value = []
    unreadCount.value = 0
    ElMessage.success('退出登录成功')
    router.push('/userlogin')
    closeUserMenu()
    if (messageTimer) {
      clearInterval(messageTimer)
    }
  }).catch(() => {})
}

// 打开问题反馈
const openFeedback = () => {
  router.push('/feedback')
}

// 跳转到消息列表页面
const goToMessageList = () => {
  router.push('/message/list')
  showMessagePopup.value = false
}

// 检查登录状态
const checkLoginStatus = () => {
  const currentUser = SessionManager.getCurrentUser()
  
  if (currentUser) {
    isLoggedIn.value = true
    userInfo.value = currentUser.user
  } else {
    isLoggedIn.value = false
  }
}

// 获取图片完整URL
const getImageUrl = (imagePath) => {
  if (!imagePath) return ''
  if (imagePath.startsWith('http')) return imagePath
  if (imagePath.startsWith('/uploads/')) return `http://localhost:8082${imagePath}`
  return `http://localhost:8082/uploads${imagePath}`
}

// 启动消息轮询
const startMessageTimer = () => {
  if (messageTimer) {
    clearInterval(messageTimer)
  }
  messageTimer = setInterval(() => {
    if (isLoggedIn.value && userInfo.value.id) {
      fetchUnreadCount()
    }
  }, 30000)
}

const openMatchingDialog = () => {
  router.push('/ai-match')
}
// 生命周期钩子
onMounted(() => {
  checkLoginStatus()
  if (isLoggedIn.value && userInfo.value.id) {
    fetchUnreadCount()
    startMessageTimer()
  }
})

onUnmounted(() => {
  if (messageTimer) {
    clearInterval(messageTimer)
  }
})
</script>

<template>
  <header class="general-nav">
    <!-- 左侧标题 -->
    <div class="nav-left">
      <h1 class="nav-title" @click="router.push('/parttime')" style="cursor: pointer;">校园兼职平台</h1>
    </div>

    <!-- 右侧功能区 -->
    <div class="nav-right">
      <!-- 消息按钮 -->
      <div v-if="isLoggedIn" class="message-wrapper" @mouseenter="onMessagePopupMouseEnter" @mouseleave="onMessagePopupMouseLeave">
        <button class="nav-btn message-btn" @click="toggleMessagePopup">
          <span class="btn-icon">🔔</span>
          <span class="btn-text">消息通知</span>
          <span v-if="unreadCount > 0" class="message-badge">{{ displayUnreadCount }}</span>
        </button>

        <!-- 消息弹窗 -->
        <div v-if="showMessagePopup" class="message-popup" @mouseenter="onMessagePopupMouseEnter" @mouseleave="onMessagePopupMouseLeave">
          <div class="popup-header">
            <span class="popup-title">消息通知</span>
            <div class="header-actions">
              <span v-if="unreadCount > 0" class="mark-all-read" @click="markAllAsRead">全部已读</span>
              <span v-if="messageList.length > 0" class="view-all" @click="goToMessageList">查看全部</span>
            </div>
          </div>
          <div class="message-list" v-if="messageList.length > 0">
            <div
              v-for="message in messageList"
              :key="message.id"
              class="message-item"
              :class="{ unread: message.isRead === 0 }"
              @click="onMessageClick(message)"
            >
              <div class="message-icon">{{ message.type === 'apply_job' ? '📋' : message.type === 'invite_student' ? '👥' : '📢' }}</div>
              <div class="message-content">
                <div class="message-title">{{ formatMessageType(message.type) }}</div>
                <div class="message-text">{{ message.content }}</div>
                <div class="message-time">{{ formatTime(message.createdAt) }}</div>
              </div>
              <div v-if="message.isRead === 0" class="unread-dot"></div>
            </div>
          </div>
          <div v-else class="empty-messages">
            <span>暂无消息</span>
          </div>
        </div>
      </div>

      <!-- 问题反馈按钮 -->
      <button class="nav-btn feedback-btn" @click="openFeedback">
        <span class="btn-icon">💬</span>
        <span class="btn-text">问题反馈</span>
      </button>

      <!-- 个人中心 -->
      <div class="user-center" @click="toggleUserMenu">
        <div class="user-btn">
          <!-- 用户头像 -->
          <div v-if="isLoggedIn && userInfo.image" class="user-avatar-small">
            <img :src="getImageUrl(userInfo.image)" alt="头像" />
          </div>
          <span v-else class="btn-icon">👤</span>
          <span class="btn-text">
            {{ isLoggedIn ? userInfo.name : '个人中心' }}
          </span>
          <span class="btn-arrow">▼</span>
        </div>

        <!-- 下拉菜单 -->
        <div v-if="showUserMenu" class="user-menu">
          <div v-if="isLoggedIn" class="menu-item" @click="goToProfile">个人资料</div>
          <div v-else>
            <div class="menu-item" @click="goToLogin">登录</div>
            <div class="menu-item" @click="goToRegistration">注册</div>
          </div>
          <div class="menu-divider"></div>
          <div class="menu-item logout" @click="logout">退出登录</div>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.general-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.nav-left {
  display: flex;
  align-items: center;
}

.nav-title {
  font-size: 24px;
  font-weight: 700;
  color: white;
  text-shadow: 1px 1px 3px rgba(0, 0, 0, 0.3);
  margin: 0;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
  position: relative;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-1px);
}

.btn-icon {
  font-size: 16px;
}

.btn-text {
  white-space: nowrap;
}

/* 消息按钮样式 */
.message-wrapper {
  position: relative;
}

.message-btn {
  position: relative;
}

.message-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: #f56c6c;
  color: white;
  font-size: 10px;
  font-weight: bold;
  padding: 2px 5px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}

/* 消息弹窗 */
.message-popup {
  position: absolute;
  top: 100%;
  right: 0;
  width: 360px;
  max-height: 480px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  margin-top: 10px;
  overflow: hidden;
  z-index: 1001;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border-bottom: 1px solid #eee;
  background: #fafafa;
}

.popup-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.mark-all-read {
  font-size: 12px;
  color: #667eea;
  cursor: pointer;
}

.mark-all-read:hover {
  color: #5a6fd8;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.view-all {
  font-size: 12px;
  color: #667eea;
  cursor: pointer;
}

.view-all:hover {
  color: #5a6fd8;
}

.message-list {
  max-height: 400px;
  overflow-y: auto;
}

.message-item {
  display: flex;
  align-items: flex-start;
  padding: 12px 15px;
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
  width: 36px;
  height: 36px;
  background: #e8f0ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}

.message-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.message-text {
  font-size: 12px;
  color: #666;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 6px;
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border-radius: 50%;
  position: absolute;
  top: 15px;
  right: 15px;
}

.empty-messages {
  padding: 40px 15px;
  text-align: center;
  color: #999;
  font-size: 14px;
}

/* 用户头像小图 */
.user-avatar-small {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.user-avatar-small img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 个人中心 */
.user-center {
  position: relative;
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
}

.user-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.btn-arrow {
  font-size: 10px;
}

/* 下拉菜单 */
.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  width: 150px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  margin-top: 8px;
  overflow: hidden;
  z-index: 1001;
}

.menu-item {
  padding: 12px 16px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  transition: background 0.2s;
}

.menu-item:hover {
  background: #f5f7fa;
  color: #667eea;
}

.menu-divider {
  height: 1px;
  background: #eee;
  margin: 4px 0;
}

.menu-item.logout {
  color: #f56c6c;
}

.menu-item.logout:hover {
  background: #fef0f0;
  color: #f56c6c;
}

/* 问题反馈按钮特殊样式 */
.feedback-btn {
  background: #f56c6c;
}

.feedback-btn:hover {
  background: #f78989;
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}

.message-list::-webkit-scrollbar-track {
  background: #f0f0f0;
}

/* 用户切换菜单 */
.user-switch-menu {
  position: absolute;
  top: 100%;
  right: 0;
  width: 220px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  margin-top: 8px;
  overflow: hidden;
  z-index: 1002;
}

.switch-menu-header {
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.switch-menu-list {
  max-height: 300px;
  overflow-y: auto;
}

.switch-menu-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  transition: background 0.2s;
}

.switch-menu-item:hover {
  background: #f5f7fa;
}

.switch-menu-item.active {
  background: #e8f0ff;
  color: #667eea;
}

.switch-avatar-small {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}

.switch-avatar-small img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.switch-avatar-icon {
  font-size: 24px;
  margin-right: 12px;
  flex-shrink: 0;
}

.switch-user-info {
  flex: 1;
  min-width: 0;
}

.switch-user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 2px;
}

.switch-user-identity {
  font-size: 12px;
  color: #666;
}

/* 切换菜单滚动条 */
.switch-menu-list::-webkit-scrollbar {
  width: 4px;
}

.switch-menu-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}

.switch-menu-list::-webkit-scrollbar-track {
  background: #f0f0f0;
}
</style>