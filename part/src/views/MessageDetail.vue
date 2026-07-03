<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import GeneralNav from '../nav/GeneralNav.vue'
import { ElMessage, ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()
const route = useRoute()

// 消息详情
const message = ref(null)
const loading = ref(true)
const processing = ref(false)

// 申请状态
const applicationStatus = ref('')
const applicationContent = ref('')
const studentTimeAvailability = ref('')

// 时间对比相关
const parsedStudentTimeSlots = ref([])
const parsedJobTimeSlots = ref([])
const jobTimeData = ref(null)
const filledSlotsMap = ref({})  // { "1_morning": true, "2_afternoon": true ... } 已被占据的时段

// 日结选中日期（用于过滤兼职时间槽）
const pendingSelectedDates = ref(null)

// 回复相关
const replyDialogVisible = ref(false)
const replyContent = ref('')

// 申请处理相关
const acceptDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const acceptForm = ref({
  phone: '',
  address: '',
  message: ''
})
const rejectForm = ref({
  message: ''
})

// 计算属性：判断申请是否已处理
const isApplicationProcessed = computed(() => {
  if (applicationStatus.value === 'accepted' || applicationStatus.value === 'rejected') {
    return true
  }
  if (!message.value || !message.value.content) return false
  const content = message.value.content
  return content.includes('已接受') || content.includes('已拒绝') ||
      content.includes('商户留言') || content.includes('拒绝原因')
})

// 计算属性：获取处理状态
const processingStatus = computed(() => {
  if (applicationStatus.value === 'accepted') return '已接受'
  if (applicationStatus.value === 'rejected') return '已拒绝'
  if (message.value?.content?.includes('已接受')) return '已接受'
  if (message.value?.content?.includes('已拒绝')) return '已拒绝'
  return ''
})

// 商户留言和拒绝原因
const merchantMessageText = ref('')

const merchantMessage = computed(() => {
  if (merchantMessageText.value) return merchantMessageText.value
  if (!message.value?.content) return ''
  const content = message.value.content
  const acceptMatch = content.match(/商户留言：(.+)/)
  const rejectMatch = content.match(/拒绝原因：(.+)/)
  if (acceptMatch) return acceptMatch[1]
  if (rejectMatch) return rejectMatch[1]
  return ''
})

// 当前用户信息
const currentUser = ref(null)

const isStudent = computed(() => currentUser.value?.identity === '学生')
const isMerchant = computed(() => currentUser.value?.identity !== '学生' && currentUser.value?.identity !== '管理员')

const resignRequested = computed(() => {
  return message.value?.content?.includes('申请辞去')
})
const resignApproved = computed(() => {
  return message.value?.content?.includes('已同意') || message.value?.content?.includes('已取消') || message.value?.content?.includes('【已同意】')
})
const resignProcessedLocal = ref(false)
const lastMessageId = ref(null)

// ---------- 工具函数 ----------
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
    'work_cancelled': '工作取消',
    'urgent_taken': '急招接取'
  }
  return typeMap[type] || '通知'
}

const formatTime = (time) => {
  if (!time) return ''
  try {
    const date = new Date(time)
    if (isNaN(date.getTime())) return time
    return date.toLocaleString()
  } catch (error) {
    console.error('时间格式化失败:', error)
    return time
  }
}

const formatMessageContent = (content) => {
  if (!content) return ''
  const urlRegex = /(https?:\/\/[^\s]+|\/[^\s]+)/g
  return content.replace(urlRegex, (url) => {
    return `<a href="${url}" target="_blank" style="color: #409EFF; text-decoration: underline;">${url}</a>`
  })
}

const parseLocalDate = (dateStr) => {
  const parts = dateStr.split('-')
  return new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
}

const getDayName = (dayNum) => {
  const map = {1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日'}
  return map[dayNum] || ''
}

const getPeriodName = (period) => {
  const map = {'morning': '上午', 'afternoon': '下午', 'evening': '晚上', '1': '上午', '2': '下午', '3': '晚上'}
  return map[period] || period
}

const normalizePeriod = (period) => {
  if (typeof period === 'number') {
    if (period === 1) return 'morning'
    else if (period === 2) return 'afternoon'
    else if (period === 3) return 'evening'
  }
  return period
}

// 需要显示的星期列（动态）
const displayDays = computed(() => {
  if (parsedJobTimeSlots.value.length === 0) return []
  const days = [...new Set(parsedJobTimeSlots.value.map(s => s.day))]
  return days.sort((a, b) => a - b)
})

// 获取时间段状态（匹配/不匹配/无需求/已被占据）
const getTimeSlotStatus = (day, period) => {
  const normalizedPeriod = normalizePeriod(period)
  const isJobSlot = parsedJobTimeSlots.value.some(slot => slot.day === day && normalizePeriod(slot.period) === normalizedPeriod)
  if (!isJobSlot) return 'na'
  // 检查是否已被其他学生占据
  const slotKey = day + '_' + normalizedPeriod
  if (filledSlotsMap.value && filledSlotsMap.value[slotKey]) return 'taken'
  const isMatched = parsedStudentTimeSlots.value.some(slot => slot.day === day && normalizePeriod(slot.period) === normalizedPeriod)
  return isMatched ? 'matched' : 'unmatched'
}

// 匹配统计
const matchedSlotsCount = computed(() => {
  return parsedJobTimeSlots.value.filter(jobSlot =>
      parsedStudentTimeSlots.value.some(studentSlot =>
          studentSlot.day === jobSlot.day && normalizePeriod(studentSlot.period) === normalizePeriod(jobSlot.period)
      )
  ).length
})

const unmatchedSlotsCount = computed(() => parsedJobTimeSlots.value.length - matchedSlotsCount.value)

const matchRate = computed(() => {
  if (parsedJobTimeSlots.value.length === 0) return 0
  return Math.round((matchedSlotsCount.value / parsedJobTimeSlots.value.length) * 100)
})

// ---------- 数据加载 ----------
const markAsRead = async (messageId) => {
  try {
    await axios.post('/api/message/read', null, {params: {id: messageId}})
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

const parseDailyStudentSlots = (selectedDates) => {
  if (!Array.isArray(selectedDates)) return []
  return selectedDates.map(d => {
    const localDate = parseLocalDate(d.date)
    const day = localDate.getDay() || 7 // 周日返回 7
    return {
      day: day,
      dayName: getDayName(day),
      period: d.timeSlot,
      periodName: getPeriodName(d.timeSlot)
    }
  })
}

const parseStudentTimeSlots = (timeAvailability) => {
  const slots = []
  if (!timeAvailability) return slots
  try {
    const arr = typeof timeAvailability === 'string' ? JSON.parse(timeAvailability) : timeAvailability
    if (!Array.isArray(arr)) return slots
    arr.forEach(item => {
      const normalizedPeriod = normalizePeriod(item.period)
      slots.push({
        day: item.day,
        period: normalizedPeriod,
        dayName: getDayName(item.day),
        periodName: getPeriodName(normalizedPeriod)
      })
    })
  } catch (error) {
    console.error('解析学生时间安排失败:', error)
  }
  return slots
}

const parseJobTimeSlots = (timeStr) => {
  const slots = []
  if (!timeStr) return slots
  try {
    let timeObj
    if (typeof timeStr === 'string') {
      timeObj = JSON.parse(timeStr)
    } else {
      timeObj = timeStr
    }
    if (timeObj && timeObj.timeSlots && Array.isArray(timeObj.timeSlots)) {
      timeObj.timeSlots.forEach(slot => {
        const normalizedPeriod = normalizePeriod(slot.period)
        slots.push({
          day: slot.day,
          period: normalizedPeriod,
          dayName: getDayName(slot.day),
          periodName: getPeriodName(normalizedPeriod)
        })
      })
    }
  } catch (error) {
    console.error('解析兼职时间失败:', error)
  }
  return slots
}

const fetchApplicationStatus = async (applicationId) => {
  if (!applicationId) return;
  try {
    const response = await axios.get(`/api/application/${applicationId}`);
    if (response.data.success && response.data.data) {
      const data = response.data.data;
      console.log('获取到的申请详情:', JSON.stringify(data, null, 2));
      applicationStatus.value = data.status;

      if (data.applicationContent) {
        applicationContent.value = data.applicationContent;
      }
      if (data.timeAvailability) {
        studentTimeAvailability.value = data.timeAvailability;
      }

      // 处理日结选中日期：优先使用 selectedDates（小驼峰），否则 selected_dates（下划线）
      const selectedDatesRaw = data.selectedDates || data.selected_dates;
      if (selectedDatesRaw) {
        try {
          // 可能是 JSON 字符串，也可能是已解析的数组
          const selectedDates = typeof selectedDatesRaw === 'string'
              ? JSON.parse(selectedDatesRaw)
              : selectedDatesRaw;
          if (Array.isArray(selectedDates) && selectedDates.length > 0) {
            pendingSelectedDates.value = selectedDates;
            parsedStudentTimeSlots.value = parseDailyStudentSlots(selectedDates);
            console.log('成功解析日结选中日期:', selectedDates);
          } else {
            // 不是数组或为空，回退到时间偏好解析
            pendingSelectedDates.value = null;
            if (data.timeAvailability) {
              parsedStudentTimeSlots.value = parseStudentTimeSlots(data.timeAvailability);
            } else {
              parsedStudentTimeSlots.value = [];
            }
          }
        } catch (e) {
          console.error('解析日结选中日期失败:', e, '原始数据:', selectedDatesRaw);
          pendingSelectedDates.value = null;
          if (data.timeAvailability) {
            parsedStudentTimeSlots.value = parseStudentTimeSlots(data.timeAvailability);
          } else {
            parsedStudentTimeSlots.value = [];
          }
        }
      } else {
        // 没有日结日期，使用学生时间偏好
        pendingSelectedDates.value = null;
        if (data.timeAvailability) {
          parsedStudentTimeSlots.value = parseStudentTimeSlots(data.timeAvailability);
        } else {
          parsedStudentTimeSlots.value = [];
        }
      }

      if (data.status === 'accepted' && data.message) {
        merchantMessageText.value = data.message;
      } else if (data.status === 'rejected' && data.message) {
        merchantMessageText.value = data.message;
      }
    }
  } catch (error) {
    console.error('获取申请状态失败:', error);
  }
};

const fetchJobTimeData = async (relatedId) => {
  if (!relatedId) return
  try {
    let jobId = relatedId
    let jobData = null

    // 优先尝试作为兼职ID
    try {
      const jobResponse = await axios.get(`/api/job/${relatedId}`)
      if (jobResponse.data.success && jobResponse.data.data) {
        jobData = jobResponse.data.data
        jobId = relatedId
      }
    } catch (error) {
      console.log('不是有效的兼职ID，尝试作为申请ID处理')
    }

    // 如果不是兼职ID，则作为申请ID获取兼职ID
    if (!jobData) {
      try {
        const appResponse = await axios.get(`/api/application/${relatedId}`)
        if (appResponse.data.success && appResponse.data.data) {
          const application = appResponse.data.data
          jobId = application.jobId
          const jobResponse2 = await axios.get(`/api/job/${jobId}`)
          if (jobResponse2.data.success && jobResponse2.data.data) {
            jobData = jobResponse2.data.data
          }
        }
      } catch (error) {
        console.error('获取申请详情失败:', error)
      }
    }

    if (jobData) {
      jobTimeData.value = jobData
      parsedJobTimeSlots.value = parseJobTimeSlots(jobData.time)
      // 解析已被占据的时段
      const filled = {}
      if (jobData.requirements && Array.isArray(jobData.requirements)) {
        jobData.requirements.forEach(req => {
          if (req.filled_count && req.filled_count > 0) {
            const key = req.day_of_week + '_' + (req.time_slot || '')
            filled[key] = true
          }
        })
      }
      filledSlotsMap.value = filled
    } else {
      parsedJobTimeSlots.value = []
      filledSlotsMap.value = {}
    }
  } catch (error) {
    console.error('获取兼职时间数据失败:', error)
    parsedJobTimeSlots.value = []
  }
}

const getMessageDetail = async () => {
  const messageId = route.params.id
  loading.value = true
  if (lastMessageId.value !== messageId) {
    resignProcessedLocal.value = false
    lastMessageId.value = messageId
  }
  try {
    const response = await axios.get(`/api/message/${messageId}`)
    if (response.data.success) {
      message.value = response.data.data

      // 标记为已读
      markAsRead(messageId)

      // 如果是申请消息，获取申请状态和兼职时间数据
      if (message.value.type === 'apply_job' && message.value.relatedId) {
        console.log('这是申请消息，开始获取申请状态和时间数据')
        // 同时发起两个请求
        await Promise.all([
          fetchApplicationStatus(message.value.relatedId),
          fetchJobTimeData(message.value.relatedId)
        ])

        // 两个数据都拿到后，如果是日结申请，统一过滤兼职时间槽
        filterJobTimeSlotsForDaily()
      }
    } else {
      ElMessage.error('获取消息详情失败')
    }
  } catch (error) {
    console.error('获取消息详情失败:', error)
    ElMessage.error('获取消息详情失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 新增统一过滤函数（放在 getMessageDetail 之后）
const filterJobTimeSlotsForDaily = () => {
  console.log('开始过滤，pendingSelectedDates:', pendingSelectedDates.value)
  if (!pendingSelectedDates.value || parsedJobTimeSlots.value.length === 0) return
  const before = parsedJobTimeSlots.value.length
  parsedJobTimeSlots.value = parsedJobTimeSlots.value.filter(slot => {
    return pendingSelectedDates.value.some(d => {
      const localDate = parseLocalDate(d.date)
      const dow = localDate.getDay() || 7
      return dow === slot.day && d.timeSlot === slot.period
    })
  })
  console.log(`时间槽过滤完成: ${before} → ${parsedJobTimeSlots.value.length}`)
}

// ---------- 操作 ----------
const handleApplication = (status) => {
  if (!message.value.relatedId) {
    ElMessage.error('缺少相关信息')
    return
  }
  if (status === 'accepted') {
    acceptDialogVisible.value = true
  } else if (status === 'rejected') {
    rejectDialogVisible.value = true
  }
}

const submitAcceptApplication = async (forceAccept = false) => {
  if (!acceptForm.value.phone || !acceptForm.value.address) {
    ElMessage.warning('请填写电话和工作地址')
    return
  }
  if (!currentUser.value || !currentUser.value.id) {
    ElMessage.error('用户未登录')
    router.push('/userlogin')
    return
  }
  if (!message.value || !message.value.relatedId) {
    ElMessage.error('缺少相关申请信息')
    return
  }

  processing.value = true
  try {
    const response = await axios.put('/api/application/status', {
      id: message.value.relatedId,
      status: 'accepted',
      operatorId: currentUser.value.id,
      phone: acceptForm.value.phone,
      address: acceptForm.value.address,
      message: acceptForm.value.message,
      forceAccept: forceAccept
    })

    if (response.data.success) {
      ElMessage.success('已接受申请')
      acceptDialogVisible.value = false
      applicationStatus.value = 'accepted'
      if (acceptForm.value.message) {
        merchantMessageText.value = acceptForm.value.message
      }
      await getMessageDetail()
    } else {
      const errorMsg = response.data.message || ''
      if (errorMsg.startsWith('TIME_CONFLICT')) {
        let conflictInfo = errorMsg
        if (errorMsg.startsWith('TIME_CONFLICT_PREFERENCE:')) {
          conflictInfo = errorMsg.replace('TIME_CONFLICT_PREFERENCE:', '').trim()
        } else if (errorMsg.startsWith('TIME_CONFLICT_SCHEDULE:')) {
          conflictInfo = errorMsg.replace('TIME_CONFLICT_SCHEDULE:', '').trim()
        } else if (errorMsg.startsWith('TIME_CONFLICT:')) {
          conflictInfo = errorMsg.replace('TIME_CONFLICT:', '').trim()
        }
        ElMessageBox.confirm(
            `该学生的时间安排与本次兼职时间存在冲突！\n\n冲突时段：${conflictInfo}\n\n是否仍要强行接受申请？`,
            '时间冲突提示',
            {
              confirmButtonText: '仍要接受',
              cancelButtonText: '取消',
              type: 'warning'
            }
        ).then(async () => {
          await submitAcceptApplication(true)
        }).catch(() => {
        })
      } else {
        ElMessage.error(errorMsg || '操作失败')
      }
    }
  } catch (error) {
    console.error('处理申请失败:', error)
    ElMessage.error('处理申请失败')
  } finally {
    processing.value = false
  }
}

const submitRejectApplication = async () => {
  if (!currentUser.value || !currentUser.value.id) {
    ElMessage.error('用户未登录')
    router.push('/userlogin')
    return
  }
  if (!message.value || !message.value.relatedId) {
    ElMessage.error('缺少相关申请信息')
    return
  }

  processing.value = true
  try {
    const response = await axios.put('/api/application/status', {
      id: message.value.relatedId,
      status: 'rejected',
      operatorId: currentUser.value.id,
      message: rejectForm.value.message
    })
    if (response.data.success) {
      ElMessage.success('已拒绝申请')
      rejectDialogVisible.value = false
      applicationStatus.value = 'rejected'
      if (rejectForm.value.message) {
        merchantMessageText.value = rejectForm.value.message
      }
      await getMessageDetail()
    } else {
      ElMessage.error(response.data.message)
    }
  } catch (error) {
    console.error('处理申请失败:', error)
    ElMessage.error('处理申请失败')
  } finally {
    processing.value = false
  }
}

const handleResignApprove = async () => {
  if (!message.value || !message.value.senderId || !message.value.relatedId) {
    ElMessage.error('缺少相关信息')
    return
  }
  try {
    await ElMessageBox.confirm(
        '同意辞职后，该学生的所有工作安排将被取消，名额将释放。确认同意？',
        '确认同意辞职',
        { type: 'warning', confirmButtonText: '确认同意', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  processing.value = true
  try {
    const res = await axios.post('/api/job/approve-resign', {
      studentId: message.value.senderId,
      jobId: message.value.relatedId,
      merchantId: currentUser.value.id
    })
    if (res.data.success) {
      ElMessage.success('已同意辞职，时段已释放')
      resignProcessedLocal.value = true
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch {
    ElMessage.error('操作失败')
  } finally {
    processing.value = false
  }
}

const replyMessage = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }

  processing.value = true
  try {
    let receiverId
    if (message.value.type === 'contact_student' || message.value.type === 'contact_merchant' || message.value.type === 'reply') {
      receiverId = message.value.senderId
    } else {
      receiverId = message.value.senderId
    }

    const response = await axios.post('/api/message/send', {
      senderId: currentUser.value.id,
      receiverId: receiverId,
      type: 'reply',
      content: `回复：${replyContent.value}`,
      relatedId: message.value.relatedId
    })

    if (response.data.success) {
      ElMessage.success('回复成功')
      replyDialogVisible.value = false
      replyContent.value = ''
    } else {
      ElMessage.error('回复失败')
    }
  } catch (error) {
    console.error('回复消息失败:', error)
    ElMessage.error('回复失败，请稍后重试')
  } finally {
    processing.value = false
  }
}

const goToJobDetail = (jobId) => {
  router.push(`/job/${jobId}`)
}

const goToStudentDetail = () => {
  router.push('/find-students')
}

// 生命周期
onMounted(async () => {
  const user = SessionManager.getCurrentUserInfo()
  if (user) {
    currentUser.value = user
  } else {
    const userStr = localStorage.getItem('userInfo')
    if (userStr) {
      try {
        currentUser.value = JSON.parse(userStr)
      } catch (error) {
        console.error('解析用户信息失败:', error)
      }
    } else {
      router.push('/userlogin')
      return
    }
  }
  await getMessageDetail()
})
</script>

<template>
  <div class="message-detail-container">
    <GeneralNav/>

    <div class="main-content">
      <!-- 顶部导航 -->
      <div class="message-header">
        <button class="back-btn" @click="router.push('/message/list')">
          <i class="el-icon-arrow-left"></i> 返回消息列表
        </button>
        <h1 class="message-title">{{ formatMessageType(message?.type) }}</h1>
        <div class="time-info">{{ message?.createdAt ? formatTime(message.createdAt) : '' }}</div>
      </div>

      <!-- 消息内容 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-icon">⏳</span>
        <p class="loading-text">加载中...</p>
      </div>

      <div v-else-if="message" class="message-content">
        <!-- 兼职申请消息 -->
        <div v-if="message.type === 'apply_job'" class="message-card">
          <div class="card-header">
            <span class="badge apply-badge">申请消息</span>
            <h3 class="card-title">学生申请了您的兼职</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">申请人信息</h4>
              <div class="info-item">
                <span class="info-label">姓名：</span>
                <span class="info-value">{{ message.content.match(/(\w+) 申请了/)?.[1] || '未知' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">申请时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="info-section">
              <h4 class="section-title">申请信息</h4>
              <div class="info-item" v-if="applicationContent">
                <span class="info-label">申请理由：</span>
                <span class="info-value">{{ applicationContent }}</span>
              </div>
            </div>

            <!-- 时间对比（动态列） -->
            <div class="info-section" v-if="parsedJobTimeSlots.length > 0 && message.relatedId">
              <h4 class="section-title">时间匹配对比</h4>
              <div v-if="!jobTimeData" class="loading-time">
                <span class="loading-icon">⏳</span>
                <p class="loading-text">正在加载兼职时间数据...</p>
              </div>
              <div v-else class="time-comparison-section">
                <div class="comparison-container">
                  <!-- 学生时间安排（左侧） -->
                  <div class="student-time-section">
                    <h5 class="comparison-title">学生选择的时间</h5>
                    <div class="time-slots-grid">
                      <div
                          class="time-slot"
                          v-for="(slot, idx) in parsedStudentTimeSlots"
                          :key="'stu-' + idx"
                      >
                        {{ slot.dayName }} {{ slot.periodName }}
                      </div>
                      <div v-if="parsedStudentTimeSlots.length === 0" class="no-slots">
                        未选择时间
                      </div>
                    </div>
                  </div>

                  <!-- 兼职时间需求（右侧） -->
                  <div class="job-time-section">
                    <h5 class="comparison-title">兼职需求时间</h5>
                    <div class="time-slots-grid">
                      <div
                          class="time-slot"
                          v-for="(slot, idx) in parsedJobTimeSlots"
                          :key="'job-' + idx"
                      >
                        {{ slot.dayName }} {{ slot.periodName }}
                      </div>
                      <div v-if="parsedJobTimeSlots.length === 0" class="no-slots">
                        {{ jobTimeData ? '未设置工作时间或时间格式无法识别' : '正在加载工作时间...' }}
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 时间对比表格（动态列） -->
                <div class="time-comparison-table">
                  <div class="table-header">
                    <div class="header-cell">时间段</div>
                    <div class="header-cell" v-for="day in displayDays" :key="day">
                      {{ getDayName(day) }}
                    </div>
                  </div>

                  <!-- 上午 -->
                  <div class="table-row">
                    <div class="row-header">上午<br>9:00-13:00</div>
                    <div v-for="day in displayDays" :key="day" class="time-cell">
                      <div v-if="getTimeSlotStatus(day, 'morning') === 'matched'" class="slot-matched">
                        ✓
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'morning') === 'taken'" class="slot-taken">
                        ●
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'morning') === 'unmatched'" class="slot-unmatched">
                        ✗
                      </div>
                      <div v-else class="slot-na">
                        -
                      </div>
                    </div>
                  </div>

                  <!-- 下午 -->
                  <div class="table-row">
                    <div class="row-header">下午<br>14:00-18:00</div>
                    <div v-for="day in displayDays" :key="day" class="time-cell">
                      <div v-if="getTimeSlotStatus(day, 'afternoon') === 'matched'" class="slot-matched">
                        ✓
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'afternoon') === 'taken'" class="slot-taken">
                        ●
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'afternoon') === 'unmatched'" class="slot-unmatched">
                        ✗
                      </div>
                      <div v-else class="slot-na">
                        -
                      </div>
                    </div>
                  </div>

                  <!-- 晚上 -->
                  <div class="table-row">
                    <div class="row-header">晚上<br>18:00-22:00</div>
                    <div v-for="day in displayDays" :key="day" class="time-cell">
                      <div v-if="getTimeSlotStatus(day, 'evening') === 'matched'" class="slot-matched">
                        ✓
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'evening') === 'taken'" class="slot-taken">
                        ●
                      </div>
                      <div v-else-if="getTimeSlotStatus(day, 'evening') === 'unmatched'" class="slot-unmatched">
                        ✗
                      </div>
                      <div v-else class="slot-na">
                        -
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 图例 -->
                <div class="comparison-legend">
                  <div class="legend-item">
                    <span class="match-indicator matched">✓</span>
                    <span class="legend-text">匹配（学生可用 &amp; 兼职需要）</span>
                  </div>
                  <div class="legend-item">
                    <span class="match-indicator unmatched">✗</span>
                    <span class="legend-text">未选择（兼职需要但学生未勾选）</span>
                  </div>
                  <div class="legend-item">
                    <span class="match-indicator taken">●</span>
                    <span class="legend-text">已被选（该时段已被其他学生占据）</span>
                  </div>
                </div>

                <!-- 匹配统计 -->
                <div class="match-statistics">
                  <div class="stat-item">
                    <span class="stat-label">兼职总时段：</span>
                    <span class="stat-value">{{ parsedJobTimeSlots.length }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">匹配时段：</span>
                    <span class="stat-value matched">{{ matchedSlotsCount }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">不匹配时段：</span>
                    <span class="stat-value unmatched">{{ unmatchedSlotsCount }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">匹配率：</span>
                    <span class="stat-value">{{ matchRate }}%</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="action-section" v-if="!isApplicationProcessed">
              <el-button
                  type="primary"
                  @click="handleApplication('accepted')"
                  :loading="processing"
              >
                接受申请
              </el-button>
              <el-button
                  type="danger"
                  @click="handleApplication('rejected')"
                  :loading="processing"
              >
                拒绝申请
              </el-button>
              <div class="action-buttons" style="margin-top: 20px;">
                <el-button @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
              </div>
            </div>
            <div class="processed-section" v-if="isApplicationProcessed">
              <div class="processed-info"
                   :class="{'accepted': processingStatus === '已接受', 'rejected': processingStatus === '已拒绝'}">
                <div class="status-header">
                  <span class="status-badge">{{ processingStatus }}</span>
                  <span class="status-time">{{ formatTime(message.createdAt) }}</span>
                </div>
                <div v-if="merchantMessage" class="merchant-message">
                  <h5 class="message-label">{{ processingStatus === '已接受' ? '商户留言' : '拒绝原因' }}</h5>
                  <p class="message-content">{{ merchantMessage }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 邀请参与消息 -->
        <div v-else-if="message.type === 'invite_student'" class="message-card">
          <div class="card-header">
            <span class="badge invite-badge">邀请消息</span>
            <h3 class="card-title">您被邀请参与兼职</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">邀请信息</h4>
              <div class="info-item">
                <span class="info-label">邀请时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">邀请内容</h4>
              <div class="message-text" v-html="formatMessageContent(message.content)"></div>
            </div>

            <div class="action-section">
              <el-button type="primary" @click="goToJobDetail(message.relatedId)">查看详情</el-button>
              <el-button @click="replyDialogVisible = true">回复消息</el-button>
            </div>
          </div>
        </div>

        <!-- 联系消息 -->
        <div v-else-if="message.type === 'contact_student'" class="message-card">
          <div class="card-header">
            <span class="badge contact-badge">联系消息</span>
            <h3 class="card-title">商户联系您</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">联系信息</h4>
              <div class="info-item">
                <span class="info-label">联系时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">联系内容</h4>
              <div class="message-text" v-html="formatMessageContent(message.content)"></div>
            </div>

            <div class="action-section">
              <el-button v-if="message.relatedId" @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
              <el-button type="primary" @click="replyDialogVisible = true">回复消息</el-button>
            </div>
          </div>
        </div>

        <!-- 学生联系商户消息 -->
        <div v-else-if="message.type === 'contact_merchant'" class="message-card">
          <div class="card-header">
            <span class="badge contact-badge">联系消息</span>
            <h3 class="card-title">学生联系您</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">联系信息</h4>
              <div class="info-item">
                <span class="info-label">联系时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">联系内容</h4>
              <div class="message-text" v-html="formatMessageContent(message.content)"></div>
            </div>

            <div class="action-section">
              <el-button v-if="message.relatedId" @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
              <el-button type="primary" @click="replyDialogVisible = true">回复消息</el-button>
            </div>
          </div>
        </div>

        <!-- 辞职申请消息 -->
        <div v-else-if="message.type === 'student_resign'" class="message-card">
          <div class="card-header">
            <span class="badge status-badge">辞职消息</span>
            <h3 class="card-title">{{ resignApproved ? '辞职结果通知' : '学生申请辞职' }}</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">消息内容</h4>
              <div class="info-item">
                <span class="info-label">发送时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>
            <div class="content-section">
              <div class="message-text">{{ message.content }}</div>
            </div>

            <div class="action-section" v-if="isMerchant && resignRequested && !resignApproved && !resignProcessedLocal">
              <el-button
                  type="primary"
                  @click="handleResignApprove"
                  :loading="processing"
              >同意辞职</el-button>
              <el-button
                  @click="replyDialogVisible = true"
              >回复消息</el-button>
            </div>
            <div class="action-section" v-else-if="!isMerchant && !resignApproved && !resignProcessedLocal">
              <el-button @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
              <el-button type="primary" @click="replyDialogVisible = true">回复消息</el-button>
            </div>
            <div class="processed-section" v-if="resignApproved || resignProcessedLocal">
              <div class="processed-info accepted">
                <div class="status-header">
                  <span class="status-badge">{{ isMerchant ? '已同意辞职' : '辞职已通过' }}</span>
                  <span class="status-time">{{ formatTime(message.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 申请状态消息 -->
        <div v-else-if="message.type === 'application_status'" class="message-card">
          <div class="card-header">
            <span class="badge status-badge">状态消息</span>
            <h3 class="card-title">申请状态更新</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">状态信息</h4>
              <div class="info-item">
                <span class="info-label">更新时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">状态内容</h4>
              <div class="message-text">{{ message.content }}</div>
            </div>

            <div class="action-section">
              <el-button @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
            </div>
          </div>
        </div>

        <!-- 回复消息 -->
        <div v-else-if="message.type === 'reply'" class="message-card">
          <div class="card-header">
            <span class="badge contact-badge">回复消息</span>
            <h3 class="card-title">{{ message.senderName }}回复您</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">回复信息</h4>
              <div class="info-item">
                <span class="info-label">回复时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">回复内容</h4>
              <div class="message-text" v-html="formatMessageContent(message.content)"></div>
            </div>

            <div class="action-section">
              <el-button v-if="message.relatedId" @click="goToJobDetail(message.relatedId)">查看兼职详情</el-button>
              <el-button type="primary" @click="replyDialogVisible = true">回复消息</el-button>
            </div>
          </div>
        </div>

        <!-- 系统通知 -->
        <div v-else class="message-card">
          <div class="card-header">
            <span class="badge system-badge">系统通知</span>
            <h3 class="card-title">系统通知</h3>
          </div>
          <div class="card-body">
            <div class="info-section">
              <h4 class="section-title">通知信息</h4>
              <div class="info-item">
                <span class="info-label">通知时间：</span>
                <span class="info-value">{{ formatTime(message.createdAt) }}</span>
              </div>
            </div>

            <div class="content-section">
              <h4 class="section-title">通知内容</h4>
              <div class="message-text">{{ message.content }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">📭</div>
        <p class="empty-text">消息不存在</p>
        <el-button @click="router.push('/message/list')">返回消息列表</el-button>
      </div>
    </div>

    <!-- 回复消息对话框 -->
    <el-dialog v-model="replyDialogVisible" title="回复消息" width="500px">
      <el-form>
        <el-form-item label="回复内容">
          <el-input
              v-model="replyContent"
              type="textarea"
              :rows="4"
              placeholder="请输入回复内容"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            @click="replyMessage"
            :loading="processing"
        >
          发送回复
        </el-button>
      </template>
    </el-dialog>

    <!-- 接受申请对话框 -->
    <el-dialog v-model="acceptDialogVisible" title="接受申请" width="500px">
      <el-form>
        <el-form-item label="联系电话" required>
          <el-input
              v-model="acceptForm.phone"
              placeholder="请输入您的联系电话"
          ></el-input>
        </el-form-item>
        <el-form-item label="工作地址" required>
          <el-input
              v-model="acceptForm.address"
              placeholder="请输入工作地址"
          ></el-input>
        </el-form-item>
        <el-form-item label="留言">
          <el-input
              v-model="acceptForm.message"
              type="textarea"
              :rows="3"
              placeholder="请输入给学生的留言"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            @click="submitAcceptApplication"
            :loading="processing"
        >
          确认接受
        </el-button>
      </template>
    </el-dialog>

    <!-- 拒绝申请对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝申请" width="500px">
      <el-form>
        <el-form-item label="拒绝原因">
          <el-input
              v-model="rejectForm.message"
              type="textarea"
              :rows="4"
              placeholder="请输入拒绝原因"
          ></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button
            type="danger"
            @click="submitRejectApplication"
            :loading="processing"
        >
          确认拒绝
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.message-detail-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.main-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.message-header {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e4e7ed;
}

.back-btn {
  display: flex;
  align-items: center;
  background: none;
  border: none;
  font-size: 16px;
  color: #409eff;
  cursor: pointer;
  margin-right: 30px;
  padding: 8px 16px;
  border-radius: 4px;
  transition: all 0.3s;
}

.back-btn:hover {
  background-color: #ecf5ff;
}

.message-title {
  flex: 1;
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin: 0;
}

.time-info {
  font-size: 14px;
  color: #909399;
}

.loading-state {
  text-align: center;
  padding: 100px 0;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 16px;
  color: #606266;
}

.message-content {
  margin-bottom: 30px;
}

.message-card {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.card-header {
  background-color: #f5f7fa;
  padding: 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
}

.badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
  margin-right: 12px;
}

.apply-badge {
  background-color: #ecf5ff;
  color: #409eff;
}

.invite-badge {
  background-color: #f0f9ff;
  color: #67c23a;
}

.contact-badge {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.status-badge {
  background-color: #fef0f0;
  color: #f56c6c;
}

.system-badge {
  background-color: #f0f0f0;
  color: #909399;
}

.card-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
  margin: 0;
}

.card-body {
  padding: 20px;
}

.info-section {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e4e7ed;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 14px;
}

.info-label {
  color: #909399;
  min-width: 80px;
}

.info-value {
  color: #606266;
  flex: 1;
}

.content-section {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e4e7ed;
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
}

.action-section {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  padding-top: 20px;
}

.empty-state {
  text-align: center;
  padding: 100px 0;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  color: #606266;
  margin-bottom: 24px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .main-content {
    padding: 10px;
  }

  .message-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .back-btn {
    margin-right: 0;
  }

  .action-section {
    flex-direction: column;
  }

  .action-section button {
    width: 100%;
  }
}

/* 处理后的状态样式 */
.processed-section {
  padding-top: 20px;
}

.processed-info {
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-left: 4px solid;
}

.processed-info.accepted {
  background-color: #f0f9ff;
  border-left-color: #67c23a;
}

.processed-info.rejected {
  background-color: #fef0f0;
  border-left-color: #f56c6c;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.status-badge {
  font-size: 18px;
  font-weight: bold;
  padding: 6px 16px;
  border-radius: 20px;
}

.processed-info.accepted .status-badge {
  background-color: #e1f5dc;
  color: #67c23a;
}

.processed-info.rejected .status-badge {
  background-color: #fde2e2;
  color: #f56c6c;
}

.status-time {
  font-size: 14px;
  color: #909399;
}

.merchant-message {
  margin: 16px 0;
  padding: 16px;
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.message-label {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.message-content {
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  margin: 0;
  white-space: pre-wrap;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  justify-content: flex-start;
}

/* 弹窗样式 */
.el-dialog__body {
  padding: 20px;
}

.el-form-item {
  margin-bottom: 16px;
}

.el-textarea {
  width: 100%;
}

/* 时间对比样式 */
.time-comparison-section {
  margin-top: 20px;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.comparison-container {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.student-time-section, .job-time-section {
  flex: 1;
  padding: 15px;
  background-color: white;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.comparison-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin: 0 0 12px 0;
  text-align: center;
}

.time-slots-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 100px;
}

.time-slot {
  padding: 6px 12px;
  border-radius: 12px;
  font-size: 13px;
  border: 1px solid #b3d8ff;
  background-color: #ecf5ff;
  color: #409eff;
}

.time-slot.slot-matched {
  background-color: #f0f9ff;
  color: #67c23a;
  border-color: #c2e7b0;
}

.time-slot.slot-unmatched {
  background-color: #fef0f0;
  color: #f56c6c;
  border-color: #fbc4c4;
}

.time-slot.slot-student-only {
  background-color: #ecf5ff;
  color: #409eff;
  border-color: #b3d8ff;
}

.match-indicator {
  font-size: 12px;
  margin-left: 4px;
  font-weight: bold;
}

.match-indicator.matched {
  color: #67c23a;
}

.match-indicator.unmatched {
  color: #f56c6c;
}

.match-indicator.taken {
  color: #e6a23c;
}

.no-slots {
  color: #999;
  font-size: 14px;
  font-style: italic;
  text-align: center;
  width: 100%;
  padding: 20px 0;
}

/* 时间数据加载中 */
.loading-time {
  text-align: center;
  padding: 40px 0;
}

.loading-time .loading-icon {
  font-size: 32px;
  margin-bottom: 12px;
  display: block;
}

.loading-time .loading-text {
  font-size: 14px;
  color: #606266;
}

/* 时间对比表格（动态列） */
.time-comparison-table {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 20px;
}

.table-header {
  display: flex;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.header-cell {
  flex: 1;
  padding: 12px 8px;
  text-align: center;
  font-weight: bold;
  font-size: 14px;
  color: #333;
  border-right: 1px solid #e4e7ed;
}

.header-cell:first-child {
  flex: 0 0 100px;
  min-width: 100px;
}

.header-cell:last-child {
  border-right: none;
}

.table-row {
  display: flex;
  border-bottom: 1px solid #e4e7ed;
}

.table-row:last-child {
  border-bottom: none;
}

.row-header {
  flex: 0 0 100px;
  min-width: 100px;
  padding: 12px 8px;
  text-align: center;
  font-weight: bold;
  font-size: 14px;
  color: #333;
  border-right: 1px solid #e4e7ed;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.time-cell {
  flex: 1;
  padding: 12px 8px;
  text-align: center;
  border-right: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
}

.time-cell:last-child {
  border-right: none;
}

.slot-matched, .slot-unmatched, .slot-taken, .slot-na {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
}

.slot-matched {
  background-color: #f0f9ff;
  color: #67c23a;
  border: 2px solid #67c23a;
}

.slot-unmatched {
  background-color: #fef0f0;
  color: #f56c6c;
  border: 2px solid #f56c6c;
}

.slot-taken {
  background-color: #fdf6ec;
  color: #e6a23c;
  border: 2px solid #e6a23c;
}

.slot-na {
  background-color: #f5f5f5;
  color: #999;
  border: 2px solid #d9d9d9;
}

/* 图例 */
.comparison-legend {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-bottom: 20px;
  padding: 12px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-text {
  font-size: 14px;
  color: #333;
}

/* 匹配统计 */
.match-statistics {
  display: flex;
  justify-content: space-around;
  padding: 15px;
  background-color: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 18px;
  font-weight: bold;
}

.stat-value.matched {
  color: #67c23a;
}

.stat-value.unmatched {
  color: #f56c6c;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .comparison-container {
    flex-direction: column;
  }

  .time-comparison-table {
    font-size: 12px;
  }

  .header-cell:first-child,
  .row-header {
    flex: 0 0 80px;
    min-width: 80px;
  }

  .comparison-legend {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .match-statistics {
    flex-direction: column;
    gap: 10px;
  }
}
</style>