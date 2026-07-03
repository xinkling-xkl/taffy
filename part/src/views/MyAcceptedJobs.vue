<template>
  <div class="my-jobs-container">
    <GeneralNav />
    <div class="main-content">
      <div class="page-header">
        <h2 class="page-title">我的兼职工作</h2>
        <div class="header-right">
          <el-button @click="router.push('/profile?menu=schedule')">返回周表</el-button>
          <el-button @click="router.push('/parttime')">返回兼职列表</el-button>
        </div>
      </div>

      <div v-if="acceptedJobs.length === 0 && !loading" class="empty-state">
        <span class="empty-icon">📋</span>
        <p>暂无已接受的兼职工作</p>
        <el-button type="primary" @click="router.push('/parttime')">去浏览兼职</el-button>
      </div>

      <div v-loading="loading" class="job-list">
        <div v-for="job in acceptedJobs" :key="job.jobId" class="job-group">
          <div class="job-group-header">
            <div class="job-info">
              <span class="job-title">{{ job.jobTitle }}</span>
              <span class="job-merchant">商户：{{ job.merchantName }}</span>
            </div>
            <div class="job-status-area">
              <el-tag :type="job.workStatus === '已签到' ? 'success' : 'warning'" size="small">
                {{ job.workStatus }}
              </el-tag>
              <el-tag
                v-if="job.remunerationType === 'daily'"
                type="info"
                size="small"
              >日结</el-tag>
              <el-tag
                v-else-if="job.remunerationType === 'hourly'"
                type="info"
                size="small"
              >时薪</el-tag>
              <el-tag
                v-else
                type=""
                size="small"
              >周结</el-tag>
            </div>
          </div>

          <div class="job-detail-row">
            <div class="detail-item">
              <span class="detail-label">薪资：</span>
              <span class="detail-value">{{ job.salary }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">地址：</span>
              <span class="detail-value">{{ job.address }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">已排班次：</span>
              <span class="detail-value">{{ job.assignedSlots }} 个时段</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">需招人数：</span>
              <span class="detail-value">{{ job.recruitmentLimit }} 人</span>
            </div>
          </div>

          <div class="time-slots-section" v-if="job.timeSlots && job.timeSlots.length > 0">
            <span class="slots-label">工作时段：</span>
            <div class="slots-list">
              <el-tag
                v-for="(slot, idx) in job.timeSlots"
                :key="idx"
                :type="slot.status === 'checked_in' ? 'success' : 'warning'"
                size="small"
                class="slot-tag"
              >
                {{ formatWorkDate(slot.workDate) }} {{ getTimeSlotChinese(slot.timeSlot) }}
              </el-tag>
            </div>
          </div>

          <div class="job-actions">
            <el-button
              size="small"
              type="primary"
              @click="goToJobDetail(job.jobId)"
            >查看详情</el-button>
            <el-button
              size="small"
              @click="contactMerchant(job)"
            >联系商户</el-button>
            <el-button
              size="small"
              type="danger"
              @click="resignJob(job)"
            >申请辞职</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="resignDialogVisible" title="申请辞职" width="450px">
      <div class="resign-confirm">
        <p><strong>兼职：</strong>{{ resignTarget?.jobTitle }}</p>
        <p><strong>商户：</strong>{{ resignTarget?.merchantName }}</p>
        <p><strong>已排班次：</strong>{{ resignTarget?.assignedSlots }} 个时段</p>
        <el-divider />
        <p class="resign-warning">点击确认后将向商户发送辞职申请，等待商户同意后才正式释放时段。</p>
      </div>
      <template #footer>
        <el-button @click="resignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmResign" :loading="resigning">确认申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contactDialogVisible" title="联系商户" width="500px">
      <el-form :model="contactForm" label-width="80px">
        <el-form-item label="商户名称">
          <el-input :model-value="contactTarget?.merchantName" disabled />
        </el-form-item>
        <el-form-item label="兼职名称">
          <el-input :model-value="contactTarget?.jobTitle" disabled />
        </el-form-item>
        <el-form-item label="留言内容">
          <el-input
            v-model="contactForm.message"
            type="textarea"
            rows="4"
            placeholder="请输入您想对商户说的话"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitContact">发送消息</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import SessionManager from '@/utils/SessionManager.js'
import GeneralNav from '@/nav/GeneralNav.vue'

const router = useRouter()
const loading = ref(false)
const acceptedJobs = ref([])

const resignDialogVisible = ref(false)
const resignTarget = ref(null)
const resigning = ref(false)

const contactDialogVisible = ref(false)
const contactTarget = ref(null)
const contactForm = ref({ message: '' })

const formatWorkDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = d.getMonth() + 1
  const day = d.getDate()
  const weekMap = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六' }
  return `${month}/${day} ${weekMap[d.getDay()]}`
}

const getTimeSlotChinese = (slot) => {
  const map = { 'morning': '上午', 'afternoon': '下午', 'evening': '晚上' }
  return map[slot] || slot
}

const loadAcceptedJobs = async () => {
  loading.value = true
  try {
    const user = SessionManager.getCurrentUserInfo()
    if (!user) {
      router.push('/userlogin')
      return
    }
    if (user.identity !== '学生') {
      ElMessage.error('仅学生可访问此页面')
      router.push('/parttime')
      return
    }
    const res = await axios.get('/api/job/student-accepted-jobs', {
      params: { studentId: user.id }
    })
    if (res.data.success) {
      acceptedJobs.value = res.data.data || []
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error('获取兼职列表失败')
  } finally {
    loading.value = false
  }
}

const goToJobDetail = (jobId) => {
  router.push(`/job/${jobId}`)
}

const contactMerchant = (job) => {
  contactTarget.value = job
  contactForm.value.message = ''
  contactDialogVisible.value = true
}

const submitContact = async () => {
  if (!contactForm.value.message.trim()) {
    ElMessage.warning('请输入留言内容')
    return
  }
  try {
    const user = SessionManager.getCurrentUserInfo()
    await axios.post('/api/message/send', {
      senderId: user.id,
      receiverId: contactTarget.value.merchantId,
      type: 'contact_merchant',
      content: contactForm.value.message,
      relatedId: contactTarget.value.jobId
    })
    ElMessage.success('消息已发送')
    contactDialogVisible.value = false
  } catch {
    ElMessage.error('发送失败')
  }
}

const resignJob = (job) => {
  resignTarget.value = job
  resignDialogVisible.value = true
}

const confirmResign = async () => {
  resigning.value = true
  try {
    const user = SessionManager.getCurrentUserInfo()
    const res = await axios.post('/api/job/student-resign', {
      studentId: user.id,
      jobId: resignTarget.value.jobId
    })
    if (res.data.success) {
      ElMessage.success(res.data.data?.message || '辞职成功')
      resignDialogVisible.value = false
      loadAcceptedJobs()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error('操作失败')
  } finally {
    resigning.value = false
  }
}

onMounted(() => {
  loadAcceptedJobs()
})
</script>

<style scoped>
.my-jobs-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f5f7fa;
}

.main-content {
  flex: 1;
  padding: 30px;
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  color: #333;
  margin: 0;
}

.header-right {
  display: flex;
  gap: 10px;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.job-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-group {
  background: white;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 20px;
}

.job-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.job-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.job-title {
  font-weight: 600;
  font-size: 17px;
  color: #333;
}

.job-merchant {
  color: #666;
  font-size: 13px;
  background: #f0f2f5;
  padding: 2px 10px;
  border-radius: 4px;
}

.job-status-area {
  display: flex;
  gap: 8px;
}

.job-detail-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail-label {
  color: #999;
  font-size: 13px;
}

.detail-value {
  color: #555;
  font-size: 13px;
  font-weight: 500;
}

.time-slots-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px;
  background: #fafafa;
  border-radius: 8px;
}

.slots-label {
  color: #888;
  font-size: 13px;
  white-space: nowrap;
  padding-top: 2px;
}

.slots-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.slot-tag {
  margin: 0;
}

.job-actions {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.resign-confirm p {
  margin: 8px 0;
  color: #555;
}

.resign-warning {
  color: #e6a23c !important;
  font-weight: 500;
}
</style>
