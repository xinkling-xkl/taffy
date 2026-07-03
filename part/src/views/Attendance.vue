<template>
  <div class="attendance-page">
    <GeneralNav />
    <div class="page-header">
      <el-button @click="router.push('/parttime')" type="default" style="margin-right:20px;">返回主页</el-button>
    </div>
    <div class="attendance-content">
      <h2 class="page-title">签到管理</h2>

      <!-- 今日签到 -->
      <div class="today-attendance">
        <h3>今日签到</h3>
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
              <p><span class="label">时间段：</span>{{ getTimeSlotText(assignment.time_slot) }}</p>
              <p><span class="label">工作地点：</span>{{ assignment.address }}</p>
              <p><span class="label">薪资：</span>{{ assignment.salary }}</p>
            </div>
            <div class="attendance-section">
              <div class="attendance-status" :class="getAttendanceStatusClass(assignment)">
                {{ getAttendanceStatusText(assignment) }}
              </div>
              <div class="attendance-actions">
                <el-button v-if="canCheckIn(assignment)" type="primary" size="large" @click="checkIn(assignment)" :loading="checkingIn">
                  {{ checkingIn ? '签到中...' : '立即签到' }}
                </el-button>
                <el-button v-if="canCheckOut(assignment)" type="success" size="large" @click="checkOut(assignment)" :loading="checkingOut">
                  {{ checkingOut ? '签退中...' : '立即签退' }}
                </el-button>
                <el-button v-if="assignment.status === 'completed'" type="info" size="large" @click="viewEvaluation(assignment)">
                  查看评价
                </el-button>
              </div>
              <div class="attendance-time" v-if="assignment.attendance">
                <p v-if="assignment.attendance.check_in_time">签到时间：{{ formatDateTime(assignment.attendance.check_in_time) }}</p>
                <p v-if="assignment.attendance.check_out_time">签退时间：{{ formatDateTime(assignment.attendance.check_out_time) }}</p>
                <p v-if="assignment.attendance.is_late && assignment.attendance.status !== 'checked_in' && assignment.attendance.status !== 'leave'"><span class="late-tag">迟到</span> {{ assignment.attendance.late_minutes }} 分钟</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 历史签到记录 -->
      <div class="history-attendance">
        <h3>历史签到记录</h3>
        <el-table :data="pagedHistory" style="width: 100%">
          <el-table-column prop="work_date" label="工作日期" width="180" />
          <el-table-column prop="jobTitle" label="工作名称" width="200" />
          <el-table-column prop="time_slot" label="时间段" width="100" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="签到时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.check_in_time) }}</template>
          </el-table-column>
          <el-table-column label="签退时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.check_out_time) }}</template>
          </el-table-column>
          <el-table-column label="工作时长" width="140">
            <template #default="scope">
              <span>{{ getWorkDurationText(scope.row.work_minutes, scope.row.check_in_time, scope.row.check_out_time) }}</span>
              <el-tooltip content="超过50分钟工作时间算作一个小时" placement="top">
                <span style="margin-left:4px;cursor:pointer;color:#909399;">?</span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container" style="margin-top:20px;text-align:center;">
          <el-pagination v-model:current-page="historyPage" :page-size="historyPageSize" :total="filteredHistory.length" layout="prev, pager, next" size="small" />
        </div>
      </div>
    </div>

    <!-- 签到/签退对话框保持不变 -->
    <el-dialog v-model="checkInDialogVisible" title="签到确认" width="400px" align-center>
      <div class="check-dialog-content">
        <div class="current-time">当前时间：{{ currentTime }}</div>
        <div class="job-info"><p>工作：{{ currentAssignment?.jobTitle }}</p><p>时间段：{{ getTimeSlotText(currentAssignment?.time_slot) }}</p></div>
        <div class="check-status" :class="checkStatusClass">{{ checkStatusText }}</div>
      </div>
      <template #footer><el-button @click="checkInDialogVisible = false">取消</el-button><el-button type="primary" @click="confirmCheckIn" :disabled="!canConfirmCheck">确认签到</el-button></template>
    </el-dialog>

    <el-dialog v-model="checkOutDialogVisible" title="签退确认" width="400px" align-center>
      <div class="check-dialog-content">
        <div class="current-time">当前时间：{{ currentTime }}</div>
        <div class="job-info"><p>工作：{{ currentAssignment?.jobTitle }}</p><p>时间段：{{ getTimeSlotText(currentAssignment?.time_slot) }}</p></div>
        <div class="work-duration" v-if="currentAssignment?.attendance?.check_in_time">工作时长：{{ calculateWorkDuration(currentAssignment.attendance.check_in_time) }}</div>
      </div>
      <template #footer><el-button @click="checkOutDialogVisible = false">取消</el-button><el-button type="primary" @click="confirmCheckOut">确认签退</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElButton, ElDialog, ElTag, ElTable, ElTableColumn, ElPagination, ElTooltip } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
import GeneralNav from '../nav/GeneralNav.vue'

const router = useRouter()
const todayAssignments = ref([])
const historyAttendances = ref([])
const checkInDialogVisible = ref(false)
const checkOutDialogVisible = ref(false)
const currentAssignment = ref(null)
const currentTime = ref('')
const checkStatusText = ref('')
const checkStatusClass = ref('')
const canConfirmCheck = ref(false)
const checkingIn = ref(false)
const checkingOut = ref(false)

const historyPage = ref(1)
const historyPageSize = 5

const filteredHistory = computed(() => {
  const today = new Date(); today.setHours(23,59,59,999)
  return historyAttendances.value.filter(item => item.work_date && new Date(item.work_date) <= today)
})
const pagedHistory = computed(() => filteredHistory.value.slice((historyPage.value-1)*historyPageSize, historyPage.value*historyPageSize))

// ---------- 数据加载 ----------
const loadTodayAssignments = async () => {
  try {
    const studentId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('http://localhost:8082/api/attendance/today', { params: { studentId } })
    if (res.data.success) todayAssignments.value = res.data.data
  } catch(e) { console.error(e) }
}
const loadHistoryAttendances = async () => {
  try {
    const studentId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('http://localhost:8082/api/attendance/history', { params: { studentId } })
    if (res.data.success) historyAttendances.value = res.data.data
  } catch(e) { console.error(e) }
}

// ---------- 签到/签退逻辑 ----------
const checkIn = (a) => { currentAssignment.value = a; checkInDialogVisible.value = true; updateCheckStatus() }
const checkOut = (a) => { currentAssignment.value = a; checkOutDialogVisible.value = true }
const updateCheckStatus = () => {
  const now = new Date(); currentTime.value = now.toLocaleString()
  if (!currentAssignment.value) return
  const timeSlot = currentAssignment.value.time_slot
  const workDate = new Date(currentAssignment.value.work_date)
  let workStartTime = new Date(workDate), workEndTime = new Date(workDate)
  if (timeSlot === 'morning') { workStartTime.setHours(9,0,0,0); workEndTime.setHours(13,0,0,0) }
  else if (timeSlot === 'afternoon') { workStartTime.setHours(14,0,0,0); workEndTime.setHours(18,0,0,0) }
  else if (timeSlot === 'evening') { workStartTime.setHours(18,0,0,0); workEndTime.setHours(22,0,0,0) }
  const allowedStart = new Date(workStartTime.getTime() - 5*60*1000)
  const allowedEnd = new Date(workStartTime.getTime() + 5*60*1000)
  if (now >= allowedStart && now <= allowedEnd) { checkStatusText.value='可以签到'; checkStatusClass.value='status-ok'; canConfirmCheck.value=true }
  else if (now < allowedStart) { checkStatusText.value='还未到签到时间'; checkStatusClass.value='status-wait'; canConfirmCheck.value=false }
  else if (now > allowedEnd && now <= workEndTime) { checkStatusText.value='已迟到，可联系商户修改'; checkStatusClass.value='status-late'; canConfirmCheck.value=true }
  else if (now > workEndTime) { checkStatusText.value='工作时间已过'; checkStatusClass.value='status-expired'; canConfirmCheck.value=false }
}

const confirmCheckIn = async () => {
  try {
    checkingIn.value = true
    const now = new Date()
    const workDate = new Date(currentAssignment.value.work_date)
    let workStartTime = new Date(workDate)
    const timeSlot = currentAssignment.value.time_slot
    if (timeSlot === 'morning') workStartTime.setHours(9,0,0,0)
    else if (timeSlot === 'afternoon') workStartTime.setHours(14,0,0,0)
    else if (timeSlot === 'evening') workStartTime.setHours(18,0,0,0)
    const lateMinutes = now > workStartTime ? Math.floor((now - workStartTime) / 60000) : 0
    const isLate = lateMinutes > 5
    const res = await axios.post('http://localhost:8082/api/attendance/check-in', {
      workAssignmentId: currentAssignment.value.id,
      studentId: SessionManager.getCurrentUserInfo().id,
      checkInTime: now.toISOString(), isLate, lateMinutes: isLate ? lateMinutes : 0
    })
    if (res.data.success) {
      ElMessage(isLate ? `签到成功，迟到${lateMinutes}分钟` : '签到成功')
      checkInDialogVisible.value = false
      loadTodayAssignments(); loadHistoryAttendances()
    } else ElMessage.error(res.data.message || '签到失败')
  } catch(e) { console.error(e); ElMessage.error('签到失败') } finally { checkingIn.value = false }
}

const confirmCheckOut = async () => {
  try {
    checkingOut.value = true
    const checkRes = await axios.get('http://localhost:8082/api/attendance/check-merchant-confirm', { params: { workAssignmentId: currentAssignment.value.id } })
    if (!checkRes.data.success || !checkRes.data.data) { ElMessage.warning('商户尚未确认下班'); checkingOut.value = false; return }
    const now = new Date()
    const res = await axios.post('http://localhost:8082/api/attendance/check-out', {
      workAssignmentId: currentAssignment.value.id,
      studentId: SessionManager.getCurrentUserInfo().id,
      checkOutTime: now.toISOString()
    })
    if (res.data.success) {
      const checkIn = new Date(currentAssignment.value.attendance.check_in_time)
      const mins = Math.floor((now - checkIn) / 60000)
      ElMessage.success(`签退成功，工作时长：${Math.floor(mins/60)}小时${mins%60}分钟`)
      checkOutDialogVisible.value = false
      loadTodayAssignments(); loadHistoryAttendances()
    } else ElMessage.error(res.data.message || '签退失败')
  } catch(e) { console.error(e); ElMessage.error('签退失败') } finally { checkingOut.value = false }
}

const canCheckIn = (a) => {
  if (!a || (a.status !== 'assigned' && a.status !== 'pending_confirmation')) return false
  if (a.attendance && a.attendance.check_in_time) return false
  const now = new Date()
  if (!a.work_date) return false
  
  // Parse work_date - handle various formats including ISO strings with 'Z'
  let workDate;
  try {
    const raw = a.work_date;
    if (raw instanceof Date) {
      workDate = raw;
    } else {
      // Handle ISO string with 'Z' (UTC) or without
      let dateStr = String(raw);
      // Remove 'Z' if present and treat as local time
      if (dateStr.endsWith('Z')) {
        dateStr = dateStr.slice(0, -1);
      }
      // Ensure we have a proper date string
      if (!dateStr.includes('T')) {
        dateStr += 'T00:00:00';
      }
      workDate = new Date(dateStr);
    }
    if (isNaN(workDate.getTime())) return false;
  } catch {
    return false;
  }
  
  let start = new Date(workDate), end = new Date(workDate)
  if (a.time_slot === 'morning') { start.setHours(9,0,0,0); end.setHours(13,0,0,0) }
  else if (a.time_slot === 'afternoon') { start.setHours(14,0,0,0); end.setHours(18,0,0,0) }
  else if (a.time_slot === 'evening') { start.setHours(18,0,0,0); end.setHours(22,0,0,0) }
  const allowedStart = new Date(start.getTime() - 5*60*1000)
  return now >= allowedStart && now <= end
}
const canCheckOut = (a) => {
  if (!a || (a.status !== 'assigned' && a.status !== 'pending_confirmation')) return false
  if (!a.attendance?.check_in_time || a.attendance.check_out_time) return false
  return true
}

// ---------- 工作时长 ----------
const getWorkDurationText = (workMinutes, checkIn, checkOut) => {
  const mins = Number(workMinutes);
  if (!isNaN(mins) && mins > 0) return formatHours(mins);
  if (checkIn && checkOut) {
    const start = new Date(checkIn);
    const end = new Date(checkOut);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) return '时间错误';
    const diff = end - start;
    if (diff >= 0) return formatHours(Math.floor(diff / 60000));
    // 差为负，回退到数据库分钟数（如果数据库也没值，则显示占位符）
    return mins === 0 ? '0小时' : '时间不一致';
  }
  if (!checkIn) return '未签到';
  if (!checkOut) return '未签退';
  return '-';
};

const formatHours = (totalMinutes) => {
  if (totalMinutes <= 0) return '0小时'
  let hours = Math.floor(totalMinutes / 60)
  const remaining = totalMinutes % 60
  if (remaining > 50) hours += 1
  return `${hours}小时`
}

const calculateWorkDuration = (checkInTime) => {
  if (!checkInTime) return ''
  const diff = Math.floor((new Date() - new Date(checkInTime)) / 60000)
  return formatHours(diff)
}

// ---------- 辅助函数（补全）----------
const viewEvaluation = (a) => router.push(`/evaluation/${a.id}`)
const getAssignmentStatusType = (s) => ({ assigned:'warning', completed:'success', cancelled:'danger' }[s] || 'info')
const getAssignmentStatusText = (s) => ({ assigned:'已分配', completed:'已完成', cancelled:'已取消' }[s] || s)
const getTimeSlotText = (t) => ({ morning:'上午', afternoon:'下午', evening:'晚上' }[t] || t)
const formatDateTime = (d) => d ? new Date(d).toLocaleString() : ''
const getAttendanceStatusClass = (a) => {
  if (!a.attendance) return 'status-pending'
  const s = a.attendance.status || 'pending'
  return {
    pending: 'status-pending', checked_in: 'status-checked', late: 'status-late',
    absent: 'status-absent', early_leave: 'status-early-leave', completed: 'status-completed'
  }[s] || 'status-pending'
}
const getAttendanceStatusText = (a) => {
  if (!a.attendance) return '待签到'
  const s = a.attendance.status || 'pending'
  return {
    pending: '待签到', checked_in: '已签到', late: '已签到（迟到）',
    absent: '缺勤', early_leave: '已签到（提前下班）', completed: '已完成'
  }[s] || '待签到'
}

const updateCurrentTime = () => { setInterval(() => { currentTime.value = new Date().toLocaleString(); if(checkInDialogVisible.value) updateCheckStatus() }, 1000) }

onMounted(() => { loadTodayAssignments(); loadHistoryAttendances(); updateCurrentTime() })
</script>

<style scoped>
/* 样式与之前相同，保持不变 */
.attendance-page { min-height: 100vh; background: #f5f7fa; }
.attendance-content { max-width: 1200px; margin: 0 auto; padding: 30px 20px; }
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
.attendance-actions { display: flex; gap: 10px; margin-bottom: 20px; }
.attendance-time { font-size: 14px; color: #666; line-height: 1.5; }
.history-attendance { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
.history-attendance h3 { margin-bottom: 20px; color: #333; }
.check-dialog-content { padding: 20px 0; }
.current-time { font-size: 16px; font-weight: bold; margin-bottom: 20px; text-align: center; }
.job-info { margin-bottom: 20px; line-height: 1.6; }
.check-status { padding: 12px; border-radius: 4px; margin-bottom: 20px; text-align: center; font-weight: bold; }
.check-status.status-ok { background: #f6ffed; border: 1px solid #b7eb8f; color: #52c41a; }
.check-status.status-wait { background: #fffbe6; border: 1px solid #ffe58f; color: #faad14; }
.work-duration { margin-top: 20px; padding: 10px; background: #f0f5ff; border-radius: 4px; text-align: center; font-weight: bold; }
</style>