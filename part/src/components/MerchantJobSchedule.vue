<template>
  <div class="merchant-job-schedule">
    <div class="schedule-header-row">
      <h3>一周工作安排</h3>
      <el-button type="link" @click="viewAllStudents" class="view-all-btn">
        兼职总体管理
      </el-button>
    </div>
    <div class="schedule-container">
      <div class="schedule-header">
        <div class="time-slot-header">
          <div class="time-slot-label">时间段</div>
        </div>
        <div v-for="day in weekDays" :key="day.id" class="day-header">
          {{ day.name }}
        </div>
      </div>

      <div v-for="slot in timeSlots" :key="slot.id" class="schedule-row">
        <div class="time-slot-cell">
          <div class="slot-name">{{ slot.name }}</div>
          <div class="slot-time">{{ slot.time }}</div>
        </div>
        <div v-for="day in weekDays" :key="day.id" class="day-cell">
          <template v-if="getJobRequirements(day.id, slot.id).length > 0">
            <div v-for="req in getJobRequirements(day.id, slot.id)" :key="req.id" class="requirement-block">
              <div class="job-title">{{ req.jobTitle }}</div>
              <div class="requirement-info">
                <span>需 {{ req.needed_count }} 人 / 已 {{ req.filled_count }} 人</span>
              </div>
              <el-tag :type="req.needed_count > req.filled_count ? 'warning' : 'success'" size="small">
                {{ req.needed_count > req.filled_count ? '招募中' : '已满' }}
              </el-tag>
            </div>
          </template>
          <div v-else class="empty-cell">-</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()

const weekDays = [
  { id: 1, name: '周一' },
  { id: 2, name: '周二' },
  { id: 3, name: '周三' },
  { id: 4, name: '周四' },
  { id: 5, name: '周五' },
  { id: 6, name: '周六' },
  { id: 7, name: '周日' }
]

const timeSlots = [
  { id: 'morning', name: '上午', time: '9:00-13:00' },
  { id: 'afternoon', name: '下午', time: '14:00-18:00' },
  { id: 'evening', name: '晚上', time: '18:00-22:00' }
]

const jobRequirements = ref([])
const jobAssignments = ref([])

const loadJobRequirements = async () => {
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('/api/schedule/merchant/requirements', { params: { merchantId } })
    if (res.data.success) jobRequirements.value = res.data.data
  } catch (error) {
    console.error('加载工作需求失败:', error)
  }
}

const loadJobAssignments = async () => {
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('/api/assignment/merchant', { params: { merchantId } })
    if (res.data.success) {
      jobAssignments.value = res.data.data
    } else {
      jobAssignments.value = []
    }
  } catch (error) {
    console.error('加载工作分配失败:', error)
    jobAssignments.value = []
  }
}

const getJobRequirements = (dayId, slotId) => {
  const requirements = jobRequirements.value.filter(r => {
    const day = typeof r.day_of_week === 'string' ? parseInt(r.day_of_week) : r.day_of_week
    return day === dayId && r.time_slot === slotId
  })

  const uniqueRequirements = []
  const seen = new Set()
  requirements.forEach(req => {
    const key = `${req.job_id || req.id}-${req.day_of_week}-${req.time_slot}`
    if (!seen.has(key)) {
      seen.add(key)
      uniqueRequirements.push(req)
    }
  })
  return uniqueRequirements
}

const getJobAssignments = (dayId, slotId) => {
  const assignments = jobAssignments.value.filter(a => {
    const day = typeof a.day_of_week === 'string' ? parseInt(a.day_of_week) : a.day_of_week
    return day === dayId && a.time_slot === slotId
  })
  const uniqueAssignments = []
  const seen = new Set()
  assignments.forEach(assign => {
    const key = `${assign.student_id}-${assign.job_id}-${assign.day_of_week}-${assign.time_slot}`
    if (!seen.has(key)) {
      seen.add(key)
      uniqueAssignments.push(assign)
    }
  })
  return uniqueAssignments
}

const viewAllStudents = () => {
  router.push('/find-students')
}

const formatStudentName = (name) => {
  if (!name) return '未知学生'
  if (name.includes('student已分配')) {
    const parts = name.split('student已分配')
    if (parts[0]?.trim()) return parts[0].trim()
  }
  return name
}

onMounted(() => {
  if (SessionManager.getCurrentUserInfo()?.identity === '商户') {
    loadJobRequirements()
    loadJobAssignments()
  }
})
</script>

<style scoped>
.merchant-job-schedule {
  padding: 16px;
  background: #f9f9f9;
  border-radius: 6px;
  max-width: 850px;
  margin: 0 auto;
}

.schedule-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.merchant-job-schedule h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.view-all-btn {
  color: #1890ff;
  font-size: 14px;
  padding: 4px 8px;
}

.view-all-btn:hover {
  color: #40a9ff;
  background-color: rgba(24, 144, 255, 0.1);
}

.schedule-container {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  background: white;
  font-size: 13px;
}

.schedule-header {
  display: flex;
  background: #f0f0f0;
  border-bottom: 1px solid #e8e8e8;
}

.time-slot-header {
  width: 90px;
  min-width: 90px;
  padding: 10px 4px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.time-slot-label {
  font-weight: bold;
  font-size: 13px;
  color: #333;
}

.day-header {
  flex: 1;
  padding: 10px 2px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  font-weight: bold;
  font-size: 13px;
  color: #333;
  min-width: 70px;
}

.day-header:last-child {
  border-right: none;
}

.schedule-row {
  display: flex;
  border-bottom: 1px solid #e8e8e8;
}

.schedule-row:last-child {
  border-bottom: none;
}

.time-slot-cell {
  width: 90px;
  min-width: 90px;
  padding: 10px 4px;
  text-align: center;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.slot-name {
  font-weight: bold;
  font-size: 13px;
  color: #333;
}

.slot-time {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.day-cell {
  flex: 1;
  padding: 6px 4px;
  text-align: center;
  border-right: 1px solid #f0f0f0;
  min-width: 70px;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.day-cell:last-child {
  border-right: none;
}

.empty-cell {
  color: #ccc;
  font-size: 13px;
}

.requirement-block {
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 6px;
  padding: 6px 4px;
  margin-bottom: 4px;
  width: 100%;
}

.requirement-block:last-child {
  margin-bottom: 0;
}

.job-title {
  font-weight: bold;
  font-size: 12px;
  color: #1677ff;
  line-height: 1.4;
  word-break: break-all;
}

.requirement-info {
  font-size: 11px;
  color: #666;
  margin: 2px 0 4px;
}
</style>
