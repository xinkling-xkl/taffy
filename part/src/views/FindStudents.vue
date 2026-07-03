<template>
  <div class="find-students-container">
    <GeneralNav />
    <div class="main-content">
      <!-- 视图切换栏 -->
      <div class="view-switch">
        <el-radio-group v-model="viewMode" @change="onViewModeChange">
          <el-radio-button value="available">空闲学生</el-radio-button>
          <el-radio-button value="working">在职学生</el-radio-button>
          <el-radio-button value="history">历史招聘</el-radio-button>
          <el-radio-button value="myJobs">兼职管理</el-radio-button>
        </el-radio-group>
        <div class="header-right">
          <el-button type="success" @click="openNewJobDialog">📝 发布兼职</el-button>
          <el-button type="primary" @click="router.push('/ai-match')">🤖 AI 智能匹配</el-button>
          <el-button @click="router.push('/parttime')">返回兼职列表</el-button>
        </div>
      </div>

      <!-- 空闲学生视图 -->
      <template v-if="viewMode === 'available'">
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索姓名或手机号" clearable style="width:200px" @input="applyFilters" />
          <el-select v-model="creditFilter" placeholder="信用分" clearable style="width:140px" @change="applyFilters">
            <el-option label="≥ 80 分" value="80" />
            <el-option label="≥ 60 分" value="60" />
            <el-option label="不限制" value="0" />
          </el-select>
          <el-select v-model="timeSlotFilter" placeholder="空闲时间段" clearable style="width:180px" @change="applyFilters">
            <el-option v-for="op in timeSlotOptions" :key="op.value" :label="op.label" :value="op.value" />
          </el-select>
        </div>

        <div class="table-section">
          <el-table :data="filteredStudents" v-loading="loading" stripe empty-text="暂无空闲学生">
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column label="信用分" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="(row.credit || 0) >= 80 ? 'success' : (row.credit || 0) >= 60 ? 'warning' : 'danger'" size="small">{{ row.credit || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="空闲时间段" min-width="220">
              <template #default="{ row }"><span class="time-slots-text">{{ formatTimePreference(row.timepreference) }}</span></template>
            </el-table-column>
            <!-- 商户评价列（增加查看评价按钮） -->
            <el-table-column label="商户评价" width="180" align="center">
              <template #default="{ row }">
                <div v-if="row.reviews && row.reviews.length > 0">
                  <div class="avg-rating">
                    <span v-for="star in 5" :key="star" class="eval-star" :class="{ active: getAvgRating(row.reviews) >= star }">★</span>
                    <span class="review-count">({{ row.reviews.length }})</span>
                  </div>
                  <el-button size="small" text type="primary" @click="showReviews(row)">查看评价</el-button>
                </div>
                <span v-else class="no-review">暂无评价</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }"><el-tag :type="row.isworking === 0 ? 'success' : 'warning'" size="small">{{ row.isworking === 0 ? '空闲' : '工作中' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="电话" width="120" align="center">
              <template #default="{ row }">{{ formatPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }"><el-button size="small" type="primary" @click="contactStudent(row)">联系</el-button></template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- 在职学生视图（保持不变） -->
      <template v-if="viewMode === 'working'">
        <div v-if="workingStudents.length === 0 && !loadingWorking" class="empty-state"><span class="empty-icon">📋</span><p>暂无在职学生</p></div>
        <div v-for="job in workingStudents" :key="job.jobId" class="job-group">
          <div class="job-group-header">
            <span class="job-title">{{ job.jobTitle }}</span>
            <el-tag :type="job.hiredCount >= parseInt(job.recruitmentLimit) ? 'success' : 'warning'" size="small">需 {{ job.recruitmentLimit }} 人 / 已招 {{ job.hiredCount }} 人</el-tag>
          </div>
          <el-table :data="job.students" v-loading="loadingWorking" stripe>
            <el-table-column prop="studentName" label="姓名" width="100" />
            <el-table-column label="信用分" width="90" align="center">
              <template #default="{ row }"><el-tag :type="(row.credit || 0) >= 80 ? 'success' : (row.credit || 0) >= 60 ? 'warning' : 'danger'" size="small">{{ row.credit || '-' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="assignedSlots" label="已排时段" width="100" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag :type="row.status === '已签到' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="电话" width="130" align="center">
              <template #default="{ row }">{{ formatPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }"><el-button size="small" type="danger" @click="fireStudent(row, job)">解雇</el-button></template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- 历史招聘视图 -->
      <template v-if="viewMode === 'history'">
        <div v-if="historyStudents.length === 0 && !loadingHistory" class="empty-state">
          <span class="empty-icon">📝</span>
          <p>暂无历史招聘记录</p>
        </div>
        <div v-else class="table-section">
          <el-table :data="historyStudents" v-loading="loadingHistory" stripe>
            <el-table-column prop="studentName" label="姓名" width="100" />
            <el-table-column label="信用分" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="(row.credit || 0) >= 80 ? 'success' : (row.credit || 0) >= 60 ? 'warning' : 'danger'" size="small">{{ row.credit || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastJobTitle" label="最近参与兼职" min-width="150" />
            <el-table-column label="工作日期" width="120">
              <template #default="scope">{{ formatDate(scope.row.lastWorkDate) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="scope">
                <el-button
                    v-if="!scope.row.evaluated"
                    size="small"
                    type="warning"
                    @click="openHistoryEval(scope.row)"
                >评价</el-button>
                <el-tag v-else type="success" size="small">已评价</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- 兼职管理视图 -->
      <template v-if="viewMode === 'myJobs'">
        <div v-if="myJobs.length === 0 && !loadingMyJobs" class="empty-state">
          <span class="empty-icon">📋</span>
          <p>暂无发布的兼职</p>
        </div>
        <div v-else class="table-section">
          <el-table :data="myJobs" v-loading="loadingMyJobs" stripe>
            <el-table-column prop="title" label="职位名称" min-width="160" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '进行中' ? 'success' : row.status === '已招满' ? 'warning' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="salary" label="薪资" width="120" />
            <el-table-column prop="recruitmentLimit" label="招聘人数" width="100" align="center" />
            <el-table-column prop="createTime" label="发布时间" width="140">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="editMyJob(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="confirmDeleteMyJob(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </div>

    <!-- 联系学生对话框（不变） -->
    <el-dialog v-model="contactDialogVisible" title="联系学生" width="500px">
      <el-form :model="contactForm" label-width="80px">
        <el-form-item label="学生姓名"><el-input :model-value="contactStudentInfo?.name" disabled /></el-form-item>
        <el-form-item label="兼职工作"><el-select v-model="contactForm.jobId" placeholder="请选择兼职"><el-option v-for="job in merchantJobs" :key="job.id" :label="job.title" :value="job.id" /></el-select></el-form-item>
        <el-form-item label="留言内容"><el-input v-model="contactForm.message" type="textarea" rows="4" placeholder="请输入您的留言" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitContactStudent">发送消息</el-button>
      </template>
    </el-dialog>

    <!-- 查看评价弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="学生评价详情" width="600px">
      <div v-if="currentReviews.length > 0" class="review-list">
        <div v-for="review in currentReviews" :key="review.id" class="review-item">
          <div class="review-header">
            <span class="merchant-name">{{ review.merchantName }}</span>
            <span class="review-stars">
              <span v-for="star in 5" :key="star" class="eval-star" :class="{ active: review.rating >= star }">★</span>
            </span>
            <span class="job-name" v-if="review.jobTitle">（{{ review.jobTitle }}）</span>
          </div>
          <p class="review-comment">{{ review.comment }}</p>
          <span class="review-time">{{ formatDateTime(review.created_at) }}</span>
        </div>
      </div>
      <div v-else class="empty-dialog">暂无评价记录</div>
    </el-dialog>

    <!-- 历史评价弹窗（商户评价学生） -->
    <el-dialog v-model="historyEvalDialogVisible" title="评价学生" width="500px">
      <el-form label-width="100px">
        <el-form-item label="学生姓名">
          <span>{{ historyEvalStudent?.studentName }}</span>
        </el-form-item>
        <el-form-item label="参与兼职">
          <span>{{ historyEvalStudent?.lastJobTitle }}</span>
        </el-form-item>
        <el-form-item label="评分">
          <div class="rating-stars">
            <span v-for="star in 5" :key="star"
                  class="star" :class="{ active: historyEvalRating >= star }"
                  @click="historyEvalRating = star">★</span>
          </div>
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="historyEvalComment" type="textarea" :rows="3" placeholder="请输入评价内容（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="historyEvalDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHistoryEval">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 兼职编辑对话框 -->
    <JobEditDialog v-model="jobEditDialogVisible" :job="editingJob" @saved="onJobSaved" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import GeneralNav from '../nav/GeneralNav.vue'
import JobEditDialog from '../components/JobEditDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const router = useRouter()
const route = useRoute()

// 视图模式
const viewMode = ref('available')
const searchKeyword = ref('')
const creditFilter = ref('0')
const timeSlotFilter = ref('')
const availableStudents = ref([])
const filteredStudents = ref([])
const loading = ref(true)
const workingStudents = ref([])
const loadingWorking = ref(false)
const currentUser = ref(null)

// 历史学生
const historyStudents = ref([])
const loadingHistory = ref(false)

// 联系学生相关
const contactDialogVisible = ref(false)
const contactForm = ref({ jobId: '', message: '' })
const contactStudentInfo = ref(null)
const merchantJobs = ref([])

// 评价查看
const reviewDialogVisible = ref(false)
const currentReviews = ref([])

// 历史评价
const historyEvalDialogVisible = ref(false)
const historyEvalStudent = ref(null)
const historyEvalRating = ref(5)
const historyEvalComment = ref('')

// 兼职管理
const myJobs = ref([])
const loadingMyJobs = ref(false)
const jobEditDialogVisible = ref(false)
const editingJob = ref(null)

// 时间段选项
const timeSlotOptions = []
const dayNames = ['周一','周二','周三','周四','周五','周六','周日']
const periodNames = ['上午','下午','晚上']
const periodKeys = ['morning','afternoon','evening']
dayNames.forEach((day, di) => { periodNames.forEach((period, pi) => { timeSlotOptions.push({ label: `${day} ${period}`, value: JSON.stringify({ day: di+1, period: periodKeys[pi] }) }) }) })

// 工具函数
const formatPhone = (phone) => { if (!phone) return '未绑定'; return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') }
const formatDateTime = (datetime) => { if (!datetime) return ''; return new Date(datetime).toLocaleString() }
const formatDate = (date) => { if (!date) return ''; return new Date(date).toLocaleDateString() }

const applyFilters = () => {
  let list = availableStudents.value
  if (searchKeyword.value.trim()) { const keyword = searchKeyword.value.toLowerCase(); list = list.filter(s => s.name.toLowerCase().includes(keyword) || (s.phone && s.phone.includes(keyword))) }
  const minCredit = parseInt(creditFilter.value) || 0; if (minCredit > 0) { list = list.filter(s => (s.credit || 0) >= minCredit) }
  if (timeSlotFilter.value) {
    try {
      const filterSlot = JSON.parse(timeSlotFilter.value)
      list = list.filter(s => { if (!s.timepreference) return false; try { const prefs = JSON.parse(s.timepreference); return prefs.some(p => { const period = typeof p.period === 'number' ? (p.period === 1 ? 'morning' : p.period === 2 ? 'afternoon' : 'evening') : p.period; return p.day === filterSlot.day && period === filterSlot.period }) } catch { return false } })
    } catch {}
  }
  filteredStudents.value = list
}

const formatTimePreference = (timepreference) => {
  if (!timepreference) return '未设置'
  try { const arr = JSON.parse(timepreference); const dayMap = {1:'周一',2:'周二',3:'周三',4:'周四',5:'周五',6:'周六',7:'周日'}; const periodMap = {morning:'上午',afternoon:'下午',evening:'晚上',1:'上午',2:'下午',3:'晚上'}; arr.sort((a,b)=>a.day-b.day); return arr.map(s=>`${dayMap[s.day]} ${periodMap[s.period] || s.period}`).join(' ') } catch { return '格式错误' }
}

// 联系学生
const contactStudent = async (student) => {
  contactStudentInfo.value = student; contactForm.value.message = ''; contactForm.value.jobId = ''
  try { const response = await axios.get('/api/job/my', { params: { userId: currentUser.value.id } }); if (response.data.success) { merchantJobs.value = response.data.data.filter(job => job.status !== '已招满') } }
  catch { ElMessage.error('获取兼职列表失败') }
  contactDialogVisible.value = true
}
const submitContactStudent = async () => {
  if (!contactStudentInfo.value || !contactForm.value.message.trim() || !contactForm.value.jobId) { ElMessage.warning('请选择兼职并输入留言内容'); return }
  try {
    const selectedJob = merchantJobs.value.find(job => job.id == contactForm.value.jobId)
    if (!selectedJob) { ElMessage.error('请选择有效的兼职'); return }
    const response = await axios.post('/api/message/send', { senderId: currentUser.value.id, receiverId: contactStudentInfo.value.id, type: 'contact_student', content: contactForm.value.message, relatedId: selectedJob.id })
    if (response.data.success) { ElMessage.success('消息已发送'); contactDialogVisible.value = false }
    else { if (response.data.message && response.data.message.includes('最近5分钟内')) { ElMessage.warning(response.data.message) } else { ElMessage.error(response.data.message) } }
  } catch { ElMessage.error('发送消息失败，请重试') }
}

// 获取空闲学生列表
const getAvailableStudents = async () => {
  try {
    const response = await axios.get('/api/user/available')
    if (response.data.success) {
      // 直接使用后端返回的空闲学生列表，不再进行二次过滤
      availableStudents.value = response.data.data || []
      // 异步加载学生评价数据，不阻塞列表显示
      loadStudentReviews()
      applyFilters()
    } else {
      ElMessage.error(response.data.message || '获取空闲学生列表失败')
      availableStudents.value = []
      filteredStudents.value = []
    }
  } catch (error) {
    console.error('获取空闲学生列表失败:', error)
    ElMessage.error('获取空闲学生列表失败，请稍后重试')
    availableStudents.value = []
    filteredStudents.value = []
  } finally {
    loading.value = false
  }
}

// 加载学生评价
const loadStudentReviews = async () => {
  const promises = availableStudents.value.map(async (student) => {
    try {
      const res = await axios.get(`/api/evaluation/student-reviews/${student.id}`)
      if (res.data.success) {
        student.reviews = res.data.data || []
      }
    } catch {
      student.reviews = []
    }
  })
  await Promise.all(promises)
}

// 计算平均星级
const getAvgRating = (reviews) => { if (!reviews || reviews.length === 0) return 0; const total = reviews.reduce((sum, r) => sum + r.rating, 0); return Math.round(total / reviews.length) }

// 查看评价弹窗
const showReviews = (student) => {
  currentReviews.value = student.reviews || []
  reviewDialogVisible.value = true
}

// 历史学生加载
const loadHistoryStudents = async () => {
  loadingHistory.value = true
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('/api/job/history-students', { params: { merchantId } })
    if (res.data.success) {
      historyStudents.value = res.data.data || []
    } else {
      ElMessage.error(res.data.message || '获取历史学生失败')
    }
  } catch (e) {
    console.error('获取历史学生失败:', e)
    ElMessage.error('获取历史学生失败')
  } finally {
    loadingHistory.value = false
  }
}

// 打开历史评价
const openHistoryEval = (student) => {
  if (!student.lastWorkAssignmentId) {
    ElMessage.warning('无法评价：缺少工作分配信息');
    return;
  }
  historyEvalStudent.value = student
  historyEvalRating.value = 5
  historyEvalComment.value = ''
  historyEvalDialogVisible.value = true
}

// 提交历史评价
const submitHistoryEval = async () => {
  const merchantId = SessionManager.getCurrentUserInfo().id
  if (!historyEvalStudent.value) return
  try {
    const res = await axios.post('/api/evaluation/submit-merchant', {
      evaluatorId: merchantId,
      evaluatedId: historyEvalStudent.value.studentId,
      jobId: historyEvalStudent.value.lastJobId,
      workAssignmentId: historyEvalStudent.value.lastWorkAssignmentId,
      rating: historyEvalRating.value,
      comment: historyEvalComment.value
    })
    if (res.data.success) {
      ElMessage.success('评价成功')
      historyEvalDialogVisible.value = false
      loadHistoryStudents() // 刷新
    } else {
      ElMessage.error(res.data.message || '评价失败')
    }
  } catch (e) {
    console.error('提交评价失败:', e)
    ElMessage.error('评价失败，请重试')
  }
}

// 视图切换
const onViewModeChange = (mode) => {
  if (mode === 'working') { loadWorkingStudents() }
  if (mode === 'history') { loadHistoryStudents() }
  if (mode === 'myJobs') { loadMyJobs() }
}

const loadWorkingStudents = async () => {
  loadingWorking.value = true
  try { const merchantId = SessionManager.getCurrentUserInfo().id; const res = await axios.get('/api/job/working-students', { params: { merchantId } }); if (res.data.success) { workingStudents.value = res.data.data || [] } else { ElMessage.error(res.data.message) } }
  catch { ElMessage.error('获取在职学生失败') }
  finally { loadingWorking.value = false }
}

// 解雇学生
const fireStudent = (student, job) => {
  ElMessageBox.confirm(
      `确定解雇学生 ${student.studentName} 吗？该学生在【${job.jobTitle}】的所有工作安排都将取消，名额全部释放。`,
      '解雇学生', { type: 'error', confirmButtonText: '确定解雇', cancelButtonText: '取消' }
  ).then(async () => {
    try { const merchantId = SessionManager.getCurrentUserInfo().id; const res = await axios.post('/api/job/fire-student', { studentId: student.studentId, jobId: job.jobId, operatorId: merchantId }); if (res.data.success) { ElMessage.success(res.data.message); loadWorkingStudents() } else { ElMessage.error(res.data.message) } }
    catch { ElMessage.error('操作失败') }
  })
}

// 兼职管理
const loadMyJobs = async () => {
  loadingMyJobs.value = true
  try {
    const merchantId = SessionManager.getCurrentUserInfo().id
    const res = await axios.get('/api/job/my', { params: { userId: merchantId } })
    if (res.data.success) {
      myJobs.value = res.data.data || []
    } else {
      ElMessage.error(res.data.message || '获取兼职列表失败')
    }
  } catch {
    ElMessage.error('获取兼职列表失败')
  } finally {
    loadingMyJobs.value = false
  }
}

const editMyJob = (job) => {
  editingJob.value = job
  jobEditDialogVisible.value = true
}

const openNewJobDialog = () => {
  editingJob.value = null
  jobEditDialogVisible.value = true
}

const confirmDeleteMyJob = async (job) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除兼职"${job.title}"吗？删除后相关的申请记录和工作安排也将被清除，此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const userId = SessionManager.getCurrentUserInfo().id
    const res = await axios.delete(`/api/job/${job.id}`, { params: { userId } })
    if (res.data.success) {
      ElMessage.success('兼职删除成功')
      loadMyJobs()
    } else {
      ElMessage.error(res.data.message || '删除失败')
    }
  } catch {
    // 用户取消
  }
}

const onJobSaved = () => {
  loadMyJobs()
}

// 生命周期
onMounted(async () => {
  const user = SessionManager.getCurrentUserInfo(); const token = SessionManager.getCurrentToken()
  if (!token || !user) { const localToken = localStorage.getItem('token'); const userStr = localStorage.getItem('userInfo'); if (!localToken || !userStr) { router.push('/userlogin'); return }; try { currentUser.value = JSON.parse(userStr) } catch { router.push('/userlogin'); return } }
  else { currentUser.value = user }
  if (currentUser.value.identity === '学生') { ElMessage.error('只有商户和管理员可以访问此页面'); router.push('/parttime'); return }

  if (route.query.tab === 'myJobs') viewMode.value = 'myJobs'
  if (route.query.tab === 'working') viewMode.value = 'working'
  if (route.query.tab === 'history') viewMode.value = 'history'

  if (viewMode.value === 'myJobs') await loadMyJobs()
  if (viewMode.value === 'history') await loadHistoryStudents()

  await getAvailableStudents()
})
</script>

<style scoped>
.find-students-container { display: flex; flex-direction: column; min-height: 100vh; background-color: #f5f7fa; }
.main-content { flex: 1; padding: 30px; max-width: 1200px; margin: 0 auto; width: 100%; }
.view-switch { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.header-right { display: flex; gap: 10px; }
.filter-bar { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; padding: 15px; background: white; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.table-section { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 10px; }
.time-slots-text { font-size: 13px; color: #555; line-height: 1.5; word-break: break-word; }
.working-students { display: flex; flex-direction: column; gap: 20px; }
.job-group { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 15px; }
.job-group-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-weight: 600; font-size: 16px; }
.job-group-header .job-title { color: #333; }
.empty-state { text-align: center; padding: 80px 0; color: #999; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }

/* 评价样式 */
.student-reviews { display: flex; flex-direction: column; align-items: center; }
.avg-rating { display: flex; align-items: center; gap: 2px; }
.eval-star { font-size: 16px; color: #ddd; }
.eval-star.active { color: #f5a623; }
.review-count { font-size: 12px; color: #999; margin-left: 4px; }
.no-review { color: #ccc; font-size: 12px; }
.review-list { max-height: 450px; overflow-y: auto; }
.review-item { padding: 12px 0; border-bottom: 1px solid #f0f0f0; }
.review-item:last-child { border-bottom: none; }
.review-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.merchant-name { font-weight: bold; font-size: 14px; }
.review-stars { display: flex; gap: 2px; }
.job-name { color: #666; font-size: 12px; }
.review-comment { font-size: 13px; color: #333; margin: 6px 0; }
.review-time { font-size: 12px; color: #999; }

.rating-stars { display: flex; gap: 8px; margin-top: 10px; }
.star { font-size: 28px; color: #ddd; cursor: pointer; transition: color 0.2s; }
.star.active { color: #f5a623; }

.empty-dialog { text-align: center; padding: 40px; color: #999; }

@media (max-width: 768px) { .main-content { padding: 15px; } .view-switch { flex-direction: column; align-items: flex-start; gap: 10px; } .filter-bar { flex-direction: column; } .filter-bar .el-input, .filter-bar .el-select { width: 100% !important; } }
</style>