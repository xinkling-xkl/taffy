<script setup>
import {ref, onMounted, computed} from 'vue'
import {useRouter} from 'vue-router'
import GeneralNav from '../nav/GeneralNav.vue'
import JobEditDialog from '../components/JobEditDialog.vue'
import {
  ElMessage,
  ElMessageBox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElButton,
  ElUpload,
  ElLoading
} from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()

// 搜索相关
const searchKeyword = ref('')

// 兼职类型筛选
const selectedCategory = ref('all') // all, work_study, campus_delivery

// 公告数据
const announcements = ref([])

// 兼职数据
const parttimeJobs = ref([])

// 过滤后的兼职列表
const filteredJobs = ref([])

// 分页相关变量
const currentPage = ref(1)
const pageSize = 6
const totalJobs = ref(0)

// 加载状态
const loading = ref(true)

// 当前用户信息
const currentUser = ref(null)

// 发布/编辑对话框
const dialogVisible = ref(false)
const editingItem = ref(null)
const isAnnouncementMode = ref(false)



// 将工作时间JSON转换为自然语言
const formatJobTime = (timeStr) => {
  if (!timeStr) return '未设置'
  try {
    const timeObj = typeof timeStr === 'string' ? JSON.parse(timeStr) : timeStr
    let result = ''

    // 处理时间槽
    if (timeObj.timeSlots && timeObj.timeSlots.length > 0) {
      const dayMap = {
        1: '周一',
        2: '周二',
        3: '周三',
        4: '周四',
        5: '周五',
        6: '周六',
        7: '周日'
      }

      const periodMap = {
        'morning': '上午',
        'afternoon': '下午',
        'evening': '晚上'
      }

      // 按天分组
      const slotsByDay = {}
      timeObj.timeSlots.forEach(slot => {
        const day = slot.day
        if (!slotsByDay[day]) {
          slotsByDay[day] = []
        }
        slotsByDay[day].push(periodMap[slot.period])
      })

      // 构建两天一行的格式
      const dayNames = [1, 2, 3, 4, 5, 6, 7]
      const lines = []

      for (let i = 0; i < dayNames.length; i += 2) {
        const day1 = dayNames[i]
        const day2 = dayNames[i + 1]

        let line = ''
        if (slotsByDay[day1]) {
          line += `${dayMap[day1]} ${slotsByDay[day1].join(' ')}`
        }

        if (slotsByDay[day2]) {
          if (line) line += '、'
          line += `${dayMap[day2]} ${slotsByDay[day2].join(' ')}`
        }

        if (line) {
          lines.push(line)
        }
      }

      result = lines.join('、')
    }

    return result
  } catch (error) {
    console.error('解析时间失败:', error)
    return timeStr
  }
}

// 图片预览相关
const previewDialogVisible = ref(false)
const previewImageUrl = ref('')

const getImageUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  if (path.startsWith('/uploads/')) return `http://localhost:8082${path}`
  return `http://localhost:8082/uploads${path}`
}

const previewImage = (url) => {
  previewImageUrl.value = url
  previewDialogVisible.value = true
}

// 计算过滤后的兼职
const filterJobs = () => {
  if (selectedCategory.value === 'all') {
    filteredJobs.value = parttimeJobs.value
  } else {
    filteredJobs.value = parttimeJobs.value.filter(job => job.category === selectedCategory.value)
  }
}

// 搜索功能
const searchJobs = () => {
  if (!searchKeyword.value.trim()) {
    filterJobs()
    return
  }

  const keyword = searchKeyword.value.toLowerCase()
  filteredJobs.value = parttimeJobs.value.filter(job =>
      (selectedCategory.value === 'all' || job.category === selectedCategory.value) &&
      (job.title.toLowerCase().includes(keyword) ||
          job.content.toLowerCase().includes(keyword) ||
          (job.tags && job.tags.some(tag => tag.toLowerCase().includes(keyword)))
      )
  )
}

// 打开智能匹配对话框
const openMatchingDialog = () => {
  router.push('/ai-match')
}

// 跳转到安全中心
const goToSecurityCenter = () => {
  router.push('/profile?menu=security')
}

// 获取公告列表
const getAnnouncements = async () => {
  try {
    const response = await axios.get('/api/announcement/published')
    announcements.value = response.data.data
  } catch (error) {
    console.error('获取公告失败:', error)
    ElMessage.error('获取公告失败')
  }
}

// 获取兼职列表
const getParttimeJobs = async () => {
  try {
    const response = await axios.get('/api/job/list')
    parttimeJobs.value = response.data.data
    // 处理每个兼职的标签
    parttimeJobs.value.forEach(job => {
      if (job.tags) {
        try {
          job.tags = JSON.parse(job.tags)
        } catch (e) {
          job.tags = []
        }
      } else {
        job.tags = []
      }
      // 将hourly结算类型转换为daily
      if (job.remunerationType === 'hourly') {
        job.remunerationType = 'daily'
      }
    })
    filterJobs()
  } catch (error) {
    console.error('获取兼职列表失败:', error)
    ElMessage.error('获取兼职列表失败')
  } finally {
    loading.value = false
  }
}

// ==================== 急招专区 ====================
// 加载急招列表
const loadUrgentList = async () => {
  try {
    const res = await axios.get('/api/job/urgent-list')
    if (res.data.success) {
      urgentList.value = res.data.data || []
    }
  } catch {
    console.error('加载急招失败')
  }
}

// 急招申请
const applyUrgent = async (item) => {
  const user = SessionManager.getCurrentUserInfo()
  if (!user || user.identity !== '学生') {
    ElMessage.warning('只有学生可以申请')
    return
  }

  // 检查时间偏好
  if (!user.timepreference) {
    ElMessage.warning('请先前往个人中心设置您的空闲时间偏好，才能进行抢单')
    return
  }

  // 防止重复点击
  if (urgentApplying.value) return

  // 弹出确认对话
  try {
    await ElMessageBox.confirm(
        `确认申请「${item.job_title}」在 ${item.work_date} ${getTimeSlotText(item.time_slot)} 的急招工作吗？\n\n工作地点：${item.address}\n薪资：${item.salary}\n\n您的空闲时间偏好：\n${formatMyTimePreference(user.timepreference)}`,
        '急招申请确认',
        {
          confirmButtonText: '确定申请',
          cancelButtonText: '取消',
          type: 'info'
        }
    )
  } catch {
    return // 取消
  }

  urgentApplying.value = true
  try {
    const res = await axios.post('/api/job/urgent-apply', {
      urgentId: item.id,
      studentId: user.id
    })
    if (res.data.success) {
      ElMessage.success('申请成功！工作已安排')
      await loadUrgentList()  // 确保刷新
      await getParttimeJobs() // 可选，若需更新兼职列表
    } else {
      ElMessage.error(res.data.message || '申请失败')
    }
  } catch (e) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    urgentApplying.value = false
  }
}

// 删除急招记录
const deleteUrgent = async (item) => {
  try {
    const user = SessionManager.getCurrentUserInfo()
    await ElMessageBox.confirm(
      `确定要删除急招「${item.job_title}」在 ${item.work_date} ${getTimeSlotText(item.time_slot)} 的记录吗？`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await axios.delete(`/api/job/urgent/${item.id}`, { params: { userId: user.id } })
    if (res.data.success) {
      ElMessage.success('急招记录已删除')
      await loadUrgentList()
    } else {
      ElMessage.error(res.data.message || '删除失败')
    }
  } catch (e) {
    // 取消或错误
  }
}

// 格式化自己的时间偏好用于展示
const formatMyTimePreference = (timepref) => {
  if (!timepref) return '未设置'
  try {
    const arr = JSON.parse(timepref)
    const dayMap = {1:'周一',2:'周二',3:'周三',4:'周四',5:'周五',6:'周六',7:'周日'}
    const periodMap = {morning:'上午',afternoon:'下午',evening:'晚上',1:'上午',2:'下午',3:'晚上'}
    arr.sort((a,b) => a.day - b.day || String(a.period).localeCompare(String(b.period)))
    return arr.map(s => `${dayMap[s.day]} ${periodMap[s.period] || s.period}`).join('、')
  } catch { return '格式错误' }
}

// 时间段文本
const getTimeSlotText = (slot) => {
  return {morning: '上午', afternoon: '下午', evening: '晚上'}[slot] || slot
}

// 打开发布/编辑对话框
const openJobDialog = (job = null, isAnnouncement = false) => {
  isAnnouncementMode.value = isAnnouncement
  editingItem.value = job
  dialogVisible.value = true
}

const onDialogSaved = () => {
  dialogVisible.value = false
  if (isAnnouncementMode.value) {
    getAnnouncements()
  } else {
    getParttimeJobs()
  }
}

const deleteJob = async (job) => {
  try {
    const user = SessionManager.getCurrentUserInfo()
    const userId = user.id

    ElMessageBox.confirm(
      `确定要删除公告"${job.title}"吗？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    ).then(async () => {
      const response = await axios.delete(`/api/announcement/${job.id}`, { params: { userId } })
      if (response.data.success) {
        ElMessage.success('公告删除成功')
        await getAnnouncements()
      } else {
        ElMessage.error(response.data.message || '删除失败')
      }
    }).catch(() => {})
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败，请重试')
  }
}

// 申请兼职对话框
const applyDialogVisible = ref(false)
const currentJob = ref(null)
const applicationContent = ref('')

// 日结相关状态
const dailyApplyDialogVisible = ref(false)
const currentDailyJob = ref(null)
const availableDates = ref([])
const selectedSlotsMap = ref({}) // { date: [timeSlot, ...] }
const dailyApplyContent = ref('')

// 获取本周一
const getWeekStart = () => {
  const now = new Date()
  const day = now.getDay() || 7
  const monday = new Date(now)
  monday.setDate(now.getDate() - (day - 1))
  monday.setHours(0, 0, 0, 0)
  return monday
}



// 根据兼职配置和学生时间偏好，获取本周可选日期及可用时段
// 安全解析本地日期（避免 UTC 时区偏移）
const parseLocalDate = (dateStr) => {
  const parts = dateStr.split('-')
  return new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
}

// 获取时段的开始时间（用于判断是否已过）
const getSlotStartHour = (slot) => {
  if (slot === 'morning') return 9   // 上午 9:00
  if (slot === 'afternoon') return 14 // 下午 14:00
  if (slot === 'evening') return 18   // 晚上 18:00
  return 0
}

// 检查时间段是否已被占用
const checkTimeSlotOccupied = async (jobId, date, timeSlot) => {
  try {
    const response = await axios.get('/api/job/check-slot-occupied', {
      params: {
        jobId: jobId,
        date: date,
        timeSlot: timeSlot
      }
    })
    return response.data.data || false
  } catch (error) {
    console.error('检查时间段占用失败:', error)
    return false
  }
}

const getAvailableDailyDates = async (job, studentTimePref, studentId) => {
  if (!job.time) return []

  let timeObj
  try {
    timeObj = JSON.parse(job.time)
  } catch {
    return []
  }
  if (!timeObj.timeSlots || !Array.isArray(timeObj.timeSlots)) return []

  // 获取该兼职各时段的已招 / 需招人数（通过 API 实时获取，不依赖 job.requirements）
  let requirements = []
  try {
    const reqRes = await axios.get('/api/schedule/merchant/requirements', {
      params: { merchantId: job.userId }
    })
    if (reqRes.data.success && reqRes.data.data) {
      requirements = reqRes.data.data.filter(r => r.job_id === job.id)
    }
  } catch {}

  // 解析学生时间偏好（兼容新旧格式）
  let studentSlots = []
  if (studentTimePref && typeof studentTimePref === 'string') {
    try {
      const parsed = JSON.parse(studentTimePref)
      if (Array.isArray(parsed)) {
        parsed.forEach(p => {
          if (p.day && p.period) {
            const period = typeof p.period === 'number'
              ? (p.period === 1 ? 'morning' : (p.period === 2 ? 'afternoon' : 'evening'))
              : p.period
            studentSlots.push({ day: p.day, period: period })
          }
        })
      } else if (typeof parsed === 'object' && parsed !== null) {
        const dayNameMap = {
          'monday': 1, 'tuesday': 2, 'wednesday': 3, 'thursday': 4,
          'friday': 5, 'saturday': 6, 'sunday': 7
        }
        for (const [key, value] of Object.entries(parsed)) {
          const day = dayNameMap[key.toLowerCase()]
          if (day && typeof value === 'string') {
            if (value.includes('白天') || value.includes('上午')) studentSlots.push({ day, period: 'morning' })
            if (value.includes('白天') || value.includes('下午')) studentSlots.push({ day, period: 'afternoon' })
            if (value.includes('夜晚') || value.includes('晚上') || value.includes('全天')) studentSlots.push({ day, period: 'evening' })
          }
        }
      }
    } catch {}
  }

  // 获取学生已占用的具体日期+时段（从 work_assignment 表，最可靠）
  let studentOccupiedMap = {}
  if (studentId) {
    try {
      const assignRes = await axios.get(`/api/assignment/student/${studentId}`)
      if (assignRes.data.success && assignRes.data.data) {
        assignRes.data.data.forEach(a => {
          if (a.work_date && a.time_slot) {
            studentOccupiedMap[`${a.work_date}_${a.time_slot}`] = true
          }
        })
      }
    } catch {}
  }

  // 日期范围：今天 ~ 本周日
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dayOfWeek = today.getDay() || 7  // 1=周一, 7=周日
  const endOfWeek = new Date(today)
  endOfWeek.setDate(today.getDate() + (7 - dayOfWeek))

  const result = []
  const currentDate = new Date(today)

  while (currentDate <= endOfWeek) {
    const currentDay = currentDate.getDay() || 7
    // 今天是第几天的偏移（0 表示今天）
    const dayDiff = Math.floor((currentDate - today) / (1000 * 60 * 60 * 24))

    // 获取该天商户需要的时段
    const merchantSlots = timeObj.timeSlots
        .filter(s => s.day === currentDay)
        .map(s => s.period)

    if (merchantSlots.length === 0) {
      currentDate.setDate(currentDate.getDate() + 1)
      continue
    }

    // 1) 排除已满时段
    let availableSlots = merchantSlots.filter(slot => {
      const req = requirements.find(r => {
        const dayInt = typeof r.day_of_week === 'string' ? parseInt(r.day_of_week) : r.day_of_week
        return dayInt === currentDay && r.time_slot === slot
      })
      if (!req) return false
      const needed = parseInt(req.needed_count) || 0
      const filled = parseInt(req.filled_count) || 0
      return filled < needed
    })

    // 2) 如果是今天，过滤已过时间的时段
    if (dayDiff === 0) {
      const currentHour = now.getHours()
      availableSlots = availableSlots.filter(slot => {
        const startHour = getSlotStartHour(slot)
        return currentHour < startHour  // 当前时间小于该时段开始时间才可选
      })
    }

    if (availableSlots.length === 0) {
      currentDate.setDate(currentDate.getDate() + 1)
      continue
    }

    // 3) 如果学生设置了时间偏好，再取交集
    if (studentSlots.length > 0) {
      const studentDaySlots = studentSlots
          .filter(s => s.day === currentDay)
          .map(s => {
            if (typeof s.period === 'number') {
              return s.period === 1 ? 'morning' : (s.period === 2 ? 'afternoon' : 'evening')
            }
            return s.period
          })
      availableSlots = availableSlots.filter(slot => studentDaySlots.includes(slot))
    }

    // 4) 检查每个时间段是否已被其他学生占用
    const dateStr =
        currentDate.getFullYear() +
        '-' +
        String(currentDate.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(currentDate.getDate()).padStart(2, '0')

    // 并行检查所有时间段
    const slotChecks = await Promise.all(
      availableSlots.map(async (slot) => {
        const isOccupied = await checkTimeSlotOccupied(job.id, dateStr, slot)
        return { slot, isOccupied }
      })
    )

    // 过滤掉已被占用的时间段
    let finalAvailableSlots = slotChecks
      .filter(check => !check.isOccupied)
      .map(check => check.slot)

    // 5) 过滤学生自己已占用的时段（从 work_assignment 直接查）
    if (Object.keys(studentOccupiedMap).length > 0) {
      finalAvailableSlots = finalAvailableSlots.filter(slot => {
        return !studentOccupiedMap[`${dateStr}_${slot}`]
      })
    }

    if (finalAvailableSlots.length > 0) {
      result.push({
        date: dateStr,
        dayOfWeek: currentDay,
        slots: finalAvailableSlots
      })
    }

    currentDate.setDate(currentDate.getDate() + 1)
  }

  return result
}

// 打开日结申请对话框
const openDailyApplyDialog = async (job) => {
  currentDailyJob.value = job
  const user = SessionManager.getCurrentUserInfo()

  const loadingInstance = ElLoading.service({ text: '正在检查可用时间段...', fullscreen: false, background: 'rgba(255,255,255,0.7)' })

  try {
    availableDates.value = await getAvailableDailyDates(job, user?.timepreference, user?.id)
    selectedSlotsMap.value = {}
    dailyApplyContent.value = ''
    dailyApplyDialogVisible.value = true
  } catch (error) {
    console.error('获取可用日期失败:', error)
    ElMessage.error('获取可用日期失败，请稍后重试')
  } finally {
    loadingInstance.close()
  }
}

// 提交日结申请
const submitDailyApply = async () => {
  const selectedDates = []
  for (const [date, slots] of Object.entries(selectedSlotsMap.value)) {
    if (slots.length > 0) {
      slots.forEach(slot => selectedDates.push({date, timeSlot: slot}))
    }
  }
  if (selectedDates.length === 0) {
    ElMessage.warning('请至少选择一天')
    return
  }

  const user = SessionManager.getCurrentUserInfo()
  try {
    const res = await axios.post('/api/application/create', {
      jobId: currentDailyJob.value.id,
      studentId: user.id,
      merchantId: currentDailyJob.value.userId,
      studentName: user.name,
      timeAvailability: user.timepreference,
      creditScore: user.credit || 60,
      applicationContent: dailyApplyContent.value,
      selectedDates: JSON.stringify(selectedDates)
    })
    if (res.data.success) {
      ElMessage.success('申请成功')
      dailyApplyDialogVisible.value = false
      getParttimeJobs()
    } else {
      ElMessage.error(res.data.message || '申请失败')
    }
  } catch {
    ElMessage.error('申请失败')
  }
}

// 打开申请对话框（原版，非日结）
const openApplyDialog = (job) => {
  currentJob.value = job
  applicationContent.value = ''
  applyDialogVisible.value = true
}

// 检查学生是否已申请兼职
const hasAppliedJob = (jobId) => {
  const user = SessionManager.getCurrentUserInfo()
  if (!user || !user.id) return false

  // 这里可以通过API检查学生是否已申请该兼职
  // 暂时返回false，实际项目中应该调用API
  return false
}

// 跳转到兼职详情页
const goToJobDetail = (jobId) => {
  // 使用 Vue Router 跳转到详情页
  router.push(`/job/${jobId}`)
}

// 申请兼职
const applyJob = (job) => {
  console.log('applyJob called, job:', job?.title, 'jobId:', job?.id)
  try {
    const user = SessionManager.getCurrentUserInfo()
    console.log('applyJob user:', user?.name, 'identity:', user?.identity)

    if (!user) {
      ElMessage.error('请先登录')
      router.push('/userlogin')
      return
    }

    if (user.identity !== '学生') {
      ElMessage.error('只有学生身份才能申请兼职，请先进行学生认证')
      return
    }

    if (!user.rname) {
      ElMessage.error('请先进行实名认证')
      return
    }

    if (!user.phone) {
      ElMessage.error('请先添加联系电话')
      return
    }

    if (!user.timepreference) {
      ElMessage.error('请先添加时间安排')
      return
    }

    console.log('applyJob checks passed, opening dialog. remunerationType:', job?.remunerationType)
    if (job.remunerationType === 'daily' || job.remunerationType === 'hourly') {
      openDailyApplyDialog(job)
    } else {
      openApplyDialog(job)
    }
  } catch (e) {
    console.error('applyJob error:', e)
    ElMessage.error('操作失败，请刷新页面后重试')
  }
}

// 提交申请（原版，非日结）
const submitApplication = async (forceSubmit = false) => {
  if (!applicationContent.value.trim()) {
    ElMessage.warning('请输入申请内容')
    return
  }

  try {
    const user = SessionManager.getCurrentUserInfo()
    const userId = user.id

    // 检查学生时间偏好
    if (!user || !user.timepreference) {
      ElMessage.warning('请先设置时间偏好')
      return
    }

    try {
      const timepreference = JSON.parse(user.timepreference)
      if (!Array.isArray(timepreference)) {
        ElMessage.warning('时间偏好格式错误')
        return
      }

      // 检查工作时间需求是否与学生时间偏好匹配
      const job = currentJob.value
      const jobTimeRequirements = []
      if (job.time) {
        try {
          const timeObj = JSON.parse(job.time)
          const timeType = timeObj.type
          let timeSlot = 'morning'
          if (timeType && timeType.includes('下午')) timeSlot = 'afternoon'
          else if (timeType && (timeType.includes('晚上') || timeType.includes('夜'))) timeSlot = 'evening'

          const weekdays = timeObj.weekdays
          if (Array.isArray(weekdays)) {
            weekdays.forEach(weekday => {
              let day = 0
              switch (weekday) {
                case '周一':
                  day = 1;
                  break
                case '周二':
                  day = 2;
                  break
                case '周三':
                  day = 3;
                  break
                case '周四':
                  day = 4;
                  break
                case '周五':
                  day = 5;
                  break
                case '周六':
                  day = 6;
                  break
                case '周日':
                  day = 7;
                  break
              }
              if (day > 0) {
                jobTimeRequirements.push({day: day, timeSlot: timeSlot})
              }
            })
          }
        } catch (e) {
          console.error('解析工作时间失败:', e)
        }
      }

      // 检查是否有时间冲突
      const conflictingTimes = []
      jobTimeRequirements.forEach(req => {
        const isAvailable = timepreference.some(item => {
          const itemDay = item.day
          let itemSlot = item.period
          // 转换时间段格式
          if (itemSlot === 1) itemSlot = 'morning'
          else if (itemSlot === 2) itemSlot = 'afternoon'
          else if (itemSlot === 3) itemSlot = 'evening'
          return itemDay === req.day && itemSlot === req.timeSlot
        })

        if (!isAvailable) {
          const dayMap = {1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日'}
          const slotMap = {morning: '上午', afternoon: '下午', evening: '晚上'}
          conflictingTimes.push(`${dayMap[req.day]} ${slotMap[req.timeSlot]}`)
        }
      })

      if (conflictingTimes.length > 0 && !forceSubmit) {
        // 显示确认对话框
        ElMessageBox.confirm(
            `时间段冲突！以下时段不在您的时间偏好中：\n\n${conflictingTimes.join('\n')}\n\n确认要提交申请吗？`,
            '时间冲突提示',
            {
              confirmButtonText: '强行提交',
              cancelButtonText: '取消',
              type: 'warning'
            }
        ).then(async () => {
          await submitApplication(true)
        }).catch(() => {
        })
        return
      }
    } catch (e) {
      console.error('解析时间偏好失败:', e)
      ElMessage.error('时间偏好解析失败')
      return
    }

    const response = await axios.post('/api/application/create', {
      jobId: currentJob.value.id,
      studentId: userId,
      merchantId: currentJob.value.userId,
      studentName: user.name,
      timeAvailability: user.timepreference,
      creditScore: user.credit || 60,
      applicationContent: applicationContent.value
    })

    if (response.data.success) {
      ElMessage.success('申请成功')
      applyDialogVisible.value = false
      // 刷新兼职列表
      await getParttimeJobs()
    } else {
      // 显示具体的错误信息
      ElMessage.error('申请失败: ' + ((response.data && response.data.message) || '未知错误'))
      console.error('申请失败响应:', response.data)
    }
  } catch (error) {
    console.error('申请失败异常:', error)
    ElMessage.error('申请失败，请检查网络连接或稍后重试')
  }
}

// 举报兼职 - 跳转到问题反馈界面
const reportJob = async (job) => {
  const user = SessionManager.getCurrentUserInfo()
  if (!user) {
    ElMessage.error('请先登录')
    router.push('/userlogin')
    return
  }

  // 构建举报内容，包含兼职信息
  let reportContent = `举报兼职：${job.title} (ID: ${job.id})\n`
  reportContent += `发布者ID：${job.userId}\n`
  if (job.userName) {
    reportContent += `发布者名称：${job.userName}\n`
  }
  reportContent += `\n请详细描述举报原因和相关证据：`

  // 跳转到问题反馈界面，并传递预填内容
  router.push({
    path: '/feedback',
    query: {
      type: '用户举报',
      content: reportContent,
      reportedUserName: job.userName || `用户${job.userId}`,
      jobId: job.id,
      jobTitle: job.title
    }
  })
}

// 管理员编辑兼职
const editJob = (job) => {
  editingItem.value = job
  isAnnouncementMode.value = false
  dialogVisible.value = true
}

// 随机排列
const randomDirty = ref(0)
const randomCooldown = ref(false)
const randomizeJobs = () => {
  if (randomCooldown.value) {
    ElMessage.warning('操作太频繁，请5秒后再试')
    return
  }
  currentPage.value = 1
  randomDirty.value++
  randomCooldown.value = true
  setTimeout(() => {
    randomCooldown.value = false
  }, 5000)
}

// 管理员删除兼职
const deleteJobByAdmin = async (job) => {
  try {
    const user = SessionManager.getCurrentUserInfo()
    const userId = user.id

    await ElMessageBox.confirm(
      `确定要删除兼职"${job.title}"吗？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )

    const response = await axios.delete(`/api/job/${job.id}`, { params: { userId } })
    if (response.data.success) {
      ElMessage.success('兼职删除成功')
      await getParttimeJobs()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  }
}

// 获取结算类型文本
const getRemunerationText = (type) => {
  return type === 'daily' ? '日结' : type === 'weekly' ? '周结' : ''
}

// 计算用户是否为管理员
const isAdmin = computed(() => {
  return currentUser.value && currentUser.value.identity === '管理员'
})

// 计算用户是否为商户
const isMerchant = computed(() => {
  return currentUser.value && currentUser.value.identity !== '学生' && currentUser.value.identity !== '管理员'
})

// 计算用户是否为学生
const isStudent = computed(() => {
  return currentUser.value && currentUser.value.identity === '学生'
})

// 分页相关计算属性
const paginatedJobs = computed(() => {
  let list = [...filteredJobs.value]
  if (randomDirty.value) {
    for (let i = list.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[list[i], list[j]] = [list[j], list[i]]
    }
  }
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return list.slice(start, end)
})

const totalPages = computed(() => {
  return Math.ceil(filteredJobs.value.length / pageSize)
})

// 生命周期钩子
onMounted(async () => {
  const token = SessionManager.getCurrentToken()
  const user = SessionManager.getCurrentUserInfo()

  if (!token || !user) {
    router.push('/userlogin')
    return
  }

  try {
    currentUser.value = user
    const identity = user.identity
  } catch (error) {
    console.error('解析用户信息失败:', error)
    router.push('/userlogin')
  }

  // 检查是否是新注册用户
  const justRegistered = localStorage.getItem('justRegistered')
  if (justRegistered === 'true') {
    localStorage.removeItem('justRegistered')
    ElMessageBox.alert(
        '欢迎来到校园兼职平台！请在申请兼职前先完善自己的个人信息（学生认证、实名认证、电话和时间安排），以便更好地使用平台功能。',
        '温馨提示',
        {
          confirmButtonText: '确定',
          type: 'info'
        }
    )
  }

  // 获取数据
  await getAnnouncements()
  await getParttimeJobs()
})

// 跳转到排班管理（Profile.vue的一周安排标签）
const goToScheduleManagement = () => {
  const user = SessionManager.getCurrentUserInfo()
  if (!user) {
    ElMessage.error('请先登录')
    router.push('/userlogin')
    return
  }

  // 跳转到个人资料页面，并激活一周安排标签
  router.push({
    path: '/profile',
    query: {
      activeTab: 'schedule'
    }
  })
}

// 跳转到签到管理
const goToAttendanceManagement = () => {
  const user = SessionManager.getCurrentUserInfo()
  if (!user) {
    ElMessage.error('请先登录')
    router.push('/userlogin')
    return
  }

  // 跳转到商户签到管理页面
  router.push('/merchant-attendance')
}
// 判断是否今天
const isToday = (dateStr) => {
  const [y, m, d] = dateStr.split('-').map(Number)
  const today = new Date()
  return today.getFullYear() === y &&
         today.getMonth() + 1 === m &&
         today.getDate() === d
};

// 星期名称
const getDayName = (dayNum) => {
  const map = {1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日'};
  return map[dayNum] || '';
};

// 时间段中文名
const getSlotName = (slot) => {
  const map = {morning: '上午', afternoon: '下午', evening: '晚上'};
  return map[slot] || slot;
};
</script>

<template>
  <div class="parttime-container">
    <GeneralNav/>

    <!-- 顶部搜索栏 -->
    <div class="top-bar">
      <div class="logo"></div>
      <div class="search-container">
        <input
            v-model="searchKeyword"
            type="text"
            class="search-input"
            placeholder="搜索兼职岗位..."
            @keyup.enter="searchJobs"
        />
        <button class="search-btn" @click="searchJobs">搜索</button>
      </div>
      <div class="action-buttons">
        <!-- 学生签到入口 -->
        <button class="action-btn attendance-btn" @click="router.push('/attendance')" v-if="isStudent">
          <span class="btn-icon">📋</span>
          <span class="btn-text">签到</span>
        </button>
        <!-- 原有按钮 -->
        <button class="action-btn primary" @click="router.push('/find-students?tab=myJobs')" v-if="isMerchant || isAdmin">发布兼职</button>
        <button class="action-btn" @click="goToScheduleManagement()" v-if="!isStudent">排班管理</button>
        <button class="action-btn" @click="router.push('/admin')" v-if="isAdmin">管理后台</button>
        <button class="action-btn intelligent-match-btn" @click="openMatchingDialog">智能匹配</button>
        <!-- 签到管理按钮 -->
        <button class="action-btn attendance-btn" @click="goToAttendanceManagement()" v-if="isMerchant || isAdmin">
          <span class="btn-icon">📋</span>
          <span class="btn-text">签到管理</span>
        </button>
      </div>
    </div>

    <div class="main-content">
      <!-- 公告栏 -->
      <div class="announcement-section">
        <div class="section-header">
          <h2 class="section-title">📢 公告栏</h2>
          <button class="add-btn" @click="openJobDialog(null, true)" v-if="isAdmin">发布公告</button>
        </div>
        <div class="announcement-list">
          <div v-for="announcement in announcements" :key="announcement.id" class="announcement-item">
            <div class="announcement-header">
              <h3 class="announcement-title">{{ announcement.title }}</h3>
              <div class="announcement-actions">
                <span class="announcement-date">{{
                    announcement.createdAt ? new Date(announcement.createdAt).toLocaleString() : ''
                  }}</span>
                <button class="edit-btn" @click="openJobDialog(announcement, true)" v-if="isAdmin">编辑</button>
                <button class="delete-btn" @click="deleteJob(announcement)" v-if="isAdmin">删除</button>
              </div>
            </div>
            <p class="announcement-content">{{ announcement.content }}</p>
            <div class="announcement-footer">
              <span class="announcement-admin">发布者：管理员</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 兼职类型筛选 -->
      <div class="category-filter">
        <button
            :class="['category-btn', { active: selectedCategory === 'all' }]"
            @click="selectedCategory = 'all'; filterJobs()"
        >
          所有兼职工作
        </button>
        <button
            :class="['category-btn', { active: selectedCategory === 'work_study' }]"
            @click="selectedCategory = 'work_study'; filterJobs()"
        >
          勤工俭学专区
        </button>
        <button
            :class="['category-btn', { active: selectedCategory === 'campus_delivery' }]"
            @click="selectedCategory = 'campus_delivery'; filterJobs()"
        >
          校园送专区
        </button>
        <button
            :class="['category-btn', { active: selectedCategory === 'temporary' }]"
            @click="selectedCategory = 'temporary'; filterJobs()"
        >
          临时兼职专区
        </button>
        <button class="action-btn" @click="randomizeJobs">🔀 随机</button>
      </div>

      <!-- 图片预览对话框 -->
      <el-dialog
          v-model="previewDialogVisible"
          title="图片预览"
          width="800px"
          destroy-on-close
      >
        <img :src="getImageUrl(previewImageUrl)" class="preview-image"/>
      </el-dialog>

      <!-- 兼职列表 -->
      <div class="job-list-section">
        <h2 class="section-title">
          {{
            selectedCategory === 'all' ? '所有兼职工作' :
                selectedCategory === 'work_study' ? '勤工俭学专区' :
                selectedCategory === 'campus_delivery' ? '校园送专区' : '临时兼职专区'
          }}
        </h2>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <span class="loading-icon">⏳</span>
          <p class="loading-text">加载中...</p>
        </div>

        <!-- 兼职列表 -->
        <div v-else class="job-list">
          <div v-for="job in paginatedJobs" :key="job.id" class="job-item">
            <div class="job-image" v-if="job.imageUrl">
              <img :src="getImageUrl(job.imageUrl)" class="job-image-thumb" @click="previewImage(getImageUrl(job.imageUrl))"/>
            </div>
            <div class="job-content">
              <div class="job-header">
                <h3 class="job-title" @click="goToJobDetail(job.id)" style="cursor:pointer">{{ job.title }}</h3>
                <span class="job-salary">{{ job.salary }}</span>
              </div>
              <div class="job-info">
                <div class="job-info-item" v-if="job.userName">
                  <span class="info-label">发布者：</span>
                  <span class="info-value">{{ job.userName }}</span>
                </div>
                <div class="job-info-item">
                  <span class="info-label">工作地点：</span>
                  <span class="info-value">{{ job.address }}</span>
                </div>
                <div class="job-info-item">
                  <span class="info-label">工作时间：</span>
                  <span class="info-value">{{ formatJobTime(job.time) }}</span>
                </div>
                <div class="job-info-item">
                  <span class="info-label">招聘人数：</span>
                  <span class="info-value">{{ job.recruitmentLimit || '不限' }}人</span>
                </div>
                <div class="job-info-item">
                  <span class="info-label">状态：</span>
                  <span class="info-value" :class="{ 'status-full': job.status === '已招满' }">
                    {{ job.status === '进行中' ? '正在招人' : (job.status || '正在招人') }}
                  </span>
                </div>
                <!-- 新增：结算类型标签 -->
                <div class="job-info-item" v-if="getRemunerationText(job.remunerationType)">
                  <span class="info-label">结算方式：</span>
                  <span class="remuneration-tag">{{ getRemunerationText(job.remunerationType) }}</span>
                </div>
              </div>
              <div class="job-description">
                {{ job.content }}
              </div>
              <div class="job-actions">
                <button class="apply-btn" @click="applyJob(job)" v-if="isStudent && job.status !== '已招满'">立即申请
                </button>
                <button class="apply-btn disabled" v-if="isStudent && job.status === '已招满'">已招满</button>
                <button class="report-btn" @click="reportJob(job)" v-if="isStudent">举报</button>
                <!-- 管理员操作按钮 -->
                <button class="edit-btn" @click="editJob(job)" v-if="isAdmin">编辑</button>
                <button class="delete-btn" @click="deleteJobByAdmin(job)" v-if="isAdmin">删除</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页控件 -->
        <div v-if="filteredJobs.length > pageSize" class="pagination-container">
          <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="filteredJobs.length"
              layout="prev, pager, next, total"
              small
          />
        </div>

        <!-- 无数据提示 -->
        <div v-if="!loading && paginatedJobs.length === 0" class="empty-state">
          <div class="empty-icon">🔍</div>
          <p class="empty-text">暂无相关兼职岗位</p>
        </div>
      </div>
    </div>

    <!-- 发布/编辑对话框 -->
    <JobEditDialog v-model="dialogVisible" :job="editingItem" :is-announcement="isAnnouncementMode" @saved="onDialogSaved" />

    <!-- 普通申请兼职对话框（非日结） -->
    <el-dialog
        v-model="applyDialogVisible"
        title="申请兼职"
        width="600px"
    >
      <el-form label-width="100px">
        <el-form-item label="兼职信息">
          <div class="job-info-preview">
            <h4>{{ currentJob?.title }}</h4>
            <p>薪资：{{ currentJob?.salary }}</p>
            <p>工作地点：{{ currentJob?.address }}</p>
            <p>工作时间：{{ formatJobTime(currentJob?.time) }}</p>
          </div>
        </el-form-item>
        <el-form-item label="申请内容" required>
          <el-input
              v-model="applicationContent"
              type="textarea"
              rows="4"
              placeholder="请输入您的申请理由和相关经历"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 日结日期选择对话框 -->
    <!-- 日结日期选择对话框（优化显示） -->
    <el-dialog v-model="dailyApplyDialogVisible" title="选择工作日" width="750px">
      <div v-if="currentDailyJob">
        <h4 style="margin-bottom:8px;font-size:16px;">{{ currentDailyJob.title }}</h4>
        <p style="color:#666;margin-bottom:16px;">请选择本周需要工作的日期和时段（只能选择今天及之后的日期）：</p>

        <div class="daily-date-grid">
          <div v-for="item in availableDates" :key="item.date" class="daily-date-row">
            <div class="date-label-area">
              <span class="date-text">{{ item.date }}</span>
              <span class="weekday-tag" :class="{ 'is-today': isToday(item.date) }">
            {{ getDayName(item.dayOfWeek) }}
          </span>
            </div>
            <div class="slots-area">
              <el-checkbox-group v-model="selectedSlotsMap[item.date]">
                <el-checkbox
                    v-for="slot in item.slots"
                    :key="slot"
                    :label="slot"
                    class="slot-checkbox"
                >
                  {{ getSlotName(slot) }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </div>

        <div v-if="availableDates.length === 0" class="no-dates-hint">
          本周无可选择的工作日期
        </div>

        <el-input
            v-model="dailyApplyContent"
            type="textarea"
            placeholder="申请理由（选填）"
            style="margin-top:20px"
            :rows="3"
        />
      </div>
      <template #footer>
        <el-button @click="dailyApplyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDailyApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.parttime-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* 顶部搜索栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 30px;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 60px;
  z-index: 100;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #667eea;
}

.search-container {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 600px;
  margin: 0 50px;
}

.search-input {
  flex: 1;
  height: 40px;
  padding: 0 15px;
  border: 1px solid #eaeaea;
  border-radius: 4px 0 0 4px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s ease;
}

.search-input:focus {
  border-color: #667eea;
}

.search-btn {
  height: 40px;
  padding: 0 20px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
}

.search-btn:hover {
  background: #5a6fd8;
}

.intelligent-search-btn {
  height: 40px;
  padding: 0 20px;
  background: #f5f7fa;
  color: #667eea;
  border: 1px solid #667eea;
  border-radius: 4px;
  margin-left: 10px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.intelligent-search-btn:hover {
  background: #667eea;
  color: white;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.action-btn {
  height: 40px;
  padding: 0 20px;
  background: #f5f7fa;
  color: #333;
  border: 1px solid #eaeaea;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.action-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.action-btn.primary {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.action-btn.primary:hover {
  background: #5a6fd8;
}

.action-btn.intelligent-match-btn {
  background: #5643fa;
  color: white;
  border-color: #5643fa;
}

.action-btn.intelligent-match-btn:hover {
  background: #4836d1;
}

/* 主要内容 */
.main-content {
  flex: 1;
  padding: 30px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

/* 公告栏 */
.announcement-section {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 20px;
  margin-bottom: 30px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.add-btn {
  padding: 8px 16px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
}

.add-btn:hover {
  background: #5a6fd8;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.announcement-item {
  padding: 15px;
  border-left: 4px solid #667eea;
  background: #f9faff;
  border-radius: 0 4px 4px 0;
}

.announcement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.announcement-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  flex: 1;
}

.announcement-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.announcement-date {
  font-size: 12px;
  color: #999;
}

.edit-btn {
  padding: 4px 12px;
  background: #4ecdc4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background-color 0.3s ease;
}

.edit-btn:hover {
  background: #3cb4ac;
}

.delete-btn {
  padding: 4px 12px;
  background: #ff6b6b;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background-color 0.3s ease;
}

.delete-btn:hover {
  background: #ee5a52;
}

.announcement-content {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin: 0 0 10px 0;
}

.announcement-footer {
  font-size: 12px;
  color: #999;
  text-align: right;
}

/* 分类筛选 */
.category-filter {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.category-btn {
  padding: 10px 20px;
  background: white;
  border: 1px solid #eaeaea;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  transition: all 0.3s ease;
}

.category-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.category-btn.active {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

/* 兼职列表 */
.job-list-section {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 20px;
}

.job-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.job-item {
  padding: 20px;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  transition: all 0.3s ease;
  background: white;
  display: flex;
}

.job-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.job-image {
  margin-right: 20px;
  flex-shrink: 0;
}

.job-image-thumb {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.job-image-thumb:hover {
  transform: scale(1.05);
}

.job-content {
  flex: 1;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
}

.job-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  flex: 1;
  cursor: pointer;
  transition: color 0.3s;
}

.job-title:hover {
  color: #409eff;
}

.job-salary {
  font-size: 14px;
  font-weight: 600;
  color: #ff6b6b;
  background: #fff5f5;
  padding: 4px 12px;
  border-radius: 12px;
}

.status-full {
  color: #ff6b6b;
  font-weight: bold;
}

.job-info {
  margin-bottom: 15px;
}

.job-info-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
}

.info-label {
  width: 80px;
  color: #999;
}

.info-value {
  color: #666;
  flex: 1;
}

.remuneration-tag {
  display: inline-block;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  color: #1890ff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 4px;
}

.job-description {
  font-size: 14px;
  color: #666;
  line-height: 1.4;
  margin-bottom: 15px;
  height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.job-actions {
  text-align: right;
}

.apply-btn {
  padding: 8px 20px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
}

.apply-btn:hover {
  background: #5a6fd8;
}

.apply-btn.disabled {
  background: #f0f0f0;
  color: #909399;
  cursor: not-allowed;
}

.apply-btn.disabled:hover {
  background: #f0f0f0;
}

.report-btn {
  padding: 8px 20px;
  background: #ff6b6b;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
  margin-left: 10px;
}

.report-btn:hover {
  background: #ee5a52;
}

.job-actions .edit-btn {
  padding: 8px 16px;
  background: #4ecdc4;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
  margin-left: 10px;
}

.job-actions .edit-btn:hover {
  background: #3cb4ac;
}

.job-actions .delete-btn {
  padding: 8px 16px;
  background: #ff6b6b;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s ease;
  margin-left: 10px;
}

.job-actions .delete-btn:hover {
  background: #ee5a52;
}

/* 加载状态 */
.loading-state {
  text-align: center;
  padding: 60px 20px;
  color: #999;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 20px;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 16px;
  margin: 0;
}

/* 图片预览样式 */
.preview-image {
  width: 100%;
  height: auto;
  max-height: 600px;
  object-fit: contain;
  border-radius: 4px;
}

/* 无数据状态 */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.empty-text {
  font-size: 16px;
  margin: 0;
}

/* 申请兼职对话框样式 */
.job-info-preview {
  background: #f9faff;
  padding: 15px;
  border-radius: 8px;
  border-left: 4px solid #667eea;
  margin-bottom: 20px;
}

.job-info-preview h4 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}

.job-info-preview p {
  margin: 5px 0;
  font-size: 14px;
  color: #666;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .top-bar {
    flex-direction: column;
    gap: 10px;
    padding: 15px;
  }

  .search-container {
    width: 100%;
    margin: 0;
  }

  .job-list {
    grid-template-columns: 1fr;
  }

  .main-content {
    padding: 15px;
  }

  .job-item {
    flex-direction: column;
  }

  .job-image {
    margin-right: 0;
    margin-bottom: 15px;
  }

  .job-image-thumb {
    width: 100%;
    height: 150px;
  }
}

.attendance-btn {
  background: #67c23a;
  color: white;
  border-color: #67c23a;
}

.attendance-btn:hover {
  background: #85ce61;
  color: white;
}

/* 日结日期选择器样式 */
.daily-date-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 350px;
  overflow-y: auto;
  padding-right: 8px;
}

.daily-date-row {
  display: flex;
  align-items: center;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 16px;
  background: #fafafa;
  transition: all 0.3s;
}

.daily-date-row:hover {
  border-color: #667eea;
  background: #f5f7ff;
}

.date-label-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 90px;
  margin-right: 20px;
  padding-right: 16px;
  border-right: 1px solid #e4e7ed;
}

.date-text {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.weekday-tag {
  display: inline-block;
  background: #e6f7ff;
  color: #1890ff;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid #91d5ff;
}

.weekday-tag.is-today {
  background: #fff7e6;
  color: #fa8c16;
  border-color: #ffd591;
}

.slots-area {
  flex: 1;
}

.slot-checkbox {
  margin-right: 16px;
}

.no-dates-hint {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 14px;
}

/* 滚动条样式 */
.daily-date-grid::-webkit-scrollbar {
  width: 6px;
}

.daily-date-grid::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}

.daily-date-grid::-webkit-scrollbar-track {
  background: #f0f0f0;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding: 16px 0;
}
</style>