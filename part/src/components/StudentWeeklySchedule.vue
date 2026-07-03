<template>
  <div class="weekly-schedule">
    <h3>我的一周工作安排</h3>
    <div class="schedule-container">
      <!-- 表头 -->
      <div class="schedule-header">
        <div class="time-slot-header">
          <div class="time-slot-label">时间段</div>
        </div>
        <div v-for="day in weekDays" :key="day.id" class="day-header">
          {{ day.name }}
          <span v-if="isToday(day.id)" class="today-tag">今天</span>
        </div>
      </div>

      <!-- 时间槽 -->
      <div v-for="slot in timeSlots" :key="slot.id" class="schedule-row">
        <div class="time-slot-cell">
          <div class="slot-name">{{ slot.name }}</div>
          <div class="slot-time">{{ slot.time }}</div>
        </div>
        <div v-for="day in weekDays" :key="day.id" class="day-cell">
          <!-- 空闲时间 -->
          <div v-if="isAvailableFromPreference(day.id, slot.id) && !getAssignment(day.id, slot.id)" class="available-cell">
            <span class="available-text">空闲</span>
          </div>

          <!-- 已分配工作 -->
          <div v-else-if="getAssignment(day.id, slot.id)"
               class="assigned-cell"
               :class="getAssignmentStatusClass(getAssignment(day.id, slot.id))">
            <div class="job-title">{{ getAssignment(day.id, slot.id).jobTitle }}</div>
            <div class="assignment-status">{{ getAssignmentStatusText(getAssignment(day.id, slot.id)) }}</div>
            <div class="action-buttons">
              <el-button size="small" type="primary" @click="viewJobDetail(getAssignment(day.id, slot.id).job_id)">
                查看详情
              </el-button>

            </div>
          </div>

          <!-- 不可用时间 -->
          <div v-else class="unavailable-cell">
            <span class="unavailable-text">不可用</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()

// 星期定义
const weekDays = [
  {id: 1, name: '周一'},
  {id: 2, name: '周二'},
  {id: 3, name: '周三'},
  {id: 4, name: '周四'},
  {id: 5, name: '周五'},
  {id: 6, name: '周六'},
  {id: 7, name: '周日'}
]

// 时间段定义（含具体时间）
const timeSlots = [
  {id: 'morning', name: '上午', time: '9:00-13:00'},
  {id: 'afternoon', name: '下午', time: '14:00-18:00'},
  {id: 'evening', name: '晚上', time: '18:00-22:00'}
]

const assignments = ref([])
const userInfo = ref({})

// 加载工作分配
const loadAssignments = async () => {
  try {
    const studentId = SessionManager.getCurrentUserInfo().id
    const response = await axios.get(`http://localhost:8082/api/assignment/student/${studentId}`)
    if (response.data.success) {
      assignments.value = response.data.data
    }
  } catch (error) {
    console.error('加载工作分配失败:', error)
  }
}

// 获取指定日期和时段的工作分配
const getAssignment = (dayId, slotId) => {
  const today = new Date();
  today.setHours(0,0,0,0);
  return assignments.value.find(a => {
    const workDate = new Date(a.work_date);
    workDate.setHours(0,0,0,0);
    if (workDate < today) return false;
    const assignmentDayOfWeek = workDate.getDay() === 0 ? 7 : workDate.getDay();
    return assignmentDayOfWeek === dayId && a.time_slot === slotId;
  });
}

// 从用户时间偏好判断是否空闲
const isAvailableFromPreference = (dayId, slotId) => {
  const pref = userInfo.value.timepreference
  if (!pref) return false
  try {
    const arr = JSON.parse(pref)
    if (!Array.isArray(arr)) return false
    // 将 slotId 转换为数字 period
    let periodNum = 0
    if (slotId === 'morning') periodNum = 1
    else if (slotId === 'afternoon') periodNum = 2
    else if (slotId === 'evening') periodNum = 3
    return arr.some(s => s.day === dayId && s.period === periodNum)
  } catch {
    return false
  }
}

// 获取分配状态样式类
const getAssignmentStatusClass = (assign) => {
  if (!assign) return ''
  return `status-${assign.status}`
}

// 获取分配状态文本
const getAssignmentStatusText = (assign) => {
  if (!assign) return ''
  const map = {
    'assigned': '已分配',
    'completed': '已完成',
    'cancelled': '已取消',
    'pending': '待确认',
    'pending_confirmation': '待确认日期'
  }
  return map[assign.status] || assign.status
}

// 查看工作详情
const viewJobDetail = (jobId) => {
  if (jobId) {
    router.push(`/job/${jobId}`)
  }
}

// 判断是否今天
const isToday = (dayId) => {
  const today = new Date()
  const dayOfWeek = today.getDay()
  const todayId = dayOfWeek === 0 ? 7 : dayOfWeek
  return todayId === dayId
}

// 初始化
onMounted(async () => {
  const user = SessionManager.getCurrentUserInfo()
  if (user) {
    userInfo.value = user
    if (user.identity === '学生') {
      await loadAssignments()
    }
  }
})


const getTimeSlotText = (slot) => {
  return { morning: '上午', afternoon: '下午', evening: '晚上' }[slot] || slot
}
</script>

<style scoped>
.weekly-schedule {
  padding: 20px;
  background: #f9f9f9;
  border-radius: 8px;
}

.weekly-schedule h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #333;
}

.schedule-container {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  background: white;
}

/* 表头 */
.schedule-header {
  display: flex;
  background: #f0f0f0;
  border-bottom: 1px solid #e8e8e8;
}

.time-slot-header {
  width: 120px;
  min-width: 120px;
  padding: 12px 8px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.time-slot-label {
  font-weight: bold;
  font-size: 14px;
  color: #333;
}

.day-header {
  flex: 1;
  padding: 12px 4px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  font-weight: bold;
  font-size: 14px;
  color: #333;
  position: relative;
  min-width: 80px;
}

.day-header:last-child {
  border-right: none;
}

.today-tag {
  position: absolute;
  top: 2px;
  right: 2px;
  background: #409eff;
  color: white;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 2px;
  font-weight: normal;
}

/* 时间槽行 */
.schedule-row {
  display: flex;
  border-bottom: 1px solid #e8e8e8;
}

.schedule-row:last-child {
  border-bottom: none;
}

/* 时间段列 */
.time-slot-cell {
  width: 120px;
  min-width: 120px;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #e8e8e8;
  background: #fafafa;
}

.slot-name {
  font-weight: bold;
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}

.slot-time {
  font-size: 11px;
  color: #888;
  white-space: nowrap;
}

/* 日期单元格 */
.day-cell {
  flex: 1;
  padding: 6px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  min-height: 100px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 80px;
}

.day-cell:last-child {
  border-right: none;
}

/* 空闲状态 */
.available-cell {
  background: #f0f9ff;
  border: 1px solid #bae7ff;
  border-radius: 6px;
  padding: 10px 6px;
  text-align: center;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.available-text {
  color: #1890ff;
  font-size: 13px;
  font-weight: 500;
}

/* 已分配状态 */
.assigned-cell {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
}

.assigned-cell.status-completed {
  background: #f0f0f0;
  border-color: #d9d9d9;
}

.assigned-cell.status-cancelled {
  background: #fff2f0;
  border-color: #ffccc7;
}

.job-title {
  font-weight: bold;
  font-size: 12px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: #333;
}

.assignment-status {
  font-size: 11px;
  color: #666;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 4px;
}

.action-buttons .el-button {
  font-size: 11px;
  padding: 3px 6px;
  min-width: 60px;
}

/* 不可用状态 */
.unavailable-cell {
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 10px 6px;
  color: #999;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.unavailable-text {
  font-size: 13px;
  color: #bbb;
}

/* 响应式 */
@media (max-width: 900px) {
  .time-slot-header,
  .time-slot-cell {
    width: 80px;
    min-width: 80px;
  }

  .day-cell {
    min-width: 60px;
    padding: 4px;
  }

  .slot-name {
    font-size: 12px;
  }

  .slot-time {
    font-size: 9px;
  }

  .day-header {
    font-size: 12px;
    padding: 8px 2px;
  }
}
</style>