<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import GeneralNav from '../nav/GeneralNav.vue'
import StudentWeeklySchedule from '../components/StudentWeeklySchedule.vue'
import MerchantJobSchedule from '../components/MerchantJobSchedule.vue'
import SessionManager from '../utils/SessionManager.js'
import axios from 'axios'
import * as echarts from 'echarts'
const router = useRouter()
const route = useRoute()

// 激活的菜单项
const activeMenu = ref('profile')
// 刷新时间表组件的key
const scheduleKey = ref(0)
// 用户信息
const userInfo = ref({
  id: '',
  name: '',
  phone: '',
  identity: '',
  credit: 0,
  image: '',
  timepreference: ''
})

// 头像上传相关
const avatarUploadRef = ref(null)
const avatarPreviewVisible = ref(false)
const imagePreviewUrl = ref('')

// 修改个人信息对话框
const editDialogVisible = ref(false)
const editForm = ref({
  name: '',
  phone: '',
  age: 0,
  image: ''
})

// 单项修改对话框
const singleEditDialogVisible = ref(false)
const singleEditType = ref('')
const singleEditValue = ref('')
const singleEditOriginal = ref('')

// 加载用户信息
const loadUserInfo = () => {
  const currentUser = SessionManager.getCurrentUserInfo()
  if (currentUser) {
    userInfo.value = currentUser
  } else {
    const storedUserInfo = localStorage.getItem('userInfo')
    if (storedUserInfo) {
      try {
        userInfo.value = JSON.parse(storedUserInfo)
      } catch (e) {
        console.error('解析用户信息失败:', e)
      }
    }
  }
}

// 格式化手机号（部分隐藏）
const formatPhone = (phone) => {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

// 格式化手机号（只显示最后四位）
const formatPhoneLastFour = (phone) => {
  if (!phone) return ''
  return phone.replace(/(\d{7})(\d{4})/, '*******$2')
}

// 格式化实名（隐藏前两个字）
const formatRealName = (rname) => {
  if (!rname) return '未实名'
  if (rname.length <= 2) return '*'.repeat(rname.length)
  return '**' + rname.substring(2)
}

// 切换菜单项
const switchMenu = (menu) => {
  activeMenu.value = menu
  if (menu === 'statistics') {
    loadStatistics()
  }
}

// 获取图片完整URL
const getImageUrl = (imagePath) => {
  if (!imagePath) return ''
  if (imagePath.startsWith('http')) return imagePath
  if (imagePath.startsWith('/uploads/')) return `http://localhost:8082${imagePath}`
  return `http://localhost:8082/uploads${imagePath}`
}

// 处理头像上传
const handleAvatarUpload = async (file) => {
  const isImage = file.raw.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  const isLt2M = file.raw.size / 1024 / 1024 < 10
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 10MB!')
    return false
  }
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    const response = await axios.post(`http://localhost:8082/api/user/${userInfo.value.id}/avatar`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.data.success) {
      ElMessage.success('头像上传成功')
      userInfo.value.image = response.data.data
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      SessionManager.updateUser({ image: response.data.data })
    } else {
      ElMessage.error(response.data.message || '头像上传失败')
    }
  } catch (error) {
    console.error('上传头像失败:', error)
    ElMessage.error('头像上传失败，请稍后重试')
  }
  return false
}

// 预览头像
const previewAvatar = () => {
  if (userInfo.value.image) {
    imagePreviewUrl.value = getImageUrl(userInfo.value.image)
    avatarPreviewVisible.value = true
  }
}

// 预览编辑对话框中的头像
const previewEditAvatar = () => {
  if (editForm.value.image) {
    imagePreviewUrl.value = getImageUrl(editForm.value.image)
    avatarPreviewVisible.value = true
  }
}

// 打开单项修改对话框
const openSingleEdit = (type, value) => {
  singleEditType.value = type
  singleEditValue.value = value
  singleEditOriginal.value = value
  singleEditDialogVisible.value = true
}

// 保存单项修改
const saveSingleEdit = async () => {
  if (singleEditType.value === 'name' && !singleEditValue.value.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  if (singleEditType.value === 'phone') {
    if (!singleEditValue.value.trim()) {
      ElMessage.warning('手机号不能为空')
      return
    }
    if (!/^1[3-9]\d{9}$/.test(singleEditValue.value)) {
      ElMessage.warning('请输入正确的手机号')
      return
    }
  }
  if (singleEditType.value === 'age') {
    const age = parseInt(singleEditValue.value)
    if (isNaN(age) || age < 0 || age > 150) {
      ElMessage.warning('请输入正确的年龄')
      return
    }
  }
  try {
    const updateData = {}
    if (singleEditType.value === 'age') {
      updateData[singleEditType.value] = parseInt(singleEditValue.value)
    } else {
      updateData[singleEditType.value] = singleEditValue.value
    }
    const response = await axios.post(`http://localhost:8082/api/user/${userInfo.value.id}`, updateData)
    if (response.data && response.data.success) {
      ElMessage.success('修改成功')
      if (singleEditType.value === 'age') {
        userInfo.value[singleEditType.value] = parseInt(singleEditValue.value)
      } else {
        userInfo.value[singleEditType.value] = singleEditValue.value
      }
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      SessionManager.updateUser({ [singleEditType.value]: singleEditValue.value })
      singleEditDialogVisible.value = false
    } else {
      ElMessage.error('修改失败')
    }
  } catch (error) {
    console.error('修改失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 打开修改个人信息对话框
const editProfile = () => {
  editForm.value = {
    name: userInfo.value.name,
    phone: userInfo.value.phone,
    age: userInfo.value.age || 0,
    image: userInfo.value.image
  }
  editDialogVisible.value = true
}

// 保存修改个人信息
const saveEditProfile = async () => {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  if (editForm.value.phone && !/^1[3-9]\d{9}$/.test(editForm.value.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  try {
    const response = await axios.post(`http://localhost:8082/api/user/${userInfo.value.id}`, editForm.value)
    if (response.data && response.data.success) {
      ElMessage.success('修改成功')
      userInfo.value.name = editForm.value.name
      userInfo.value.phone = editForm.value.phone
      userInfo.value.age = editForm.value.age
      userInfo.value.image = editForm.value.image
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      SessionManager.updateUser({
        name: editForm.value.name,
        phone: editForm.value.phone,
        age: editForm.value.age,
        image: editForm.value.image
      })
      editDialogVisible.value = false
    } else {
      ElMessage.error('修改失败')
    }
  } catch (error) {
    console.error('修改失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 处理修改对话框中的头像上传
const handleEditAvatarUpload = async (file) => {
  const isImage = file.raw.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  const isLt2M = file.raw.size / 1024 / 1024 < 10
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 10MB!')
    return false
  }
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    const response = await axios.post(`http://localhost:8082/api/user/${userInfo.value.id}/avatar`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.data.success) {
      ElMessage.success('头像上传成功')
      editForm.value.image = response.data.data
    } else {
      ElMessage.error(response.data.message || '头像上传失败')
    }
  } catch (error) {
    console.error('上传头像失败:', error)
    ElMessage.error('头像上传失败，请稍后重试')
  }
  return false
}

// 修改密码对话框
const changePasswordDialogVisible = ref(false)
const changePasswordForm = ref({
  oldPassword: '',
  phone: '',
  newPassword: '',
  confirmPassword: ''
})

// 打开修改密码对话框
const changePassword = () => {
  changePasswordForm.value = {
    oldPassword: '',
    phone: '',
    newPassword: '',
    confirmPassword: ''
  }
  changePasswordDialogVisible.value = true
}

// 保存修改密码
const saveChangePassword = async () => {
  if (!changePasswordForm.value.oldPassword) {
    ElMessage.warning('请输入旧密码')
    return
  }
  if (!changePasswordForm.value.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(changePasswordForm.value.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!changePasswordForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (changePasswordForm.value.newPassword.length < 6) {
    ElMessage.warning('密码长度不能少于6位')
    return
  }
  if (changePasswordForm.value.newPassword !== changePasswordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (changePasswordForm.value.phone !== userInfo.value.phone) {
    ElMessage.warning('请输入绑定的手机号')
    return
  }
  try {
    const response = await axios.post(`http://localhost:8082/api/user/${userInfo.value.id}/change-password`, {
      oldPassword: changePasswordForm.value.oldPassword,
      newPassword: changePasswordForm.value.newPassword
    })
    if (response.data.success) {
      ElMessage.success('密码修改成功')
      changePasswordDialogVisible.value = false
    } else {
      ElMessage.error(response.data.message || '密码修改失败')
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 认证相关
const authDialogVisible = ref(false)
const authHistoryDialogVisible = ref(false)
const studentAuthDialogVisible = ref(false)
const studentAuthForm = ref({
  name: '',
  number: ''
})
const authForm = ref({
  image: '',
  businessName: '',
  contactPerson: '',
  contactPhone: '',
  idcard: ''
})
const authHistory = ref([])
const loading = ref(false)

// 打开学生认证对话框
const studentAuth = () => {
  studentAuthForm.value = { name: '', number: '' }
  studentAuthDialogVisible.value = true
}

// 提交学生认证
const submitStudentAuth = async () => {
  if (!studentAuthForm.value.name) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (!studentAuthForm.value.number) {
    ElMessage.warning('请输入学号')
    return
  }
  try {
    loading.value = true
    const response = await axios.post('http://localhost:8082/api/realname-auth/student', studentAuthForm.value, {
      params: { userId: userInfo.value.id }
    })
    if (response.data.success) {
      ElMessage.success('学生认证成功')
      ElMessage.info('请刷新页面或重新登录以使身份变更生效')
      userInfo.value.rname = studentAuthForm.value.name
      userInfo.value.number = studentAuthForm.value.number
      userInfo.value.identity = '学生'
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      SessionManager.updateUser({
        identity: '学生',
        rname: studentAuthForm.value.name,
        number: studentAuthForm.value.number
      })
      studentAuthDialogVisible.value = false
    } else {
      ElMessage.error(response.data.message || '认证失败')
    }
  } catch (error) {
    console.error('学生认证失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 打开认证对话框
const userAuthentication = () => {
  authForm.value = {
    image: '',
    businessName: '',
    contactPerson: '',
    contactPhone: '',
    idcard: ''
  }
  authDialogVisible.value = true
}

// 处理认证图片上传
const handleAuthImageUpload = async (file) => {
  const isImage = file.raw.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  const isLt2M = file.raw.size / 1024 / 1024 < 10
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 10MB!')
    return false
  }
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    const response = await axios.post('http://localhost:8082/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.data.success) {
      ElMessage.success('图片上传成功')
      authForm.value.image = response.data.data
    } else {
      ElMessage.error(response.data.message || '图片上传失败')
    }
  } catch (error) {
    console.error('上传图片失败:', error)
    ElMessage.error('图片上传失败，请稍后重试')
  }
  return false
}

// 提交认证申请
const submitAuthApplication = async () => {
  if (!authForm.value.image) {
    ElMessage.warning('请上传营业执照图片')
    return
  }
  if (!authForm.value.businessName) {
    ElMessage.warning('请输入商户名称')
    return
  }
  if (!authForm.value.contactPerson) {
    ElMessage.warning('请输入联系人姓名')
    return
  }
  if (!authForm.value.contactPhone || !/^1[3-9]\d{9}$/.test(authForm.value.contactPhone)) {
    ElMessage.warning('请输入正确的联系电话')
    return
  }
  if (!authForm.value.idcard) {
    ElMessage.warning('请输入身份证号')
    return
  }
  if (!/^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/.test(authForm.value.idcard)) {
    ElMessage.warning('请输入正确的身份证号')
    return
  }
  try {
    loading.value = true
    await axios.post('http://localhost:8082/api/realname-auth/merchant', {
      name: authForm.value.contactPerson,
      idcard: authForm.value.idcard
    }, {
      params: { userId: userInfo.value.id }
    })
    const response = await axios.post('http://localhost:8082/api/identity', {
      userId: userInfo.value.id,
      imageurl: authForm.value.image,
      bname: authForm.value.businessName
    })
    if (response.data.success) {
      ElMessage.success('认证申请提交成功，请等待管理员审核')
      userInfo.value.rname = authForm.value.contactPerson
      userInfo.value.idcard = authForm.value.idcard
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      authDialogVisible.value = false
    } else {
      ElMessage.error(response.data.message || '认证申请提交失败')
    }
  } catch (error) {
    console.error('提交认证申请失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 查看认证申请历史
const viewAuthHistory = async () => {
  try {
    loading.value = true
    const response = await axios.get(`http://localhost:8082/api/identity/user/${userInfo.value.id}`)
    if (response.data.success) {
      authHistory.value = response.data.data
      authHistoryDialogVisible.value = true
    } else {
      ElMessage.error(response.data.message || '获取认证历史失败')
    }
  } catch (error) {
    console.error('获取认证历史失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// ==================== 信用分相关 ====================
const creditDialogVisible = ref(false)
const creditRecords = ref([])
const loadingCreditRecords = ref(false)
const creditPage = ref(1)
const creditPageSize = 5

const pagedCreditRecords = computed(() => {
  const start = (creditPage.value - 1) * creditPageSize
  return creditRecords.value.slice(start, start + creditPageSize)
})

// 获取行为描述（兼容驼峰和下划线命名）
const getCreditActionText = (row) => {
  const action = row.actionType || row.action_type || ''
  const map = {
    late: '迟到',
    absent: '缺勤',
    early_leave: '提前下班',
    early_leave_complete: '提前下班完成',
    complete_job: '完成工作',
    appeal: '申诉',
    praise: '好评',
    criticize: '差评',
    punctual: '准时',
    evaluation_revoke: '评论修改',
    other: '其他'
  }
  return map[action] || action || '未知'
}

// 根据分数变化决定标签颜色
const getCreditTagType = (row) => {
  const score = row.changeScore || row.change_score || 0
  if (score > 0) return 'success'
  if (score < 0) return 'danger'
  return 'info'
}

const getCreditChangeClass = (score) => {
  if (!score && score !== 0) return 'credit-neutral'
  if (score > 0) return 'credit-increase'
  if (score < 0) return 'credit-decrease'
  return 'credit-neutral'
}

const formatCreditDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString()
}

const viewCredit = async () => {
  try {
    loadingCreditRecords.value = true
    const userId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('/api/credit/records', { params: { userId } })
    if (res.data.success) {
      creditRecords.value = res.data.data
      creditPage.value = 1
      creditDialogVisible.value = true
    } else {
      ElMessage.error('获取信用记录失败')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取信用记录失败')
  } finally {
    loadingCreditRecords.value = false
  }
}

// 星期和时间段定义
const weekDays = [
  { id: 1, name: '周一' },
  { id: 2, name: '周二' },
  { id: 3, name: '周三' },
  { id: 4, name: '周四' },
  { id: 5, name: '周五' },
  { id: 6, name: '周六' },
  { id: 7, name: '周日' }
]

const timePeriods = [
  { id: 1, name: '上午' },
  { id: 2, name: '下午' },
  { id: 3, name: '晚上' }
]

// 格式化时间偏好显示
const formatTimePreference = (timepreference) => {
  if (!timepreference) return '未设置'
  try {
    const arr = JSON.parse(timepreference)
    const dayMap = {1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日'}
    const periodMap = {1: '上午', 2: '下午', 3: '晚上'}
    return arr.map(s => `${dayMap[s.day]} ${periodMap[s.period]}`).join('\n')
  } catch {
    return timepreference
  }
}

// 时间偏好设置
const timePreferenceDialogVisible = ref(false)
const timeSlots = ref([])

const isTimeSlotSelected = (dayId, periodId) => {
  if (!Array.isArray(timeSlots.value)) return false
  return timeSlots.value.some(slot => slot.day === dayId && slot.period === periodId)
}

const toggleTimeSlot = (dayId, periodId) => {
  if (!Array.isArray(timeSlots.value)) timeSlots.value = []
  const index = timeSlots.value.findIndex(slot => slot.day === dayId && slot.period === periodId)
  if (index > -1) timeSlots.value.splice(index, 1)
  else timeSlots.value.push({day: dayId, period: periodId})
}

const openTimePreferenceDialog = () => {
  timeSlots.value = []
  if (userInfo.value.timepreference) {
    try {
      const currentPreference = JSON.parse(userInfo.value.timepreference)
      if (Array.isArray(currentPreference)) timeSlots.value = currentPreference
    } catch (e) {
      console.error('解析时间偏好失败:', e)
    }
  }
  timePreferenceDialogVisible.value = true
}

const saveTimePreference = async () => {
  try {
    // 按星期和时段排序：周一上午、下午、晚上，周二上午...
    timeSlots.value.sort((a, b) => {
      if (a.day !== b.day) return a.day - b.day
      // period 可能是数字 1/2/3 或字符串 'morning'/'afternoon'/'evening'
      const order = { 'morning': 1, 'afternoon': 2, 'evening': 3, 1: 1, 2: 2, 3: 3 }
      return (order[a.period] || 0) - (order[b.period] || 0)
    })

    const timepreference = JSON.stringify(timeSlots.value)
    const response = await axios.post('http://localhost:8082/api/user/time-preference', {
      userId: userInfo.value.id,
      timepreference: timepreference
    })
    if (response.data.success) {
      ElMessage.success('时间偏好设置成功')
      ElMessage.warning('请确认你的时间设置！这非常重要，会影响到你的推荐机制')
      userInfo.value.timepreference = timepreference
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      SessionManager.updateUser({ timepreference: timepreference })
      timePreferenceDialogVisible.value = false
      scheduleKey.value++
    } else {
      ElMessage.error(response.data.message || '时间偏好设置失败')
    }
  } catch (error) {
    console.error('设置时间偏好失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const checkLoginStatus = () => {
  if (!SessionManager.isLoggedIn()) {
    ElMessage.warning('请先登录')
    router.push('/userlogin')
  }
}

onMounted(() => {
  checkLoginStatus()
  loadUserInfo()
  const query = route.query
  if (query.activeTab) activeMenu.value = query.activeTab
})




// 数据概述状态
const statsCards = ref([])
const chartConfigs = ref([])
const chartRefs = {}        // 非响应式，用于存储图表 DOM 引用
let chartInstances = {}     // 存储已初始化的 ECharts 实例

// 设置图表引用
const setChartRef = (el, id) => {
  if (el) chartRefs[id] = el
}

// 加载统计数据（根据身份调用不同接口）
const loadStatistics = async () => {
  const identity = userInfo.value.identity
  const uid = userInfo.value.id
  if (!identity || !uid) return

  // 重置
  statsCards.value = []
  chartConfigs.value = []
  Object.values(chartInstances).forEach(c => c.dispose())
  chartInstances = {}

  if (identity === '学生') {
    await loadStudentStatistics(uid)
  } else if (identity === '商户') {
    await loadMerchantStatistics(uid)
  }
}

// 学生统计
const loadStudentStatistics = async (studentId) => {
  try {
    const res1 = await axios.get('/api/statistics/student/credit-trend', { params: { studentId } })
    const res2 = await axios.get('/api/statistics/student/work-hours', { params: { studentId } })
    const res3 = await axios.get('/api/statistics/student/job-type-distribution', { params: { studentId } })

    const creditTrend = res1.data?.data || []
    const workHours = res2.data?.data || []
    const lastCredit = creditTrend.length > 0 ? creditTrend[creditTrend.length - 1].credit : userInfo.value.credit
    const firstCredit = creditTrend.length > 0 ? creditTrend[0].credit : lastCredit
    const creditChange = lastCredit - firstCredit

    // 本周时长和任务数
    const now = new Date()
    const dayOfWeek = now.getDay() || 7
    const monday = new Date(now)
    monday.setDate(now.getDate() - dayOfWeek + 1)
    monday.setHours(0,0,0,0)
    const sunday = new Date(monday)
    sunday.setDate(monday.getDate() + 6)
    sunday.setHours(23,59,59,999)

    const thisWeekMinutes = workHours
        .filter(i => { const d = new Date(i.date); return d >= monday && d <= sunday })
        .reduce((sum, i) => sum + (i.minutes || 0), 0)
    const thisWeekTasks = workHours
        .filter(i => { const d = new Date(i.date); return d >= monday && d <= sunday }).length

    statsCards.value = [
      { label: '本周工作时长', value: Math.floor(thisWeekMinutes / 60) + 'h ' + (thisWeekMinutes % 60) + 'min' },
      { label: '本周任务数', value: thisWeekTasks + ' 个' },
      { label: '当前信用分', value: lastCredit + '分' },
      { label: '信用变化', value: (creditChange >= 0 ? '+' : '') + creditChange + '分' }
    ]

    chartConfigs.value = [
      { id: 'studentWorkBar', title: '近30天工作时长' },
      { id: 'studentCreditLine', title: '信用分变化趋势' },
      { id: 'studentJobTypePie', title: '兼职类型分布' }
    ]

    await nextTick()
    // 工作时长柱状图
    if (chartRefs['studentWorkBar']) {
      const chart = echarts.init(chartRefs['studentWorkBar'])
      chart.setOption({
        xAxis: { data: workHours.map(i => i.date), axisLabel: { rotate: 45 } },
        yAxis: { name: '分钟' },
        series: [{ type: 'bar', data: workHours.map(i => i.minutes), itemStyle: { color: '#667eea' } }]
      })
      chartInstances['studentWorkBar'] = chart
    }
    // 信用分折线图
    if (chartRefs['studentCreditLine']) {
      const chart = echarts.init(chartRefs['studentCreditLine'])
      chart.setOption({
        xAxis: { data: creditTrend.map(i => i.date), axisLabel: { rotate: 45 } },
        yAxis: { name: '信用分' },
        series: [{ type: 'line', data: creditTrend.map(i => i.credit), smooth: true, itemStyle: { color: '#52c41a' } }]
      })
      chartInstances['studentCreditLine'] = chart
    }
    // 兼职类型饼图
    if (chartRefs['studentJobTypePie']) {
      const chart = echarts.init(chartRefs['studentJobTypePie'])
      const data = res3.data?.data || []
      chart.setOption({
        series: [{ type: 'pie', data: data.map(i => ({ name: i.name === 'daily' ? '日结' : i.name === 'weekly' ? '周结' : i.name, value: i.value })) }]
      })
      chartInstances['studentJobTypePie'] = chart
    }
  } catch (e) { console.error('学生统计失败', e) }
}

// 商户统计
const loadMerchantStatistics = async (merchantId) => {
  try {
    const id = parseInt(merchantId);  // 确保是整数
    const res1 = await axios.get('/api/statistics/merchant/recruitment-progress', {params: {merchantId: id}})
    const res2 = await axios.get('/api/statistics/merchant/attendance-distribution', {params: {merchantId: id}})
    const res3 = await axios.get('/api/statistics/merchant/daily-checkin-trend', {params: {merchantId: id}})
    const res4 = await axios.get('/api/statistics/merchant/top-workers', {params: {merchantId: id, limit: 5}})
    // 评价摘要
    let goodEval = 0, badEval = 0
    try {
      const evalRes = await axios.get('/api/statistics/merchant/evaluation-summary', {params: {merchantId: id}})
      goodEval = evalRes.data?.good || 0
      badEval = evalRes.data?.bad || 0
    } catch {}
    const recruitment = res1.data?.data || []
    const hiredTotal = recruitment.reduce((s, i) => s + (i.hired || 0), 0)
    let creditChange = 0
    try {
      const creditRes = await axios.get('/api/statistics/merchant/credit-trend', {params: {merchantId: id}})
      const trend = creditRes.data?.data || []
      if (trend.length > 1) creditChange = trend[trend.length - 1].credit - trend[0].credit
    } catch {}
    statsCards.value = [
      {label: '本周招聘学生', value: hiredTotal + '人'},
      {label: '本周好评', value: goodEval + '个'},
      {label: '本周差评', value: badEval + '个'},
      {label: '信用变化', value: (creditChange >= 0 ? '+' : '') + creditChange + '分'}
    ]

    chartConfigs.value = [
      {id: 'recruitBar', title: '各兼职招聘进度'},
      {id: 'attendancePie', title: '考勤状态分布'},
      {id: 'checkinLine', title: '近7天签到趋势'}
    ]

    await nextTick()
    if (chartRefs['recruitBar']) {
      const chart = echarts.init(chartRefs['recruitBar'])
      const titles = recruitment.map(i => i.title || '兼职#' + i.id)
      chart.setOption({
        legend: {},
        tooltip: {},
        xAxis: {data: titles, axisLabel: {rotate: 30}},
        yAxis: {},
        series: [
          {name: '需招', type: 'bar', data: recruitment.map(i => i.needed), itemStyle: {color: '#91cc75'}},
          {name: '已招', type: 'bar', data: recruitment.map(i => i.hired), itemStyle: {color: '#5470c6'}}
        ]
      })
      chartInstances['recruitBar'] = chart
    }
    if (chartRefs['attendancePie']) {
      const chart = echarts.init(chartRefs['attendancePie'])
      const data = res2.data?.data || []
      const statusMap = {checked_in: '正常', late: '迟到', absent: '缺勤', early_leave: '早退'}
      chart.setOption({
        series: [{type: 'pie', data: data.map(i => ({name: statusMap[i.name] || i.name, value: i.value}))}]
      })
      chartInstances['attendancePie'] = chart
    }
    if (chartRefs['checkinLine']) {
      const chart = echarts.init(chartRefs['checkinLine'])
      const trend = res3.data?.data || []
      chart.setOption({
        xAxis: {data: trend.map(i => i.date)},
        yAxis: {name: '人数'},
        series: [{type: 'line', data: trend.map(i => i.count), itemStyle: {color: '#fa8c16'}}]
      })
      chartInstances['checkinLine'] = chart
    }
  } catch (e) {
    console.error('商户统计失败', e)
    statsCards.value = [];
    chartConfigs.value = [];
  }


}

const goToMyAcceptedJobs = () => {
  router.push('/my-accepted-jobs')
}

</script>

<template>
  <div class="profile-page">
    <GeneralNav/>

    <div class="profile-content">
      <div class="back-home-btn">
        <el-button @click="router.push('/parttime')" type="primary" plain>
          <span class="btn-icon">🏠</span>
          <span>返回主页</span>
        </el-button>
      </div>

      <div class="content-area">
        <div class="profile-sidebar">
          <h3 class="sidebar-title">账户设置</h3>
          <div class="sidebar-menu">
            <div class="menu-item" :class="{ active: activeMenu === 'profile' }" @click="switchMenu('profile')">个人资料</div>
            <div class="menu-item" :class="{ active: activeMenu === 'schedule' }" @click="switchMenu('schedule')">一周安排</div>
            <div class="menu-item" :class="{ active: activeMenu === 'security' }" @click="switchMenu('security')">安全中心</div>
            <div class="menu-item" :class="{ active: activeMenu === 'statistics' }" @click="switchMenu('statistics')">数据概述</div>
          </div>
        </div>

        <div class="profile-main">
          <!-- 个人资料 -->
          <div v-if="activeMenu === 'profile'" class="profile-section">
            <h2 class="section-title">个人资料</h2>
            <div class="profile-info">
              <div class="info-item">
                <label class="info-label">头像</label>
                <div class="avatar-container">
                  <div class="avatar" :class="{ 'has-image': userInfo.image }" @click="previewAvatar">
                    <img v-if="userInfo.image" :src="getImageUrl(userInfo.image)" alt="头像"/>
                    <span v-else>{{ (userInfo.name && userInfo.name.charAt(0)) || 'U' }}</span>
                  </div>
                  <el-upload ref="avatarUploadRef" action="#" :auto-upload="false" :show-file-list="false" :on-change="handleAvatarUpload" accept="image/*">
                    <span class="edit-btn">修改</span>
                  </el-upload>
                </div>
              </div>
              <div class="info-item">
                <label class="info-label">用户ID</label>
                <div class="info-value">{{ userInfo.id }}</div>
              </div>
              <div class="info-item">
                <label class="info-label">用户名</label>
                <div class="info-value">
                  {{ userInfo.name }}
                  <span class="edit-btn" @click="openSingleEdit('name', userInfo.name)">修改</span>
                </div>
              </div>
              <div class="info-item">
                <label class="info-label">实名</label>
                <div class="info-value">{{ userInfo.rname ? formatRealName(userInfo.rname) : '未实名' }}</div>
              </div>
              <div class="info-item">
                <label class="info-label">手机号</label>
                <div class="info-value">
                  {{ userInfo.phone ? formatPhoneLastFour(userInfo.phone) : '未设置' }}
                  <span class="edit-btn" @click="openSingleEdit('phone', userInfo.phone)">修改</span>
                </div>
              </div>
              <div class="info-item">
                <label class="info-label">年龄</label>
                <div class="info-value">
                  {{ userInfo.age || '未设置' }}
                  <span class="edit-btn" @click="openSingleEdit('age', userInfo.age || 0)">修改</span>
                </div>
              </div>
              <div class="info-item">
                <label class="info-label">身份认证</label>
                <div class="info-value">{{ userInfo.identity || '未认证' }}</div>
              </div>
              <div class="info-item">
                <label class="info-label">信用分</label>
                <div class="info-value">{{ userInfo.credit }}</div>
              </div>
              <div class="info-item">
                <label class="info-label">时间偏好</label>
                <div class="info-value">
                  <span class="time-preference-summary">{{ formatTimePreference(userInfo.timepreference) }}</span>
                  <span class="edit-btn" @click="openTimePreferenceDialog">修改</span>
                </div>
              </div>
            </div>
            <div class="profile-actions">
              <button class="primary-btn" @click="editProfile">修改个人信息</button>
            </div>
          </div>

          <!-- 一周安排 -->
          <div v-if="activeMenu === 'schedule'" class="schedule-section">
            <div class="schedule-header">
              <h2 class="section-title">周表</h2>
              <el-button
                v-if="userInfo.identity === '学生'"
                type="primary"
                size="small"
                @click="goToMyAcceptedJobs"
              >查看目前所有兼职</el-button>
            </div>
            <!-- 修改后 -->
            <StudentWeeklySchedule v-if="userInfo.identity === '学生'" key="schedule-student"/>
            <MerchantJobSchedule v-else-if="userInfo.identity === '商户'" key="schedule-merchant"/>
            <div v-else class="no-schedule" key="schedule-empty"><p>暂无工作安排</p></div>
          </div>

          <!-- 安全中心 -->
          <div v-if="activeMenu === 'security'" class="security-section">
            <h2 class="section-title">安全中心</h2>
            <div class="security-actions">
              <button class="security-btn" @click="changePassword">修改密码</button>
              <button v-if="userInfo.identity === '未认证'" class="security-btn" @click="studentAuth">学生认证</button>
              <button v-if="userInfo.identity !== '商户'" class="security-btn" @click="userAuthentication">商户认证</button>
              <button class="security-btn" @click="viewCredit">信用分查看</button>
            </div>
          </div>
          <!-- 数据概述 -->
          <!-- 数据概述 -->
          <div v-if="activeMenu === 'statistics'" class="statistics-section">
            <h2 class="section-title">数据概述</h2>

            <div v-if="userInfo.identity === '学生' || userInfo.identity === '商户'">
              <div class="stats-cards" v-if="statsCards.length > 0">
                <div v-for="card in statsCards" :key="card.label" class="stat-card">
                  <div class="card-value">{{ card.value }}</div>
                  <div class="card-label">{{ card.label }}</div>
                </div>
              </div>
              <div class="chart-grid" v-if="chartConfigs.length > 0">
                <div v-for="chart in chartConfigs" :key="chart.id" class="chart-card">
                  <h4>{{ chart.title }}</h4>
                  <div :ref="el => setChartRef(el, chart.id)" style="width:100%;height:320px;"></div>
                </div>
              </div>
              <div v-if="statsCards.length === 0 && chartConfigs.length === 0" class="empty-stats">
                <span class="empty-icon">📊</span>
                <p>暂无统计数据，开始使用后这里会有分析</p>
              </div>
            </div>

            <div v-else class="empty-stats">
              <span class="empty-icon">🔒</span>
              <p>请先完成学生或商户认证，解锁数据概述功能</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 头像预览对话框 -->
    <el-dialog v-model="avatarPreviewVisible" title="头像预览" width="400px" align-center>
      <div class="avatar-preview-dialog">
        <img :src="imagePreviewUrl" alt="头像预览"/>
      </div>
    </el-dialog>

    <!-- 单项修改对话框 -->
    <el-dialog v-model="singleEditDialogVisible" :title="singleEditType === 'name' ? '修改用户名' : singleEditType === 'phone' ? '修改手机号' : '修改年龄'" width="400px">
      <el-form>
        <el-form-item :label="singleEditType === 'name' ? '用户名' : singleEditType === 'phone' ? '手机号' : '年龄'" required>
          <template v-if="singleEditType === 'age'">
            <el-input-number v-model="singleEditValue" :min="0" :max="150" placeholder="请输入年龄"/>
          </template>
          <template v-else>
            <el-input v-model="singleEditValue" :placeholder="singleEditType === 'name' ? '请输入用户名' : '请输入手机号'" :maxlength="singleEditType === 'name' ? 20 : 11"/>
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="singleEditDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveSingleEdit">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 修改个人信息对话框 -->
    <el-dialog v-model="editDialogVisible" title="修改个人信息" width="600px">
      <el-form>
        <el-form-item label="头像">
          <div class="edit-avatar-container">
            <div class="avatar" :class="{ 'has-image': editForm.image }" @click="previewEditAvatar">
              <img v-if="editForm.image" :src="getImageUrl(editForm.image)" alt="头像"/>
              <span v-else>{{ (editForm.name && editForm.name.charAt(0)) || 'U' }}</span>
            </div>
            <el-upload action="#" :auto-upload="false" :show-file-list="false" :on-change="handleEditAvatarUpload" accept="image/*">
              <span class="edit-btn">修改</span>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="editForm.name" placeholder="请输入用户名" maxlength="20"/>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11"/>
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="editForm.age" :min="0" :max="150" placeholder="请输入年龄"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveEditProfile">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="changePasswordDialogVisible" title="修改密码" width="400px">
      <el-form>
        <el-form-item label="旧密码" required>
          <el-input v-model="changePasswordForm.oldPassword" type="password" placeholder="请输入旧密码" maxlength="20"/>
        </el-form-item>
        <el-form-item label="手机号" required>
          <div class="phone-display" v-if="userInfo.phone">
            <span class="phone-hide">{{ formatPhone(userInfo.phone) }}</span>
          </div>
          <el-input v-model="changePasswordForm.phone" placeholder="请输入绑定的手机号" maxlength="11"/>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="changePasswordForm.newPassword" type="password" placeholder="请输入新密码" maxlength="20"/>
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input v-model="changePasswordForm.confirmPassword" type="password" placeholder="请再次输入新密码" maxlength="20"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePasswordDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveChangePassword">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 认证申请对话框 -->
    <el-dialog v-model="authDialogVisible" title="商户认证申请" width="600px">
      <el-form>
        <el-form-item label="营业执照图片" required>
          <div class="edit-avatar-container">
            <div class="avatar" :class="{ 'has-image': authForm.image }" @click="previewEditAvatar">
              <img v-if="authForm.image" :src="getImageUrl(authForm.image)" alt="营业执照"/>
              <span v-else>上传图片</span>
            </div>
            <el-upload action="#" :auto-upload="false" :show-file-list="false" :on-change="handleAuthImageUpload" accept="image/*">
              <span class="edit-btn">上传</span>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="商户名称" required>
          <el-input v-model="authForm.businessName" placeholder="请输入商户名称" maxlength="50"/>
        </el-form-item>
        <el-form-item label="联系人姓名" required>
          <el-input v-model="authForm.contactPerson" placeholder="请输入联系人姓名" maxlength="20"/>
        </el-form-item>
        <el-form-item label="联系电话" required>
          <el-input v-model="authForm.contactPhone" placeholder="请输入联系电话" maxlength="11"/>
        </el-form-item>
        <el-form-item label="身份证号" required>
          <el-input v-model="authForm.idcard" placeholder="请输入18位身份证号" maxlength="18"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="authDialogVisible = false">取消</el-button>
          <el-button @click="viewAuthHistory">查看申请历史</el-button>
          <el-button type="primary" @click="submitAuthApplication" :loading="loading">提交申请</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 学生认证对话框 -->
    <el-dialog v-model="studentAuthDialogVisible" title="学生认证" width="500px" align-center>
      <el-form label-width="80px">
        <el-form-item label="真实姓名" required>
          <el-input v-model="studentAuthForm.name" placeholder="请输入真实姓名"/>
        </el-form-item>
        <el-form-item label="学号" required>
          <el-input v-model="studentAuthForm.number" placeholder="请输入学号"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="studentAuthDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitStudentAuth" :loading="loading">提交认证</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 认证申请历史对话框 -->
    <el-dialog v-model="authHistoryDialogVisible" title="认证申请历史" width="800px" align-center>
      <div v-loading="loading" class="history-content">
        <div v-if="authHistory.length === 0" class="empty-history">
          <span class="empty-icon">📝</span>
          <p>暂无认证申请记录</p>
        </div>
        <div v-else class="auth-history-list">
          <div v-for="item in authHistory" :key="item.id" class="auth-history-item">
            <div class="auth-history-header">
              <span class="auth-history-date">{{ new Date(item.data).toLocaleString() }}</span>
              <el-tag :type="item.status === '已批准' ? 'success' : item.status === '已驳回' ? 'danger' : 'warning'" size="small">
                {{ item.status || '待审核' }}
              </el-tag>
            </div>
            <div class="auth-history-image" v-if="item.imageurl">
              <img :src="getImageUrl(item.imageurl)" alt="营业执照" @click="previewAvatar"/>
            </div>
            <div class="auth-history-info" v-if="item.bname || item.user">
              <div class="auth-history-info-item" v-if="item.bname">
                <label>商户名称：</label>
                <span>{{ item.bname }}</span>
              </div>
              <div class="auth-history-info-item" v-if="item.user">
                <label>申请人：</label>
                <span>{{ item.user.name }}</span>
              </div>
              <div class="auth-history-info-item" v-if="item.user">
                <label>联系电话：</label>
                <span>{{ item.user.phone }}</span>
              </div>
            </div>
            <div v-if="item.respond" class="auth-history-reply">
              <div class="reply-label">管理员回复：</div>
              <div class="reply-content">{{ item.respond }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 时间偏好设置对话框 -->
    <el-dialog v-model="timePreferenceDialogVisible" title="时间偏好设置" width="600px">
      <div class="time-warning">
        <p class="warning-text">请同学们严格按照自己的课表来安排自己可用的空余时间</p>
      </div>
      <div class="time-slot-selector">
        <div v-for="day in weekDays" :key="day.id" class="day-section">
          <div class="day-title">{{ day.name }}</div>
          <div class="period-buttons">
            <el-button v-for="period in timePeriods" :key="period.id" :type="isTimeSlotSelected(day.id, period.id) ? 'primary' : 'info'" :plain="!isTimeSlotSelected(day.id, period.id)" @click="toggleTimeSlot(day.id, period.id)" size="small" class="period-btn">
              {{ period.name }}
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="timePreferenceDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveTimePreference">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 信用分记录弹窗（修复后含规则提示） -->
    <el-dialog v-model="creditDialogVisible" width="850px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between;">
          <span>信用分记录</span>
          <el-tooltip placement="left" effect="light">
            <template #content>
              <div style="max-width:260px;line-height:1.6;">
                <p><b>信用分规则</b></p>
                <p>完成工作：+2分/小时 (超过50分钟算1小时)</p>
                <p>迟到：-8分</p>
                <p>缺勤：-15分</p>
                <p>好评：+3分 | 差评：-5分</p>
                <p>信用分低于50分将被冻结</p>
              </div>
            </template>
            <el-button type="text" size="small" style="padding:0;">
              <span style="font-size:16px;cursor:help;">❓</span>
            </el-button>
          </el-tooltip>
        </div>
      </template>

      <div v-if="loadingCreditRecords" v-loading="loadingCreditRecords" style="min-height:200px;"></div>
      <div v-else-if="creditRecords.length === 0" style="text-align:center;padding:40px;color:#999;">暂无信用分记录</div>
      <div v-else class="credit-records">
        <el-table :data="pagedCreditRecords" style="width: 100%">
          <el-table-column label="行为 / 时间" width="320">
            <template #default="scope">
              <div style="display:flex;align-items:center;gap:8px;">
                <el-tag :type="getCreditTagType(scope.row)" size="small">
                  {{ getCreditActionText(scope.row) }}
                </el-tag>
                <span style="color:#909399;font-size:12px;">{{ formatCreditDate(scope.row.createdAt) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分值变化" width="100" align="center">
            <template #default="scope">
              <span :class="getCreditChangeClass(scope.row.changeScore)" style="font-weight:bold;">
                {{ scope.row.changeScore != null ? (scope.row.changeScore > 0 ? '+' : '') + scope.row.changeScore : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
        </el-table>

        <div class="pagination-wrapper" style="margin-top:16px;text-align:center;">
          <el-pagination
              v-model:current-page="creditPage"
              :page-size="creditPageSize"
              :total="creditRecords.length"
              layout="prev, pager, next"
              size="small"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page { min-height: 100vh; background-color: #f5f7fa; }
.profile-content { display: flex; flex-direction: column; min-height: calc(100vh - 60px); max-width: 1200px; margin: 0 auto; padding: 30px; gap: 20px; }
.back-home-btn { align-self: flex-start; margin-bottom: 10px; }
.btn-icon { margin-right: 5px; }
.content-area { display: flex; gap: 30px; flex: 1; }
.profile-sidebar { width: 200px; background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); padding: 20px; }
.sidebar-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 20px 0; }
.sidebar-menu { display: flex; flex-direction: column; gap: 8px; }
.menu-item { padding: 12px 16px; border-radius: 6px; cursor: pointer; transition: all 0.3s ease; color: #666; font-size: 14px; }
.menu-item:hover { background-color: #f5f7fa; color: #667eea; }
.menu-item.active { background-color: #667eea; color: white; }
.profile-main { flex: 1; background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); padding: 30px; }
.section-title { font-size: 20px; font-weight: 600; color: #333; margin: 0 0 30px 0; }
.schedule-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.schedule-header .section-title { margin: 0; }
.profile-info { display: flex; flex-direction: column; gap: 20px; margin-bottom: 30px; }
.info-item { display: flex; align-items: center; padding: 16px 0; border-bottom: 1px solid #f0f0f0; }
.info-label { width: 100px; font-size: 14px; color: #666; font-weight: 500; }
.info-value { flex: 1; font-size: 14px; color: #333; display: flex; align-items: center; gap: 12px; }
.avatar-container { display: flex; align-items: center; gap: 12px; }
.avatar { width: 60px; height: 60px; border-radius: 50%; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center; color: white; font-size: 24px; font-weight: 600; overflow: hidden; cursor: pointer; transition: all 0.3s ease; }
.avatar.has-image { background: none; border: 2px solid #eaeaea; }
.avatar:hover { transform: scale(1.05); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.edit-btn { font-size: 12px; color: #667eea; cursor: pointer; padding: 4px 8px; border-radius: 4px; transition: background-color 0.3s ease; }
.edit-btn:hover { background-color: #f5f7fa; }
.time-preference-summary { font-size: 14px; color: #333; background-color: #f0f4ff; padding: 6px 12px; border-radius: 4px; border-left: 3px solid #667eea; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: pre-line; line-height: 1.5; }
.profile-actions { display: flex; justify-content: center; padding-top: 20px; border-top: 1px solid #f0f0f0; }
.primary-btn { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 6px; padding: 12px 32px; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.3s ease; }
.primary-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
.security-actions { display: flex; flex-direction: column; gap: 16px; }
.security-btn { background: #f5f7fa; border: 1px solid #eaeaea; border-radius: 6px; padding: 20px 30px; font-size: 16px; font-weight: 500; color: #333; cursor: pointer; transition: all 0.3s ease; text-align: left; }
.security-btn:hover { background: #667eea; color: white; border-color: #667eea; transform: translateX(8px); }
.avatar-preview-dialog { display: flex; justify-content: center; align-items: center; padding: 20px; }
.avatar-preview-dialog img { max-width: 100%; max-height: 300px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
.edit-avatar-container { display: flex; align-items: center; gap: 12px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
.auth-history-list { display: flex; flex-direction: column; gap: 20px; }
.auth-history-item { border: 1px solid #f0f0f0; border-radius: 8px; padding: 20px; background-color: #f9f9f9; }
.auth-history-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #e0e0e0; }
.auth-history-date { font-size: 14px; color: #666; }
.auth-history-image { margin: 16px 0; }
.auth-history-image img { max-width: 200px; max-height: 150px; border-radius: 4px; cursor: pointer; transition: transform 0.3s ease; }
.auth-history-image img:hover { transform: scale(1.05); }
.auth-history-info { margin: 16px 0; display: flex; flex-direction: column; gap: 8px; }
.auth-history-info-item { display: flex; align-items: center; gap: 12px; }
.auth-history-info-item label { width: 80px; font-size: 14px; color: #666; font-weight: 500; }
.auth-history-info-item span { font-size: 14px; color: #333; }
.phone-display { margin-bottom: 8px; font-size: 14px; color: #606266; }
.phone-hide { color: #909399; font-weight: 500; }
.auth-history-reply { margin-top: 16px; padding: 12px; background-color: #f0f7ff; border-radius: 4px; border-left: 4px solid #667eea; }
.reply-label { font-size: 14px; font-weight: 500; color: #667eea; margin-bottom: 8px; }
.reply-content { font-size: 14px; color: #333; line-height: 1.5; }
.empty-history { text-align: center; padding: 40px 0; color: #999; }
.empty-icon { font-size: 48px; display: block; margin-bottom: 16px; }
.history-content { max-height: 500px; overflow-y: auto; }
.time-slot-selector { margin: 20px 0; }
.day-section { margin-bottom: 15px; }
.day-title { font-weight: bold; margin-bottom: 8px; color: #333; }
.period-buttons { display: flex; gap: 10px; flex-wrap: wrap; }
.period-btn { margin-right: 8px; margin-bottom: 8px; }
.time-warning { background-color: #fff3f3; border: 1px solid #ffd5d5; border-radius: 4px; padding: 10px; margin-bottom: 20px; }
.warning-text { color: #ff4d4f; margin: 0; font-size: 14px; }
.credit-increase { color: #67c23a; font-weight: bold; }
.credit-decrease { color: #f56c6c; font-weight: bold; }
.credit-neutral { color: #909399; font-weight: bold; }
.credit-records { max-height: 400px; overflow-y: auto; }

@media (max-width: 768px) {
  .profile-content { padding: 20px; gap: 20px; }
  .profile-sidebar { width: 100%; }
  .sidebar-menu { flex-direction: row; justify-content: space-around; }
  .menu-item { text-align: center; }
  .profile-main { padding: 20px; }
  .info-item { flex-direction: column; align-items: flex-start; gap: 8px; }
  .info-label { width: 100%; }
}
.stats-cards { display: flex; gap: 15px; margin-bottom: 30px; flex-wrap: wrap; }
.stat-card { background: white; border-radius: 8px; padding: 20px; flex: 1; min-width: 140px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); text-align: center; }
.stat-card .card-value { font-size: 28px; font-weight: bold; color: #333; }
.stat-card .card-label { font-size: 14px; color: #888; margin-top: 8px; }
.chart-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 20px; }
.chart-card { background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 6px rgba(0,0,0,0.08); }
.chart-card h4 { margin: 0 0 12px; color: #333; }

.empty-stats {
  text-align: center;
  padding: 60px 20px;
  color: #999;
}
.empty-stats .empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}
</style>