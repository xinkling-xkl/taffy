<template>
  <div class="merchant-attendance-page">
    <GeneralNav />

    <div class="attendance-content">
      <!-- 返回主页按钮 -->
      <div class="back-header">
        <el-button type="default" icon="arrow-left" @click="router.push('/parttime')">
          返回兼职主页
        </el-button>
      </div>

      <h2 class="page-title">商户签到管理</h2>

      <!-- 今日签到任务 -->
      <div class="today-attendance">
        <h3>今日签到任务</h3>
        <div v-if="todayAssignments.length === 0" class="empty-assignments">
          <span class="empty-icon">📅</span>
          <p>今日暂无工作安排</p>
        </div>
        <div v-else class="assignment-list">
          <div v-for="assignment in todayAssignments" :key="assignment.id" class="assignment-card">
            <div class="assignment-header">
              <h4>{{ assignment.jobTitle }}</h4>
              <el-tag :type="getAssignmentStatusType(assignment.status)">
                {{ getAssignmentStatusText(assignment.status) }}
              </el-tag>
            </div>

            <div class="assignment-info">
              <p><span class="label">学生：</span>{{ assignment.studentName }}</p>
              <p><span class="label">时间段：</span>{{ getTimeSlotText(assignment.time_slot) }}</p>
              <p><span class="label">工作地点：</span>{{ assignment.address }}</p>
              <p><span class="label">薪资：</span>{{ assignment.salary }}</p>
            </div>

            <div class="attendance-section">
              <div class="attendance-status" :class="getAttendanceStatusClass(assignment)">
                {{ getAttendanceStatusText(assignment) }}
              </div>

              <div class="attendance-time" v-if="assignment.attendance">
                <p v-if="assignment.attendance.check_in_time">
                  签到时间：{{ formatDateTime(assignment.attendance.check_in_time) }}
                </p>
                <p v-if="assignment.attendance.check_out_time">
                  签退时间：{{ formatDateTime(assignment.attendance.check_out_time) }}
                </p>
                <p v-if="assignment.attendance.is_late && assignment.attendance.status !== 'checked_in' && assignment.attendance.status !== 'leave'">
                  <span class="late-tag">迟到</span> {{ assignment.attendance.late_minutes }} 分钟
                </p>
              </div>

              <div class="merchant-actions">
                <div class="status-management" v-if="assignment.attendance && !assignment.attendance.check_out_time && assignment.attendance.status !== 'leave'">
                  <template v-if="!assignment.attendance.check_in_time">
                    <el-button type="primary" size="small" @click="markAsCheckedIn(assignment)">标记为签到</el-button>
                  </template>
                  <template v-else-if="assignment.attendance.status === 'late'">
                    <el-button type="primary" size="small" @click="markNormal(assignment)">标记为签到</el-button>
                    <el-button type="danger" size="small" @click="markAbsent(assignment)">标记缺勤</el-button>
                  </template>
                  <template v-else-if="assignment.attendance.status === 'absent'">
                    <el-button type="primary" size="small" @click="markNormal(assignment)">标记为签到</el-button>
                    <el-button type="warning" size="small" @click="markLate(assignment)">标记迟到</el-button>
                  </template>
                  <template v-else>
                    <el-button type="warning" size="small" @click="markLate(assignment)">标记迟到</el-button>
                    <el-button type="danger" size="small" @click="markAbsent(assignment)">标记缺勤</el-button>
                  </template>
                  <el-button v-if="canConfirmCheckOut(assignment)" type="success" size="small" @click="confirmCheckOut(assignment)">确认下班</el-button>
                </div>
                <div v-else-if="assignment.attendance && assignment.attendance.status === 'leave'" class="leave-notice">
                  <el-tag type="info">已请假，无法修改状态</el-tag>
                </div>
                <!-- 请假按钮 -->
                <el-button v-if="canFire(assignment)" type="danger" size="small" @click="fireStudent(assignment)">
                  请假
                </el-button>
                <el-button v-if="canMerchantCheckOut(assignment)" type="success" size="large" @click="merchantCheckOut(assignment)" :loading="checkingOut">
                  {{ checkingOut ? '签退中...' : '商户签退' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 历史签到记录 -->
      <div class="history-attendance">
        <h3>历史签到记录</h3>
        <el-table :data="pagedHistoryAttendances" style="width: 100%">
          <el-table-column prop="work_date" label="工作日期" width="180">
            <template #default="scope">{{ formatDate(scope.row.work_date) }}</template>
          </el-table-column>
          <el-table-column prop="jobTitle" label="工作名称" width="200" />
          <el-table-column prop="studentName" label="学生姓名" width="120" />
          <el-table-column prop="time_slot" label="时间段" width="100">
            <template #default="scope">{{ getTimeSlotText(scope.row.time_slot) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="签到时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.attendance?.check_in_time) }}</template>
          </el-table-column>
          <el-table-column label="签退时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.attendance?.check_out_time) }}</template>
          </el-table-column>
          <el-table-column label="工作时长" width="140">
            <template #default="scope">
              <span>{{ getWorkDurationText(scope.row.attendance?.work_minutes, scope.row.attendance?.check_in_time, scope.row.attendance?.check_out_time) }}</span>
              <el-tooltip content="超过50分钟工作时间算作一个小时" placement="top">
                <span style="margin-left:4px;cursor:pointer;color:#909399;">?</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="考勤状态" width="120">
            <template #default="scope">
              <el-tag :type="getAttendanceTagType(scope.row)">{{ getAttendanceStatusText(scope.row) }}</el-tag>
            </template>
          </el-table-column>
          <!-- 评价操作列 -->
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button
                  v-if="scope.row.status === 'completed' && !scope.row.merchantEvaluated"
                  size="small"
                  type="warning"
                  @click="openMerchantEvaluation(scope.row)">
                评价学生
              </el-button>
              <el-tag v-else-if="scope.row.status === 'completed' && scope.row.merchantEvaluated" type="success" size="small">已评价</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container" style="margin-top: 20px; text-align: center;">
          <el-pagination
              v-model:current-page="historyPage"
              :page-size="historyPageSize"
              :total="filteredHistoryAttendances.length"
              layout="prev, pager, next"
              small
          />
        </div>
      </div>
    </div>

    <!-- 标记对话框 -->
    <el-dialog v-model="markDialogVisible" :title="markDialogTitle" width="400px" align-center>
      <div class="mark-dialog-content">
        <div class="current-time">当前时间：{{ currentTime }}</div>
        <div class="job-info">
          <p>工作：{{ currentAssignment?.jobTitle }}</p>
          <p>学生：{{ currentAssignment?.studentName }}</p>
          <p>时间段：{{ getTimeSlotText(currentAssignment?.time_slot) }}</p>
        </div>
        <div class="mark-reason" v-if="markType === 'late' || markType === 'absent'">
          <el-input v-model="markReason" type="textarea" rows="3" placeholder="请输入原因（可选）" />
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="markDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmMark">确认{{ markDialogTitle }}</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 签退对话框 -->
    <el-dialog v-model="checkOutDialogVisible" title="商户签退确认" width="400px" align-center>
      <div class="check-dialog-content">
        <div class="current-time">当前时间：{{ currentTime }}</div>
        <div class="job-info">
          <p>工作：{{ currentAssignment?.jobTitle }}</p>
          <p>学生：{{ currentAssignment?.studentName }}</p>
          <p>时间段：{{ getTimeSlotText(currentAssignment?.time_slot) }}</p>
        </div>
        <div class="work-duration" v-if="currentAssignment?.attendance?.check_in_time">
          工作时长：{{ calculateWorkDuration(currentAssignment.attendance.check_in_time) }}
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="checkOutDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmMerchantCheckOut">确认签退</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 商户评价学生对话框 -->
    <el-dialog v-model="merchantEvalDialogVisible" title="评价学生" width="500px" align-center>
      <div class="eval-student-info" v-if="merchantEvalStudent">
        <p><span class="label">学生姓名：</span>{{ merchantEvalStudent.studentName }}</p>
        <p><span class="label">工作名称：</span>{{ merchantEvalStudent.jobTitle }}</p>
        <p><span class="label">工作日期：</span>{{ formatDate(merchantEvalStudent.work_date) }}</p>
        <p><span class="label">时间段：</span>{{ getTimeSlotText(merchantEvalStudent.time_slot) }}</p>
      </div>
      <div class="rating-stars">
        <span class="rating-label">评分：</span>
        <span v-for="star in 5" :key="star"
              class="star"
              :class="{ active: merchantEvalRating >= star }"
              @click="merchantEvalRating = star">★</span>
      </div>
      <el-input
          v-model="merchantEvalComment"
          type="textarea"
          placeholder="请输入评价内容（可选）"
          rows="3"
          style="margin-top:15px"
      />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="merchantEvalDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitMerchantEvaluation">提交评价</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElButton, ElDialog, ElTag, ElTable, ElTableColumn, ElInput, ElPagination, ElTooltip, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
import GeneralNav from '../nav/GeneralNav.vue'

const router = useRouter()

const todayAssignments = ref([])
const historyAttendances = ref([])
const markDialogVisible = ref(false)
const checkOutDialogVisible = ref(false)
const currentAssignment = ref(null)
const currentTime = ref('')
const markType = ref('')
const markReason = ref('')
const markDialogTitle = ref('')
const checkingOut = ref(false)

// 商户评价学生相关
const merchantEvalDialogVisible = ref(false)
const merchantEvalStudent = ref(null)
const merchantEvalRating = ref(5)
const merchantEvalComment = ref('')

const historyPage = ref(1)
const historyPageSize = 5

// 过滤：只保留今天及以前的记录
const filteredHistoryAttendances = computed(() => {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return historyAttendances.value.filter(item => {
    if (!item.work_date) return false
    const date = new Date(item.work_date)
    return date <= today
  })
})

const pagedHistoryAttendances = computed(() => {
  const start = (historyPage.value - 1) * historyPageSize
  return filteredHistoryAttendances.value.slice(start, start + historyPageSize)
})

// ---------- 数据加载 ----------
const loadTodayAssignments = async () => {
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const response = await axios.get(`http://localhost:8082/api/attendance/merchant/today`, {
      params: { merchantId }
    })
    if (response.data.success) todayAssignments.value = response.data.data
  } catch (error) {
    console.error('加载今日签到任务失败:', error)
  }
}

const loadHistoryAttendances = async () => {
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const response = await axios.get(`http://localhost:8082/api/attendance/merchant/history`, {
      params: { merchantId }
    })
    if (response.data.success) {
      const data = response.data.data || []
      // 为每条历史记录检查商户是否已评价
      for (const item of data) {
        if (item.status === 'completed' && item.id) {
          try {
            const evalRes = await axios.get(`/api/evaluation/check`, {
              params: { workAssignmentId: item.id, type: 'merchant_to_student' }
            })
            item.merchantEvaluated = evalRes.data?.exists || false
          } catch {
            item.merchantEvaluated = false
          }
        } else {
          item.merchantEvaluated = false
        }
      }
      historyAttendances.value = data
    }
  } catch (error) {
    console.error('加载历史签到记录失败:', error)
  }
}

// ---------- 标记操作 ----------
const markAsCheckedIn = (assignment) => { currentAssignment.value = assignment; markType.value = 'checked_in'; markDialogTitle.value = '标记为签到'; markReason.value = ''; markDialogVisible.value = true }
const markEarlyLeave = (assignment) => { currentAssignment.value = assignment; markType.value = 'early_leave'; markDialogTitle.value = '标记提前下班'; markReason.value = ''; markDialogVisible.value = true }
const markLate = (assignment) => { currentAssignment.value = assignment; markType.value = 'late'; markDialogTitle.value = '标记迟到'; markReason.value = ''; markDialogVisible.value = true }
const markAbsent = (assignment) => { currentAssignment.value = assignment; markType.value = 'absent'; markDialogTitle.value = '标记缺勤'; markReason.value = ''; markDialogVisible.value = true }

const confirmMark = async () => {
  try {
    let apiUrl = ''
    let requestData = {}
    switch (markType.value) {
      case 'checked_in':
        apiUrl = 'http://localhost:8082/api/attendance/mark-checked-in'
        requestData = { workAssignmentId: currentAssignment.value.id, merchantId: SessionManager.getCurrentUserInfo().id, reason: markReason.value }
        break
      case 'early_leave':
        apiUrl = 'http://localhost:8082/api/attendance/mark-early-leave'
        requestData = { workAssignmentId: currentAssignment.value.id, merchantId: SessionManager.getCurrentUserInfo().id, reason: markReason.value }
        break
      case 'late':
        apiUrl = 'http://localhost:8082/api/attendance/mark-late'
        requestData = { workAssignmentId: currentAssignment.value.id, merchantId: SessionManager.getCurrentUserInfo().id, reason: markReason.value }
        break
      case 'absent':
        apiUrl = 'http://localhost:8082/api/attendance/mark-absent'
        requestData = { workAssignmentId: currentAssignment.value.id, merchantId: SessionManager.getCurrentUserInfo().id, reason: markReason.value }
        break
    }
    const response = await axios.post(apiUrl, requestData)
    if (response.data.success) {
      ElMessage.success(`${markDialogTitle.value}成功`)
      markDialogVisible.value = false
      loadTodayAssignments(); loadHistoryAttendances()
    } else { ElMessage.error(response.data.message || `${markDialogTitle.value}失败`) }
  } catch (error) { console.error(`${markDialogTitle.value}失败:`, error); ElMessage.error(`${markDialogTitle.value}失败`) }
}

const confirmCheckOut = async (assignment) => {
  try {
    const response = await axios.post('http://localhost:8082/api/attendance/merchant-confirm', {
      workAssignmentId: assignment.id, merchantId: SessionManager.getCurrentUserInfo().id
    })
    if (response.data.success) { ElMessage.success('确认下班成功'); loadTodayAssignments() }
    else { ElMessage.error(response.data.message || '确认下班失败') }
  } catch (error) { console.error('确认下班失败:', error); ElMessage.error('确认下班失败') }
}

const merchantCheckOut = (assignment) => { currentAssignment.value = assignment; checkOutDialogVisible.value = true }

const confirmMerchantCheckOut = async () => {
  try {
    checkingOut.value = true
    const now = new Date()
    const response = await axios.post('http://localhost:8082/api/attendance/merchant-check-out', {
      workAssignmentId: currentAssignment.value.id, merchantId: SessionManager.getCurrentUserInfo().id, checkOutTime: now.toISOString()
    })
    if (response.data.success) {
      const checkInTime = new Date(currentAssignment.value.attendance.check_in_time)
      const workDuration = Math.floor((now - checkInTime) / (1000 * 60))
      const hours = Math.floor(workDuration / 60); const minutes = workDuration % 60
      ElMessage.success(`签退成功，工作时长：${hours}小时${minutes}分钟`)
      checkOutDialogVisible.value = false
      loadTodayAssignments(); loadHistoryAttendances()
    } else { ElMessage.error(response.data.message || '签退失败') }
  } catch (error) { console.error('签退失败:', error); ElMessage.error('签退失败') }
  finally { checkingOut.value = false }
}

// ---------- 解雇学生 ----------
const canFire = (assignment) => { return (assignment.status === 'assigned' || assignment.status === 'checked_in') && (!assignment.attendance || assignment.attendance.status !== 'leave') }

const fireStudent = async (assignment) => {
  ElMessageBox.confirm(
      `确定要给学生请假吗 ${assignment.studentName} 吗？名额将被释放。`,
      '请假确认',
      { confirmButtonText: '确定请假', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    const merchantId = SessionManager.getCurrentUserInfo().id
    try {
      const res = await axios.post('/api/job/cancel-assignment', null, { params: { workAssignmentId: assignment.id, userId: merchantId } })
      if (res.data.success) { ElMessage.success('请假成功，名额已释放'); loadTodayAssignments(); loadHistoryAttendances() }
      else { ElMessage.error(res.data.message) }
    } catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}

// ---------- 商户评价学生 ----------
const openMerchantEvaluation = (row) => {
  merchantEvalStudent.value = row
  merchantEvalRating.value = 5
  merchantEvalComment.value = ''
  merchantEvalDialogVisible.value = true
}

const submitMerchantEvaluation = async () => {
  const merchantId = SessionManager.getCurrentUserInfo().id
  if (!merchantEvalStudent.value) return
  try {
    const res = await axios.post('/api/evaluation/submit-merchant', {
      evaluatorId: merchantId,
      evaluatedId: merchantEvalStudent.value.studentId,
      jobId: merchantEvalStudent.value.jobId,
      workAssignmentId: merchantEvalStudent.value.id,
      rating: merchantEvalRating.value,
      comment: merchantEvalComment.value
    })
    if (res.data.success) {
      ElMessage.success('评价成功')
      merchantEvalDialogVisible.value = false
      loadHistoryAttendances()
    } else { ElMessage.error(res.data.message) }
  } catch { ElMessage.error('评价失败') }
}

// ---------- 恢复为正常签到 - 迟到或缺勤改回签到 ----------
const canMarkNormal = (assignment) => {
  if (!assignment.attendance) return false
  return assignment.attendance.attendance_status === 'late' || assignment.attendance.status === 'late'
    || assignment.attendance.attendance_status === 'absent' || assignment.attendance.status === 'absent'
}

const markNormal = async (assignment) => {
  try {
    const response = await axios.post('http://localhost:8082/api/attendance/mark-normal-checked-in', {
      workAssignmentId: assignment.id, merchantId: SessionManager.getCurrentUserInfo().id
    })
    if (response.data.success) { ElMessage.success('已恢复为正常签到，信用分已补偿'); loadTodayAssignments(); loadHistoryAttendances() }
    else { ElMessage.error(response.data.message || '操作失败') }
  } catch (error) { console.error('恢复签到失败:', error); ElMessage.error('操作失败') }
}

// ---------- 按钮显示条件 ----------
const canMarkAsCheckedIn = (assignment) => { return !assignment.attendance?.check_in_time && !assignment.attendance?.check_out_time }
const canMarkLate = (assignment) => { if (!assignment.attendance?.check_in_time || assignment.attendance?.check_out_time) return false; return !assignment.attendance.is_late }
const canMarkAbsent = (assignment) => { if (assignment.attendance?.check_in_time || assignment.attendance?.check_out_time) return false; return !assignment.attendance.is_absent }
const canMarkEarlyLeave = (assignment) => { if (!assignment.attendance?.check_in_time || assignment.attendance?.check_out_time) return false; return !assignment.attendance.is_early_leave }
const canConfirmCheckOut = (assignment) => {
  if (!assignment.attendance?.check_in_time || assignment.attendance?.check_out_time) return false
  const now = new Date(); const workDate = new Date(assignment.work_date); let workEnd = new Date(workDate)
  if (assignment.time_slot === 'morning') workEnd.setHours(13, 0, 0, 0)
  else if (assignment.time_slot === 'afternoon') workEnd.setHours(18, 0, 0, 0)
  else if (assignment.time_slot === 'evening') workEnd.setHours(22, 0, 0, 0)
  return now >= workEnd
}
const canMerchantCheckOut = (assignment) => {
  if (!assignment.attendance?.check_in_time || assignment.attendance?.check_out_time) return false
  const now = new Date(); const workDate = new Date(assignment.work_date); let workEnd = new Date(workDate)
  if (assignment.time_slot === 'morning') workEnd.setHours(13, 0, 0, 0)
  else if (assignment.time_slot === 'afternoon') workEnd.setHours(18, 0, 0, 0)
  else if (assignment.time_slot === 'evening') workEnd.setHours(22, 0, 0, 0)
  return now >= workEnd
}

// ---------- 工作时长展示 ----------
const getWorkDurationText = (workMinutes, checkIn, checkOut) => {
  const mins = Number(workMinutes)
  if (!isNaN(mins) && mins > 0) return formatHours(mins)
  if (checkIn && checkOut) { const start = new Date(checkIn); const end = new Date(checkOut); if (isNaN(start.getTime()) || isNaN(end.getTime())) return '时间错误'; const diff = end - start; if (diff >= 0) return formatHours(Math.floor(diff / 60000)); return mins === 0 ? '0小时' : '时间不一致' }
  if (!checkIn) return '未签到'
  if (!checkOut) return '未签退'
  return '-'
}
const formatHours = (totalMinutes) => { if (totalMinutes <= 0) return '0小时'; let hours = Math.floor(totalMinutes / 60); const remaining = totalMinutes % 60; if (remaining > 50) hours += 1; return `${hours}小时` }
const calculateWorkDuration = (checkInTime) => { if (!checkInTime) return ''; return formatHours(Math.floor((new Date() - new Date(checkInTime)) / 60000)) }

// ---------- 格式化与状态辅助函数 ----------
const formatDate = (date) => { if (!date) return ''; return new Date(date).toLocaleDateString() }
const formatDateTime = (datetime) => { if (!datetime) return ''; return new Date(datetime).toLocaleString() }
const getTimeSlotText = (timeSlot) => { const slotMap = { 'morning': '上午', 'afternoon': '下午', 'evening': '晚上' }; return slotMap[timeSlot] || timeSlot }
const getAssignmentStatusType = (status) => { const typeMap = { 'assigned': 'warning', 'completed': 'success', 'cancelled': 'info' }; return typeMap[status] || 'info' }
const getAssignmentStatusText = (status) => { const textMap = { 'assigned': '已分配', 'completed': '已完成', 'cancelled': '请假', 'checked_in': '已签到' }; return textMap[status] || status }
const getAttendanceStatusClass = (assignment) => {
  if (assignment.status === 'cancelled') return 'status-leave'
  if (!assignment.attendance) return 'status-pending'
  const s = assignment.attendance.status
  if (s === 'pending') return 'status-pending'
  if (s === 'late') return 'status-late'
  if (s === 'absent') return 'status-absent'
  if (s === 'early_leave') return 'status-early-leave'
  if (s === 'completed') return 'status-completed'
  if (s === 'leave') return 'status-leave'
  return 'status-checked'
}
const getAttendanceStatusText = (assignment) => {
  if (assignment.status === 'cancelled') return '请假'
  if (!assignment.attendance) return '待签到'
  const s = assignment.attendance.status
  if (s === 'pending') return '待签到'
  if (s === 'late') return '已签到（迟到）'
  if (s === 'absent') return '缺勤'
  if (s === 'early_leave') return '已签到（提前下班）'
  if (s === 'completed') return '已完成'
  if (s === 'leave') return '请假'
  return '已签到'
}
const getAttendanceTagType = (attendance) => { if (!attendance.attendance) return 'info'; const s = attendance.attendance.status; if (s === 'late') return 'warning'; if (s === 'absent') return 'danger'; if (s === 'early_leave') return 'warning'; if (s === 'completed') return 'success'; if (s === 'leave') return 'info'; return 'info' }

const updateCurrentTime = () => { setInterval(() => { currentTime.value = new Date().toLocaleString() }, 1000) }

onMounted(() => { loadTodayAssignments(); loadHistoryAttendances(); updateCurrentTime() })
</script>

<style scoped>
.merchant-attendance-page { min-height: 100vh; background: #f5f7fa; }
.attendance-content { max-width: 1200px; margin: 0 auto; padding: 30px 20px; }
.back-header { margin-bottom: 20px; display: flex; align-items: center; }
.back-header .el-button { font-size: 14px; padding: 8px 18px; border-radius: 6px; background: #fff; border-color: #dcdfe6; color: #606266; transition: all 0.3s; }
.back-header .el-button:hover { border-color: #667eea; color: #667eea; background: #f5f7fa; }
.page-title { text-align: center; margin-bottom: 30px; color: #333; }
.today-attendance { background: white; border-radius: 8px; padding: 20px; margin-bottom: 30px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.today-attendance h3 { margin-bottom: 20px; color: #333; }
.empty-assignments { text-align: center; padding: 60px 0; color: #999; }
.empty-icon { font-size: 48px; display: block; margin-bottom: 16px; }
.assignment-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(400px, 1fr)); gap: 20px; }
.assignment-card { border: 1px solid #e8e8e8; border-radius: 8px; padding: 20px; background: #f9f9f9; }
.assignment-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.assignment-header h4 { margin: 0; color: #333; }
.assignment-info { margin-bottom: 20px; line-height: 1.6; font-size: 14px; color: #666; }
.assignment-info .label { font-weight: bold; color: #333; }
.attendance-section { border-top: 1px solid #e8e8e8; padding-top: 20px; }
.attendance-status { padding: 10px; border-radius: 4px; margin-bottom: 20px; text-align: center; font-weight: bold; font-size: 16px; }
.attendance-status.status-pending { background: #fffbe6; border: 1px solid #ffe58f; color: #faad14; }
.attendance-status.status-checked { background: #f6ffed; border: 1px solid #b7eb8f; color: #52c41a; }
.attendance-status.status-late { background: #fff2f0; border: 1px solid #ffccc7; color: #f5222d; }
.attendance-status.status-absent { background: #fff1f0; border: 1px solid #ffccc7; color: #f5222d; }
.attendance-status.status-early-leave { background: #fff7e6; border: 1px solid #ffd591; color: #fa8c16; }
.attendance-status.status-completed { background: #f0f5ff; border: 1px solid #adc6ff; color: #2f54eb; }
.attendance-status.status-leave { background: #f0f0f0; border: 1px solid #d9d9d9; color: #8c8c8c; }
.late-tag { display: inline-block; background: #f5222d; color: white; font-size: 12px; padding: 2px 6px; border-radius: 2px; margin-right: 5px; }
.attendance-time { font-size: 14px; color: #666; line-height: 1.5; margin-bottom: 20px; }
.merchant-actions { display: flex; flex-direction: column; gap: 10px; }
.status-management { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.history-attendance { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.history-attendance h3 { margin-bottom: 20px; color: #333; }
.check-dialog-content { padding: 20px 0; }
.current-time { font-size: 16px; font-weight: bold; margin-bottom: 20px; text-align: center; }
.job-info { margin-bottom: 20px; line-height: 1.6; }
.work-duration { margin-top: 20px; padding: 10px; background: #f0f5ff; border-radius: 4px; text-align: center; font-weight: bold; }
.mark-dialog-content { padding: 20px 0; }
.mark-reason { margin-top: 20px; }
.eval-student-info { margin-bottom: 15px; line-height: 1.8; font-size: 14px; }
.eval-student-info .label { font-weight: bold; color: #333; }
.rating-stars { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.rating-label { font-size: 14px; color: #333; }
.star { font-size: 28px; color: #ddd; cursor: pointer; transition: color 0.2s; }
.star.active { color: #f5a623; }
</style>