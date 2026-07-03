<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElIcon } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAllFeedback, resolveFeedback, deleteFeedback as deleteFeedbackApi } from '../services/Feedbackservice.js'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
import request from '../services/request.js'

const router = useRouter()

// 选中的菜单
const activeMenu = ref('user-management')

// 当前管理员用户
const currentAdmin = ref(null)

// 用户数据
const users = ref([])

// 反馈数据
const feedbacks = ref([])

// 加载状态
const loading = ref(false)

// 分页相关
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 添加用户对话框
const dialogVisible = ref(false)

// 编辑用户对话框
const editDialogVisible = ref(false)

// 解决反馈对话框
const resolveDialogVisible = ref(false)

// 当前选中的反馈
const currentFeedback = ref(null)

// 反馈回复内容
const feedbackReply = ref('')

// 添加用户表单数据
const addUserForm = ref({
  input: '',
  password: '',
  age: 18,
  identity: '未认证',
  credit: 100
})

// 编辑用户表单数据
const editUserForm = ref({
  id: '',
  name: '',
  phone: '',
  password: '',
  age: 18,
  identity: '未认证',
  credit: 100
})

// 认证申请相关
const authenticationList = ref([])
const imagePreviewVisible = ref(false)
const imagePreviewUrl = ref('')
const rejectDialogVisible = ref(false)
const currentApplication = ref(null)
const rejectReason = ref('')
const detailDialogVisible = ref(false)
const detailApplication = ref(null)

// 兼职管理相关
const jobs = ref([])
const loadingJobs = ref(false)
const currentJob = ref(null)
const messageDialogVisible = ref(false)
const currentMerchant = ref(null)
const messageContent = ref('')

// 举报管理相关
const reports = ref([])
const loadingReports = ref(false)

// 申诉管理相关
const appeals = ref([])
const loadingAppeals = ref(false)
const appealResolveDialogVisible = ref(false)
const currentAppeal = ref(null)
const appealReply = ref('')
const appealCreditScore = ref(60)
const currentAppealCredit = ref(null)

// 切换菜单
const handleMenuClick = (menu) => {
  activeMenu.value = menu
  if (menu === 'data-display') {
    loadAdminStatistics();
  }
  if (menu === 'user-management') loadUsers()
  if (menu === 'job-management') loadJobs()
  if (menu === 'feedback') loadFeedbacks()
  if (menu === 'report-management') loadReports()
  if (menu === 'appeal-management') loadAppeals()
  if (menu === 'authentication') loadAuthenticationList()
  if (menu === 'ai-settings') loadAiAvatar()
}

// ==================== 工作时间格式化 ====================
const formatWorkTime = (timeJson) => {
  if (!timeJson) return '未设置'
  try {
    const obj = typeof timeJson === 'string' ? JSON.parse(timeJson) : timeJson
    if (obj.timeSlots && obj.timeSlots.length > 0) {
      const dayMap = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
      const periodMap = { morning: '上午', afternoon: '下午', evening: '晚上' }
      const sorted = [...obj.timeSlots].sort((a, b) => {
        if (a.day !== b.day) return a.day - b.day
        const order = { morning: 1, afternoon: 2, evening: 3 }
        return (order[a.period] || 0) - (order[b.period] || 0)
      })
      return sorted.map(s => `${dayMap[s.day]}${periodMap[s.period]}`).join('、')
    }
    return '格式异常'
  } catch {
    return '格式异常'
  }
}

// 加载反馈数据
const loadFeedbacks = async () => {
  loading.value = true
  try {
    const result = await getAllFeedback()
    if (result.success) {
      feedbacks.value = (result.data || []).filter(feedback =>
          !feedback.content || (!feedback.content.includes('举报类型：') && !feedback.content.includes('信用申诉'))
      )
    } else {
      ElMessage.error(result.message || '加载反馈数据失败')
      feedbacks.value = []
    }
  } catch (error) {
    console.error('加载反馈数据失败:', error)
    ElMessage.error('加载反馈数据失败')
    feedbacks.value = []
  } finally {
    loading.value = false
  }
}

// 加载举报数据
const loadReports = async () => {
  loadingReports.value = true
  try {
    const result = await getAllFeedback()
    if (result.success) {
      reports.value = (result.data || []).filter(feedback =>
          feedback.content && feedback.content.includes('举报类型：')
      )
    } else {
      ElMessage.error(result.message || '加载举报数据失败')
      reports.value = []
    }
  } catch (error) {
    console.error('加载举报数据失败:', error)
    ElMessage.error('加载举报数据失败')
    reports.value = []
  } finally {
    loadingReports.value = false
  }
}

// 举报处理对话框
const reportResolveDialogVisible = ref(false)
const currentReport = ref(null)
const reportReply = ref('')

const handleResolveReport = (report) => {
  currentReport.value = report
  reportReply.value = ''
  reportResolveDialogVisible.value = true
}

const submitResolveReport = async () => {
  if (!reportReply.value.trim()) {
    ElMessage.warning('请输入处理意见')
    return
  }
  try {
    const result = await resolveFeedback(currentReport.value.id, reportReply.value)
    if (result.success) {
      ElMessage.success('举报处理成功')
      loadReports()
      reportResolveDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '举报处理失败')
    }
  } catch (error) {
    console.error('处理举报失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const handleDeleteReport = async (reportId) => {
  if (confirm('确定要删除这个举报吗？')) {
    try {
      const result = await deleteFeedbackApi(reportId, 0, true)
      if (result.success) {
        ElMessage.success('举报删除成功')
        loadReports()
      } else {
        ElMessage.error(result.message || '举报删除失败')
      }
    } catch (error) {
      console.error('删除举报失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    }
  }
}

// 加载申诉数据
const loadAppeals = async () => {
  loadingAppeals.value = true
  try {
    const result = await getAllFeedback()
    if (result.success) {
      appeals.value = (result.data || []).filter(feedback =>
          feedback.content && feedback.content.includes('信用申诉')
      )
    } else {
      ElMessage.error(result.message || '加载申诉数据失败')
      appeals.value = []
    }
  } catch (error) {
    console.error('加载申诉数据失败:', error)
    ElMessage.error('加载申诉数据失败')
    appeals.value = []
  } finally {
    loadingAppeals.value = false
  }
}

const handleResolveAppeal = async (appeal) => {
  currentAppeal.value = appeal
  appealReply.value = ''
  appealCreditScore.value = 60
  currentAppealCredit.value = null
  appealResolveDialogVisible.value = true
  try {
    const res = await axios.get(`http://localhost:8082/api/user/${appeal.userId}`)
    if (res.data.success) {
      currentAppealCredit.value = res.data.credit ?? 0
    }
  } catch {
    currentAppealCredit.value = null
  }
}

const submitResolveAppeal = async () => {
  if (appealCreditScore.value == null || appealCreditScore.value < 0 || appealCreditScore.value > 100) {
    ElMessage.warning('请输入有效的信用分值（0-100）')
    return
  }
  try {
    const userId = currentAppeal.value.userId
    const userRes = await axios.get(`http://localhost:8082/api/user/${userId}`)
    const currentCredit = userRes.data.credit
    const changeScore = appealCreditScore.value - currentCredit

    if (changeScore !== 0) {
      await axios.post('/api/credit/update', null, {
        params: {
          userId: userId,
          changeScore: changeScore,
          reason: '申诉处理',
          actionType: 'appeal'
        }
      })
    }

    const replyText = `管理员已将您的信用分调整为${appealCreditScore.value}分。${appealReply.value ? '处理意见：' + appealReply.value : ''}`
    const result = await resolveFeedback(currentAppeal.value.id, replyText)
    if (result.success) {
      ElMessage.success('申诉处理成功')
      loadAppeals()
      appealResolveDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '申诉处理失败')
    }
  } catch (error) {
    console.error('处理申诉失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const handleDeleteAppeal = async (appealId) => {
  if (confirm('确定要删除这个申诉吗？')) {
    try {
      const result = await deleteFeedbackApi(appealId, 0, true)
      if (result.success) {
        ElMessage.success('申诉删除成功')
        loadAppeals()
      } else {
        ElMessage.error(result.message || '申诉删除失败')
      }
    } catch (error) {
      console.error('删除申诉失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    }
  }
}

const handleResolveFeedback = (feedback) => {
  currentFeedback.value = feedback
  feedbackReply.value = ''
  resolveDialogVisible.value = true
}

const getImageUrl = (imagePath) => {
  if (!imagePath) return ''
  if (imagePath.startsWith('http')) return imagePath
  if (imagePath.startsWith('/uploads/')) return `http://localhost:8082${imagePath}`
  return `http://localhost:8082/uploads${imagePath}`
}

const feedbackImagePreviewVisible = ref(false)
const feedbackImagePreviewUrl = ref('')

const previewFeedbackImage = (imagePath) => {
  if (imagePath) {
    feedbackImagePreviewUrl.value = getImageUrl(imagePath)
    feedbackImagePreviewVisible.value = true
  }
}

const submitResolveFeedback = async () => {
  if (!feedbackReply.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  try {
    const result = await resolveFeedback(currentFeedback.value.id, feedbackReply.value)
    if (result.success) {
      ElMessage.success('反馈解决成功')
      loadFeedbacks()
      resolveDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '反馈解决失败')
    }
  } catch (error) {
    console.error('解决反馈失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const handleDeleteFeedback = async (feedbackId) => {
  if (confirm('确定要删除这个反馈吗？')) {
    try {
      const result = await deleteFeedbackApi(feedbackId, 0, true)
      if (result.success) {
        ElMessage.success('反馈删除成功')
        loadFeedbacks()
      } else {
        ElMessage.error(result.message || '反馈删除失败')
      }
    } catch (error) {
      console.error('删除反馈失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    }
  }
}

const loadUsers = async (page = 1, size = 10) => {
  loading.value = true
  try {
    const data = await request(`/api/user?page=${page}&size=${size}`)
    users.value = data.users || []
    total.value = data.total || 0
    if (users.value && Array.isArray(users.value)) {
      users.value.forEach(user => {
        if (user.password) {
          user.password = '******'
        }
      })
    }
  } catch (error) {
    console.error('加载用户数据失败:', error)
    ElMessage.error('加载用户数据失败')
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleEditUser = (user) => {
  editUserForm.value = {
    id: user.id,
    name: user.name,
    phone: user.phone,
    password: '',
    age: user.age,
    identity: user.identity,
    credit: user.credit
  }
  editDialogVisible.value = true
}

const handleUpdateUser = async () => {
  if (!editUserForm.value.name) {
    ElMessage.error('用户名不能为空')
    return
  }
  try {
    const data = await request(`/api/user/${editUserForm.value.id}`, {
      method: 'POST',
      body: JSON.stringify({
        name: editUserForm.value.name,
        phone: editUserForm.value.phone,
        password: editUserForm.value.password || users.value.find(u => u.id === editUserForm.value.id).password,
        age: editUserForm.value.age,
        identity: editUserForm.value.identity,
        credit: editUserForm.value.credit
      })
    })
    if (data.success) {
      ElMessage.success('修改用户成功')
      loadUsers(currentPage.value, pageSize.value)
      editDialogVisible.value = false
    } else {
      ElMessage.error(data.message || '修改用户失败')
    }
  } catch (error) {
    console.error('修改用户失败:', error)
    ElMessage.error('修改用户失败，请稍后重试')
  }
}

const handleDeleteUser = async (userId) => {
  if (confirm('确定要删除这个用户吗？')) {
    try {
      const data = await request(`/api/user/${userId}`, { method: 'DELETE' })
      if (data.success) {
        ElMessage.success('删除用户成功')
        loadUsers(currentPage.value, pageSize.value)
      } else {
        ElMessage.error(data.message || '删除用户失败')
      }
    } catch (error) {
      console.error('删除用户失败:', error)
      ElMessage.error('删除用户失败，请稍后重试')
    }
  }
}

const handleLogout = async () => {
  try {
    const data = await request('/api/user/logout', { method: 'POST' })
    if (data.success) {
      SessionManager.logout()
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.success('登出成功')
      router.push('/userlogin')
    } else {
      ElMessage.error(data.message || '登出失败')
    }
  } catch (error) {
    console.error('登出失败:', error)
    SessionManager.logout()
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    ElMessage.success('登出成功')
    router.push('/userlogin')
  }
}

const handleAddUser = async () => {
  if (!addUserForm.value.input) {
    ElMessage.error('用户名/手机号不能为空')
    return
  }
  if (!addUserForm.value.password) {
    ElMessage.error('密码不能为空')
    return
  }
  try {
    const data = await request('/api/user', {
      method: 'POST',
      body: JSON.stringify({
        name: addUserForm.value.input,
        phone: addUserForm.value.input,
        password: addUserForm.value.password,
        age: addUserForm.value.age,
        identity: addUserForm.value.identity,
        credit: addUserForm.value.credit
      })
    })
    if (data.success) {
      ElMessage.success('添加用户成功')
      loadUsers()
      dialogVisible.value = false
      addUserForm.value = {
        input: '',
        password: '',
        age: 18,
        identity: '学生',
        credit: 100
      }
    } else {
      ElMessage.error('添加用户失败，请稍后重试')
    }
  } catch (error) {
    console.error('添加用户失败:', error)
    ElMessage.error('添加用户失败，请稍后重试')
  }
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadUsers(val, pageSize.value)
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadUsers(currentPage.value, val)
}

const loadAuthenticationList = async () => {
  try {
    loading.value = true
    const response = await axios.get('http://localhost:8082/api/identity')
    if (response.data.success) {
      authenticationList.value = response.data.data
    }
  } catch (error) {
    console.error('加载认证申请列表失败:', error)
  } finally {
    loading.value = false
  }
}

const previewImage = (imageUrl) => {
  imagePreviewUrl.value = getImageUrl(imageUrl)
  imagePreviewVisible.value = true
}

const approveApplication = async (application) => {
  try {
    const response = await axios.put(`http://localhost:8082/api/identity/${application.id}`, {
      status: '已批准'
    })
    if (response.data.success) {
      ElMessage.success('批准成功')
      loadAuthenticationList()
    } else {
      ElMessage.error(response.data.message || '批准失败')
    }
  } catch (error) {
    console.error('批准认证申请失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const rejectApplication = (application) => {
  currentApplication.value = application
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const submitReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  try {
    const response = await axios.put(`http://localhost:8082/api/identity/${currentApplication.value.id}`, {
      status: '已驳回',
      respond: rejectReason.value
    })
    if (response.data.success) {
      ElMessage.success('驳回成功')
      rejectDialogVisible.value = false
      loadAuthenticationList()
    } else {
      ElMessage.error(response.data.message || '驳回失败')
    }
  } catch (error) {
    console.error('驳回认证申请失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const viewDetails = (application) => {
  detailApplication.value = application
  detailDialogVisible.value = true
}

const deleteAuthRecord = async (id) => {
  if (!confirm('确定要删除该认证申请记录吗？此操作不可恢复。')) return
  try {
    const response = await axios.delete(`http://localhost:8082/api/identity/${id}`)
    if (response.data.success) {
      ElMessage.success('认证记录已删除')
      loadAuthenticationList()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
  } catch (error) {
    console.error('删除认证记录失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const loadJobs = async () => {
  loadingJobs.value = true
  try {
    const response = await axios.get('/api/job/list')
    if (response.data.success) {
      jobs.value = response.data.data
    } else {
      ElMessage.error(response.data.message || '加载兼职列表失败')
      jobs.value = []
    }
  } catch (error) {
    console.error('加载兼职列表失败:', error)
    ElMessage.error('加载兼职列表失败')
    jobs.value = []
  } finally {
    loadingJobs.value = false
  }
}

const handleDeleteJob = (job) => {
  currentJob.value = job
  currentMerchant.value = { id: job.userId, name: job.userName || '商户' }
  messageContent.value = ''
  messageDialogVisible.value = true
}

const confirmDeleteJob = async () => {
  if (!currentJob.value) return
  if (!messageContent.value.trim()) {
    ElMessage.warning('请输入删除原因')
    return
  }
  const adminId = currentAdmin.value?.id || 0
  try {
    const sendResponse = await axios.post('/api/message/send', {
      senderId: adminId,
      receiverId: currentMerchant.value.id,
      type: 'system',
      content: `您的兼职"${currentJob.value.title}"已被管理员删除，原因：${messageContent.value}`,
      relatedId: currentJob.value.id
    })
    if (sendResponse.data.success) {
      const deleteResponse = await axios.delete(`/api/job/${currentJob.value.id}`, {
        params: { userId: adminId }
      })
      if (deleteResponse.data.success) {
        ElMessage.success('删除成功，留言已发送')
        loadJobs()
        messageDialogVisible.value = false
      } else {
        ElMessage.error(deleteResponse.data.message || '删除失败')
      }
    } else {
      ElMessage.error('发送留言失败，无法删除')
    }
  } catch (error) {
    console.error('删除兼职失败:', error)
    ElMessage.error('删除兼职失败')
  }
}

// AI 头像相关
const aiAvatar = ref('')
const aiAvatarPreview = ref('')

const loadAiAvatar = async () => {
  try {
    const res = await axios.get('/api/matching/ai-avatar')
    if (res.data.success && res.data.data) {
      aiAvatar.value = res.data.data
    }
  } catch {}
}

const beforeImageUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) { ElMessage.error('只能上传图片文件!'); return false }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) { ElMessage.error('图片大小不能超过 10MB!'); return false }
  return true
}

const handleAiAvatarUploadSuccess = (response) => {
  if (response.code === 200) {
    aiAvatarPreview.value = response.data
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleAiAvatarUploadError = () => {
  ElMessage.error('图片上传失败，请检查文件大小或网络连接')
}

const saveAiAvatar = async () => {
  if (!aiAvatarPreview.value) {
    ElMessage.warning('请先上传头像')
    return
  }
  try {
    const res = await axios.post('/api/matching/admin/ai-avatar', {avatar: aiAvatarPreview.value})
    if (res.data.success) {
      ElMessage.success('AI头像已更新')
      aiAvatar.value = aiAvatarPreview.value
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error('设置失败')
  }
}

onMounted(() => {
  const user = SessionManager.getCurrentUserInfo()
  if (user) {
    currentAdmin.value = user
  }
  loadUsers(currentPage.value, pageSize.value)
})

import * as echarts from 'echarts'
import { nextTick } from 'vue'

const adminStatsCards = ref([])
const adminChartConfigs = ref([])
const adminChartRefs = {}
let adminChartInstances = {}

const setAdminChartRef = (el, id) => { if (el) adminChartRefs[id] = el }

const loadAdminStatistics = async () => {
  try {
    // 调用所有管理员接口
    const res1 = await axios.get('/api/statistics/admin/user-growth')
    const res2 = await axios.get('/api/statistics/admin/identity-distribution')
    const res3 = await axios.get('/api/statistics/admin/job-status-stats')
    const res4 = await axios.get('/api/statistics/admin/attendance-overall')
    const userCountRes = await axios.get('/api/user?page=1&size=1')
    const totalUsers = userCountRes.data?.total || 0

    const now = new Date()
    const firstDayOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)
    const userGrowth = res1.data?.data || []
    const monthNewUsers = userGrowth.filter(i => new Date(i.date) >= firstDayOfMonth).reduce((s, i) => s + i.count, 0)

    const jobStats = res3.data?.data || []
    const activeJobs = jobStats.find(i => i.name === '进行中')?.value || 0
    const totalJobs = jobStats.reduce((s, i) => s + i.value, 0)

    // 设置数字卡片
    adminStatsCards.value = [
      { label: '平台总用户', value: totalUsers + '人' },
      { label: '本月新增用户', value: monthNewUsers + '人' },
      { label: '兼职总数', value: totalJobs + '个' },
      { label: '进行中兼职', value: activeJobs + '个' }
    ]

    adminChartConfigs.value = [
      { id: 'adminUserGrowth', title: '近30天新增用户' },
      { id: 'adminIdentityPie', title: '用户身份分布' },
      { id: 'adminJobStatusBar', title: '兼职状态分布' },
      { id: 'adminAttendancePie', title: '全平台考勤分布' }
    ]

    // 等待 DOM 更新后渲染图表
    await nextTick()

    // 销毁旧图表实例避免重复
    Object.values(adminChartInstances).forEach(c => c.dispose())
    adminChartInstances = {}

    // 用户增长折线图
    if (adminChartRefs['adminUserGrowth']) {
      const chart = echarts.init(adminChartRefs['adminUserGrowth'])
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { data: userGrowth.map(i => i.date), axisLabel: { rotate: 45 } },
        yAxis: {},
        series: [{ type: 'line', data: userGrowth.map(i => i.count), smooth: true, itemStyle: { color: '#667eea' } }]
      })
      adminChartInstances['adminUserGrowth'] = chart
    }

    // 身份分布饼图
    if (adminChartRefs['adminIdentityPie']) {
      const chart = echarts.init(adminChartRefs['adminIdentityPie'])
      const data = res2.data?.data || []
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{ type: 'pie', radius: ['40%', '70%'], data: data.map(i => ({ name: i.name, value: i.value })) }]
      })
      adminChartInstances['adminIdentityPie'] = chart
    }

    // 兼职状态柱状图
    if (adminChartRefs['adminJobStatusBar']) {
      const chart = echarts.init(adminChartRefs['adminJobStatusBar'])
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { data: jobStats.map(i => i.name) },
        yAxis: {},
        series: [{ type: 'bar', data: jobStats.map(i => i.value), itemStyle: { color: '#52c41a' } }]
      })
      adminChartInstances['adminJobStatusBar'] = chart
    }

    // 全平台考勤饼图
    if (adminChartRefs['adminAttendancePie']) {
      const chart = echarts.init(adminChartRefs['adminAttendancePie'])
      const data = res4.data?.data || []
      const statusMap = { checked_in: '正常', late: '迟到', absent: '缺勤', early_leave: '早退' }
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{ type: 'pie', radius: ['40%', '70%'], data: data.map(i => ({ name: statusMap[i.name] || i.name, value: i.value })) }]
      })
      adminChartInstances['adminAttendancePie'] = chart
    }

  } catch (e) {
    console.error('管理员统计失败', e)
  }
}

</script>

<template>
  <div class="admin-container">
    <!-- 导航栏 -->
    <header class="admin-header">
      <div class="header-title">后台管理系统</div>
      <div class="header-actions">
        <el-button @click="router.push('/parttime')">兼职主页</el-button>
        <el-button @click="router.push('/ai-match')">🤖 智能匹配</el-button>
        <el-button type="primary" @click="handleLogout">登出</el-button>
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="admin-main">
      <!-- 左侧菜单 -->
      <aside class="admin-sidebar">
        <nav class="sidebar-nav">
          <div class="nav-item" :class="{ active: activeMenu === 'user-management' }"
               @click="handleMenuClick('user-management')">
            <span class="nav-icon">👥</span>
            <span class="nav-text">用户管理</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'job-management' }"
               @click="handleMenuClick('job-management')">
            <span class="nav-icon">💼</span>
            <span class="nav-text">兼职信息管理</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'data-display' }"
               @click="handleMenuClick('data-display')">
            <span class="nav-icon">📊</span>
            <span class="nav-text">数据显示</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'authentication' }"
               @click="handleMenuClick('authentication')">
            <span class="nav-icon">🔍</span>
            <span class="nav-text">认证申请</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'feedback' }" @click="handleMenuClick('feedback')">
            <span class="nav-icon">💬</span>
            <span class="nav-text">问题反馈</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'report-management' }"
               @click="handleMenuClick('report-management')">
            <span class="nav-icon">🚫</span>
            <span class="nav-text">举报管理</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'appeal-management' }"
               @click="handleMenuClick('appeal-management')">
            <span class="nav-icon">🔄</span>
            <span class="nav-text">申诉管理</span>
          </div>
          <div class="nav-item" :class="{ active: activeMenu === 'ai-settings' }"
               @click="handleMenuClick('ai-settings')">
            <span class="nav-icon">🤖</span>
            <span class="nav-text">AI设置</span>
          </div>
        </nav>
      </aside>

      <!-- 右侧内容 -->
      <main class="admin-content">
        <!-- 用户管理 -->
        <div v-if="activeMenu === 'user-management'" class="content-section">
          <div class="section-header">
            <h2 class="section-title">用户管理</h2>
            <el-button type="primary" @click="dialogVisible = true">添加用户</el-button>
          </div>

          <el-table v-loading="loading" :data="users" style="width: 100%" border>
            <el-table-column prop="id" label="用户ID" width="80"/>
            <el-table-column prop="name" label="名字" width="120"/>
            <el-table-column prop="age" label="年龄" width="80"/>
            <el-table-column prop="phone" label="手机号" width="150"/>
            <el-table-column prop="password" label="密码" width="100"/>
            <el-table-column prop="identity" label="认证信息" width="120"/>
            <el-table-column prop="credit" label="信用分数" width="100"/>
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button type="primary" size="small" @click="handleEditUser(scope.row)">修改</el-button>
                <el-button type="danger" size="small" @click="handleDeleteUser(scope.row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-container">
            <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
            />
          </div>

          <!-- 添加用户对话框 -->
          <el-dialog v-model="dialogVisible" title="添加用户" width="500px">
            <el-form :model="addUserForm" label-width="100px">
              <el-form-item label="用户名/手机号">
                <el-input v-model="addUserForm.input" placeholder="请输入用户名或手机号"/>
              </el-form-item>
              <el-form-item label="密码">
                <el-input type="password" v-model="addUserForm.password" placeholder="请输入密码"/>
              </el-form-item>
              <el-form-item label="年龄">
                <el-input type="number" v-model="addUserForm.age" placeholder="请输入年龄"/>
              </el-form-item>
              <el-form-item label="认证信息">
                <el-select v-model="addUserForm.identity" placeholder="请选择认证信息">
                  <el-option label="未认证" value="未认证"/>
                  <el-option label="学生" value="学生"/>
                  <el-option label="商户" value="商户"/>
                </el-select>
              </el-form-item>
              <el-form-item label="信用信息">
                <el-input type="number" v-model="addUserForm.credit" placeholder="请输入信用信息"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleAddUser">确定</el-button>
              </span>
            </template>
          </el-dialog>

          <!-- 编辑用户对话框 -->
          <el-dialog v-model="editDialogVisible" title="编辑用户" width="500px">
            <el-form :model="editUserForm" label-width="100px">
              <el-form-item label="用户名">
                <el-input v-model="editUserForm.name" placeholder="请输入用户名"/>
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="editUserForm.phone" placeholder="请输入手机号"/>
              </el-form-item>
              <el-form-item label="密码">
                <el-input type="password" v-model="editUserForm.password" placeholder="请输入密码（不修改请留空）"/>
              </el-form-item>
              <el-form-item label="年龄">
                <el-input type="number" v-model="editUserForm.age" placeholder="请输入年龄"/>
              </el-form-item>
              <el-form-item label="认证信息">
                <el-select v-model="editUserForm.identity" placeholder="请选择认证信息">
                  <el-option label="未认证" value="未认证"/>
                  <el-option label="学生" value="学生"/>
                  <el-option label="商户" value="商户"/>
                </el-select>
              </el-form-item>
              <el-form-item label="信用信息">
                <el-input type="number" v-model="editUserForm.credit" placeholder="请输入信用信息"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="editDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleUpdateUser">确定</el-button>
              </span>
            </template>
          </el-dialog>
        </div>

        <!-- 兼职信息管理 -->
        <div v-else-if="activeMenu === 'job-management'" class="content-section">
          <div class="section-header">
            <h2 class="section-title">兼职信息管理</h2>
          </div>

          <div class="table-wrapper">
            <el-table v-loading="loadingJobs" :data="jobs" style="width: 100%" border>
              <el-table-column prop="id" label="兼职ID" width="80"/>
              <el-table-column prop="title" label="兼职标题" min-width="150"/>
              <el-table-column prop="content" label="兼职内容" min-width="200">
                <template #default="scope">
                  <div class="job-content">{{ scope.row.content }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="address" label="工作地点" min-width="120"/>
              <el-table-column prop="salary" label="薪资" width="100"/>
              <el-table-column label="工作时间" min-width="180">
                <template #default="scope">
                  <span>{{ formatWorkTime(scope.row.time) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="scope">
                  <el-tag
                      :type="scope.row.status === '已发布' ? 'success' : scope.row.status === '已招满' ? 'info' : 'warning'">
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="userName" label="发布商户" width="120"/>
              <el-table-column prop="createTime" label="发布时间" width="180">
                <template #default="scope">
                  {{ scope.row.createTime ? new Date(scope.row.createTime).toLocaleString() : '未设置' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button type="danger" size="small" @click="handleDeleteJob(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 删除兼职留言对话框 -->
          <el-dialog v-model="messageDialogVisible" title="删除兼职" width="500px">
            <el-form label-width="100px">
              <el-form-item label="商户名称">
                <el-input :value="currentMerchant?.name" readonly/>
              </el-form-item>
              <el-form-item label="兼职标题">
                <el-input :value="currentJob?.title" readonly/>
              </el-form-item>
              <el-form-item label="删除原因" required>
                <el-input type="textarea" v-model="messageContent" placeholder="请输入删除原因" rows="4"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="messageDialogVisible = false">取消</el-button>
                <el-button type="danger" @click="confirmDeleteJob">确认删除</el-button>
              </span>
            </template>
          </el-dialog>
        </div>

        <!-- 数据显示 -->
        <div v-else-if="activeMenu === 'data-display'" class="content-section">
          <h2 class="section-title">平台数据总览</h2>

          <!-- 数字卡片 -->
          <div class="stats-cards" v-if="adminStatsCards.length > 0">
            <div v-for="card in adminStatsCards" :key="card.label" class="stat-card">
              <div class="card-value">{{ card.value }}</div>
              <div class="card-label">{{ card.label }}</div>
            </div>
          </div>

          <!-- 图表区域 -->
          <div class="chart-grid">
            <div v-for="chart in adminChartConfigs" :key="chart.id" class="chart-card">
              <h4>{{ chart.title }}</h4>
              <div :ref="el => setAdminChartRef(el, chart.id)" style="width:100%;height:340px;"></div>
            </div>
          </div>
        </div>
        <!-- 认证申请 -->
        <div v-else-if="activeMenu === 'authentication'" class="content-section">
          <h2 class="section-title">认证申请</h2>
          <div v-loading="loading" class="authentication-list">
            <div v-if="authenticationList.length === 0" class="empty-content">
              <p>暂无认证申请</p>
            </div>
            <div v-else class="table-container">
              <el-table :data="authenticationList" style="width: 100%">
                <el-table-column prop="id" label="申请ID" width="80"/>
                <el-table-column prop="userId" label="用户ID" width="80"/>
                <el-table-column label="用户信息" min-width="240">
                  <template #default="scope">
                    <div v-if="scope.row.user">
                      <div>用户名: {{ scope.row.user.name }}</div>
                      <div v-if="scope.row.user.rname">姓名: {{ scope.row.user.rname }}</div>
                      <div v-if="scope.row.user.idcard">身份证号:{{ scope.row.user.idcard }}</div>
                      <div>电话号:{{ scope.row.user.phone }}</div>
                    </div>
                    <div v-else>加载中...</div>
                  </template>
                </el-table-column>
                <el-table-column label="营业执照" width="100">
                  <template #default="scope">
                    <div v-if="scope.row.imageurl" class="image-preview">
                      <img :src="getImageUrl(scope.row.imageurl)" alt="营业执照"
                           @click="previewImage(scope.row.imageurl)"/>
                    </div>
                    <div v-else>-</div>
                  </template>
                </el-table-column>
                <el-table-column label="商户信息" min-width="150">
                  <template #default="scope">
                    <div v-if="scope.row.bname">
                      <div>商户名称: {{ scope.row.bname }}</div>
                    </div>
                    <div v-else>-</div>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="scope">
                    <el-tag
                        :type="scope.row.status === '已批准' ? 'success' : scope.row.status === '已驳回' ? 'danger' : 'warning'">
                      {{ scope.row.status || '待审核' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="data" label="申请时间" width="180">
                  <template #default="scope">
                    {{ new Date(scope.row.data).toLocaleString() }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="scope">
                    <div v-if="scope.row.status === '待审核'" class="action-buttons">
                      <el-button type="primary" size="small" @click="approveApplication(scope.row)">批准</el-button>
                      <el-button type="danger" size="small" @click="rejectApplication(scope.row)">驳回</el-button>
                    </div>
                    <div v-else-if="scope.row.status === '已驳回'" class="action-buttons">
                      <el-button size="small" @click="viewDetails(scope.row)">查看详情</el-button>
                      <el-button type="danger" size="small" @click="deleteAuthRecord(scope.row.id)">删除</el-button>
                    </div>
                    <div v-else class="action-buttons">
                      <el-button type="danger" size="small" @click="deleteAuthRecord(scope.row.id)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>

        <!-- 问题反馈 -->
        <div v-else-if="activeMenu === 'feedback'" class="content-section">
          <div class="section-header">
            <h2 class="section-title">问题反馈</h2>
          </div>
          <el-table v-loading="loading" :data="feedbacks" style="width: 100%" border>
            <el-table-column prop="id" label="反馈ID" width="80"/>
            <el-table-column prop="userId" label="用户ID" width="100"/>
            <el-table-column prop="content" label="反馈内容" min-width="300">
              <template #default="scope">
                <div class="feedback-content">
                  <div class="content-preview" :class="{ 'expanded': scope.row._expanded }">{{
                      scope.row.content
                    }}
                  </div>
                  <div v-if="scope.row.content && scope.row.content.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._expanded = !scope.row._expanded">
                      {{ scope.row._expanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="image" label="图片" width="100">
              <template #default="scope">
                <div v-if="scope.row.image" class="feedback-thumbnail" @click="previewFeedbackImage(scope.row.image)">
                  <img :src="getImageUrl(scope.row.image)" alt="反馈图片"/>
                </div>
                <el-tag v-else size="small" type="info">无图片</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.status === '未解决'" size="small" type="danger">{{ scope.row.status }}</el-tag>
                <el-tag v-else size="small" type="success">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reply" label="回复" min-width="200">
              <template #default="scope">
                <div class="feedback-reply">
                  <div class="content-preview" :class="{ 'expanded': scope.row._replyExpanded }">
                    {{ scope.row.reply || '暂无回复' }}
                  </div>
                  <div v-if="scope.row.reply && scope.row.reply.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._replyExpanded = !scope.row._replyExpanded">
                      {{ scope.row._replyExpanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="data" label="时间" width="180">
              <template #default="scope">
                {{ scope.row.data ? new Date(scope.row.data).toLocaleString() : '未设置' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button v-if="scope.row.status === '未解决'" type="primary" size="small"
                           @click="handleResolveFeedback(scope.row)">解决
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteFeedback(scope.row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="resolveDialogVisible" title="解决反馈" width="500px">
            <el-form label-width="100px">
              <el-form-item label="反馈内容">
                <el-input type="textarea" :value="currentFeedback?.content" readonly rows="4"/>
              </el-form-item>
              <el-form-item label="回复内容">
                <el-input type="textarea" v-model="feedbackReply" placeholder="请输入回复内容" rows="4"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="resolveDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitResolveFeedback">确定</el-button>
              </span>
            </template>
          </el-dialog>

          <el-dialog v-model="feedbackImagePreviewVisible" title="反馈图片预览" width="600px" align-center>
            <div class="feedback-image-preview-dialog">
              <img :src="feedbackImagePreviewUrl" alt="反馈图片预览"/>
            </div>
          </el-dialog>
        </div>

        <!-- 举报管理 -->
        <div v-else-if="activeMenu === 'report-management'" class="content-section">
          <div class="section-header">
            <h2 class="section-title">举报管理</h2>
          </div>
          <el-table v-loading="loadingReports" :data="reports" style="width: 100%" border>
            <el-table-column prop="id" label="举报ID" width="80"/>
            <el-table-column prop="userId" label="举报人ID" width="100"/>
            <el-table-column prop="content" label="举报内容" min-width="300">
              <template #default="scope">
                <div class="feedback-content">
                  <div class="content-preview" :class="{ 'expanded': scope.row._expanded }">{{
                      scope.row.content
                    }}
                  </div>
                  <div v-if="scope.row.content && scope.row.content.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._expanded = !scope.row._expanded">
                      {{ scope.row._expanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="image" label="图片" width="100">
              <template #default="scope">
                <div v-if="scope.row.image" class="feedback-thumbnail" @click="previewFeedbackImage(scope.row.image)">
                  <img :src="getImageUrl(scope.row.image)" alt="举报图片"/>
                </div>
                <el-tag v-else size="small" type="info">无图片</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.status === '未解决'" size="small" type="danger">{{ scope.row.status }}</el-tag>
                <el-tag v-else size="small" type="success">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reply" label="回复" min-width="200">
              <template #default="scope">
                <div class="feedback-reply">
                  <div class="content-preview" :class="{ 'expanded': scope.row._replyExpanded }">
                    {{ scope.row.reply || '暂无回复' }}
                  </div>
                  <div v-if="scope.row.reply && scope.row.reply.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._replyExpanded = !scope.row._replyExpanded">
                      {{ scope.row._replyExpanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="data" label="时间" width="180">
              <template #default="scope">
                {{ scope.row.data ? new Date(scope.row.data).toLocaleString() : '未设置' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button v-if="scope.row.status === '未解决'" type="primary" size="small"
                           @click="handleResolveReport(scope.row)">处理
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteReport(scope.row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="reportResolveDialogVisible" title="处理举报" width="500px">
            <el-form label-width="100px">
              <el-form-item label="举报内容">
                <el-input type="textarea" :value="currentReport?.content" readonly rows="4"/>
              </el-form-item>
              <el-form-item label="处理意见">
                <el-input type="textarea" v-model="reportReply" placeholder="请输入处理意见" rows="4"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="reportResolveDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitResolveReport">确定</el-button>
              </span>
            </template>
          </el-dialog>
        </div>

        <!-- 申诉管理 -->
        <div v-else-if="activeMenu === 'appeal-management'" class="content-section">
          <div class="section-header">
            <h2 class="section-title">申诉管理</h2>
          </div>
          <el-table v-loading="loadingAppeals" :data="appeals" style="width: 100%" border>
            <el-table-column prop="id" label="申诉ID" width="80"/>
            <el-table-column prop="userId" label="用户ID" width="100"/>
            <el-table-column prop="content" label="申诉内容" min-width="300">
              <template #default="scope">
                <div class="feedback-content">
                  <div class="content-preview" :class="{ 'expanded': scope.row._expanded }">{{
                      scope.row.content
                    }}
                  </div>
                  <div v-if="scope.row.content && scope.row.content.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._expanded = !scope.row._expanded">
                      {{ scope.row._expanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="image" label="图片" width="100">
              <template #default="scope">
                <div v-if="scope.row.image" class="feedback-thumbnail" @click="previewFeedbackImage(scope.row.image)">
                  <img :src="getImageUrl(scope.row.image)" alt="申诉图片"/>
                </div>
                <el-tag v-else size="small" type="info">无图片</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.status === '未解决'" size="small" type="danger">{{ scope.row.status }}</el-tag>
                <el-tag v-else size="small" type="success">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reply" label="回复" min-width="200">
              <template #default="scope">
                <div class="feedback-reply">
                  <div class="content-preview" :class="{ 'expanded': scope.row._replyExpanded }">
                    {{ scope.row.reply || '暂无回复' }}
                  </div>
                  <div v-if="scope.row.reply && scope.row.reply.length > 100" class="expand-toggle">
                    <el-button type="text" size="small" @click="scope.row._replyExpanded = !scope.row._replyExpanded">
                      {{ scope.row._replyExpanded ? '收起' : '展开' }}
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="data" label="时间" width="180">
              <template #default="scope">
                {{ scope.row.data ? new Date(scope.row.data).toLocaleString() : '未设置' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250">
              <template #default="scope">
                <el-button v-if="scope.row.status === '未解决'" type="primary" size="small"
                           @click="handleResolveAppeal(scope.row)">处理
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteAppeal(scope.row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="appealResolveDialogVisible" title="处理申诉" width="500px">
            <el-form label-width="100px">
              <el-form-item label="用户ID">
                <el-input :value="currentAppeal?.userId" readonly/>
              </el-form-item>
              <el-form-item label="申诉内容">
                <el-input type="textarea" :value="currentAppeal?.content" readonly rows="3"/>
              </el-form-item>
              <el-form-item label="当前信用分">
                <el-input :value="currentAppealCredit !== null ? currentAppealCredit : '加载中...'" readonly>
                  <template #suffix>
                    <span v-if="currentAppealCredit !== null" style="color:#909399;">分</span>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="信用分设为">
                <el-input-number v-model="appealCreditScore" :min="0" :max="100" />
                <span style="margin-left:8px;color:#909399;font-size:12px;">（0-100）</span>
              </el-form-item>
              <el-form-item label="处理意见">
                <el-input type="textarea" v-model="appealReply" placeholder="请输入处理意见" rows="3"/>
              </el-form-item>
            </el-form>
            <template #footer>
              <span class="dialog-footer">
                <el-button @click="appealResolveDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitResolveAppeal">确定处理</el-button>
              </span>
            </template>
          </el-dialog>

        </div>

        <!-- AI 设置（修正位置：放在 admin-content 内部） -->
        <div v-if="activeMenu === 'ai-settings'" class="content-section">
          <h2 class="section-title">AI 助手头像设置</h2>
          <el-form label-width="100px">
            <el-form-item label="当前头像">
              <img v-if="aiAvatar" :src="getImageUrl(aiAvatar)"
                   style="width:80px;height:80px;border-radius:50%;object-fit:cover"/>
              <span v-else>未设置（默认显示 🤖）</span>
            </el-form-item>
            <el-form-item label="新头像">
              <el-upload
                  class="avatar-uploader"
                  action="/api/upload"
                  :show-file-list="false"
                  :on-success="handleAiAvatarUploadSuccess"
                  :on-error="handleAiAvatarUploadError"
                  :before-upload="beforeImageUpload"
              >
                <img v-if="aiAvatarPreview" :src="getImageUrl(aiAvatarPreview)" class="avatar-preview"
                     style="width:80px;height:80px;border-radius:50%;object-fit:cover"/>
                <el-icon v-else class="avatar-uploader-icon">
                  <Plus/>
                </el-icon>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAiAvatar">保存设置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </main>
    </div>

    <!-- 认证申请图片预览对话框 -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="600px" align-center>
      <div class="feedback-image-preview-dialog">
        <img :src="imagePreviewUrl" alt="图片预览"/>
      </div>
    </el-dialog>

    <!-- 驳回认证申请对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回认证申请" width="500px">
      <el-form label-width="100px">
        <el-form-item label="申请ID">
          <el-input :value="currentApplication?.id" readonly/>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input :value="currentApplication?.userId" readonly/>
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input type="textarea" v-model="rejectReason" placeholder="请输入驳回原因" rows="4"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="rejectDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitReject">提交驳回</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="申请详情" width="500px">
      <el-form label-width="100px" v-if="detailApplication">
        <el-form-item label="申请ID">
          <el-input :value="detailApplication.id" readonly/>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input :value="detailApplication.userId" readonly/>
        </el-form-item>
        <el-form-item label="申请人" v-if="detailApplication.user">
          <el-input :value="detailApplication.user.name" readonly/>
        </el-form-item>
        <el-form-item label="实名" v-if="detailApplication.user && detailApplication.user.rname">
          <el-input :value="detailApplication.user.rname" readonly/>
        </el-form-item>
        <el-form-item label="身份证号" v-if="detailApplication.user && detailApplication.user.idcard">
          <el-input :value="detailApplication.user.idcard" readonly/>
        </el-form-item>
        <el-form-item label="联系电话" v-if="detailApplication.user">
          <el-input :value="detailApplication.user.phone" readonly/>
        </el-form-item>
        <el-form-item label="商户名称">
          <el-input :value="detailApplication.bname" readonly/>
        </el-form-item>
        <el-form-item label="状态">
          <el-tag
              :type="detailApplication.status === '已批准' ? 'success' : detailApplication.status === '已驳回' ? 'danger' : 'warning'">
            {{ detailApplication.status }}
          </el-tag>
        </el-form-item>
        <el-form-item label="申请时间">
          <el-input :value="new Date(detailApplication.data).toLocaleString()" readonly/>
        </el-form-item>
        <el-form-item label="驳回原因" v-if="detailApplication.respond">
          <el-input type="textarea" :value="detailApplication.respond" readonly rows="4"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fa;
}

.admin-header {
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.header-title {
  font-size: 24px;
  font-weight: 700;
  color: white;
  text-shadow: 1px 1px 3px rgba(0, 0, 0, 0.3);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.admin-sidebar {
  width: 220px;
  background: white;
  border-right: 1px solid #eaeaea;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
}

.sidebar-nav {
  padding: 20px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 14px 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-left: 3px solid transparent;
}

.nav-item:hover {
  background-color: #f0f2f5;
}

.nav-item.active {
  background-color: #f0f2f5;
  border-left-color: #667eea;
  color: #667eea;
}

.nav-icon {
  font-size: 18px;
  margin-right: 12px;
  width: 24px;
  text-align: center;
}

.nav-text {
  font-size: 15px;
  font-weight: 500;
}

.admin-content {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
}

.content-section {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border-bottom: 1px solid #eaeaea;
  padding-bottom: 12px;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin-bottom: 0;
}

.empty-content {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #999;
  font-size: 16px;
}

/* 表格横向滚动条适配 */
.table-wrapper {
  overflow-x: auto;
  width: 100%;
}

@media (max-width: 768px) {
  .admin-sidebar {
    width: 60px;
  }

  .nav-text {
    display: none;
  }

  .nav-icon {
    margin-right: 0;
  }

  .admin-content {
    padding: 20px;
  }

  .header-title {
    font-size: 20px;
  }
}

@media (max-width: 480px) {
  .admin-header {
    padding: 0 20px;
  }

  .content-section {
    padding: 16px;
  }

  .section-title {
    font-size: 18px;
  }
}

.feedback-content {
  max-width: 400px;
}

.feedback-content .content-preview {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.5;
  word-break: break-word;
}

.feedback-content .content-preview.expanded {
  -webkit-line-clamp: unset;
  display: block;
  white-space: pre-wrap;
}

.feedback-content .expand-toggle {
  margin-top: 4px;
  text-align: right;
}

.feedback-content .expand-toggle .el-button {
  padding: 0;
  font-size: 12px;
  color: #409eff;
}

.feedback-reply {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: #666;
  font-size: 14px;
}

.feedback-thumbnail {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #eaeaea;
  transition: all 0.3s ease;
}

.feedback-thumbnail:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.feedback-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feedback-image-preview-dialog {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.feedback-image-preview-dialog img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.image-preview {
  width: 80px;
  height: 60px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #eaeaea;
  transition: all 0.3s ease;
}

.image-preview:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.authentication-list {
  margin-top: 20px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.table-container {
  overflow-x: auto;
}
.stats-cards { display: flex; gap: 15px; margin-bottom: 30px; flex-wrap: wrap; }
.stat-card { background: white; border-radius: 8px; padding: 20px; flex: 1; min-width: 140px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); text-align: center; }
.stat-card .card-value { font-size: 28px; font-weight: bold; color: #333; }
.stat-card .card-label { font-size: 14px; color: #888; margin-top: 8px; }
.chart-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 20px; }
.chart-card { background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); }
.chart-card h4 { margin: 0 0 12px; color: #333; }
</style>