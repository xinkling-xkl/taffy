<template>
  <div class="job-detail-container">
    <GeneralNav />
    <div class="main-content">
      <div v-if="loading" class="loading-state">
        <span class="loading-icon">⏳</span>
        <p class="loading-text">加载中...</p>
      </div>

      <div v-else-if="job" class="job-detail">
        <!-- 顶部信息 -->
        <div class="job-header-section">
          <div class="back-button-container">
            <button class="back-btn" @click="router.push('/parttime')">返回主页</button>
          </div>
          <div class="job-title-section">
            <h1 class="job-title">{{ job.title }}</h1>
            <span class="job-salary">{{ job.salary }}</span>
          </div>
          <div class="job-image-tags">
            <div class="job-image" v-if="job.imageUrl">
              <img :src="getImageUrl(job.imageUrl)" class="job-image-full" @click="previewImage(getImageUrl(job.imageUrl))" />
            </div>
            <div class="job-tags">
              <span class="tag" v-for="(tag, index) in job.tags" :key="index">{{ tag }}</span>
              <span class="tag remuneration-tag" v-if="job.remunerationType">
                {{ getRemunerationText(job.remunerationType) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 基本信息和工作安排 -->
        <div class="job-info-section">
          <div class="info-grid">
            <div class="info-column">
              <div class="info-item" v-if="job.userName">
                <span class="info-label">发布者：</span>
                <span class="info-value">{{ job.userName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">工作地点：</span>
                <span class="info-value">{{ job.address }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ job.remunerationType === 'piece' ? '结算方式：' : '工作时间：' }}</span>
                <span class="info-value">{{ job.remunerationType === 'piece' ? '计件' : formatJobTime(job.time) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">招聘人数：</span>
                <span class="info-value">{{ job.recruitmentLimit || '不限' }}人</span>
              </div>
              <div class="info-item">
                <span class="info-label">状态：</span>
                <span class="info-value" :class="{ 'status-full': job.status === '已招满' }">
                  {{ job.status === '进行中' ? '正在招人' : (job.status || '正在招人') }}
                </span>
              </div>
              <div class="info-item">
                <span class="info-label">发布时间：</span>
                <span class="info-value">{{ job.createTime ? new Date(job.createTime).toLocaleString() : '' }}</span>
              </div>
            </div>

            <!-- 工作时间安排表 -->
            <div class="schedule-column" v-if="job.time">
              <div class="mini-schedule-container">
                <h3 class="schedule-title">工作时间安排</h3>
                <div class="mini-schedule-table">
                  <div class="schedule-header">
                    <div class="header-cell time-cell">时间段</div>
                    <div class="header-cell" v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day">周{{ day }}</div>
                  </div>
                  <div class="schedule-row" v-for="(period, periodIndex) in ['上午', '下午', '晚上']" :key="periodIndex">
                    <div class="row-header">{{ period }}</div>
                    <div
                        class="schedule-cell"
                        v-for="dayIndex in 7"
                        :key="dayIndex"
                        :class="{ 'has-work': workSchedule && workSchedule.data[dayIndex-1] && workSchedule.data[dayIndex-1][periodIndex] }"
                    >
                      <span v-if="workSchedule && workSchedule.data[dayIndex-1] && workSchedule.data[dayIndex-1][periodIndex]">✓</span>
                    </div>
                  </div>
                </div>
                <div class="schedule-legend">
                  <div class="legend-item"><div class="legend-color has-work"></div><span class="legend-text">有工作安排</span></div>
                  <div class="legend-item"><div class="legend-color no-work"></div><span class="legend-text">无工作安排</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 申请按钮 -->
        <div class="apply-section">
          <button class="apply-btn" @click="applyJob(job)" v-if="isStudent && job.status !== '已招满'">立即申请</button>
          <button class="apply-btn disabled" v-if="isStudent && job.status === '已招满'">已招满</button>
          <div class="small-btn-container" v-if="isStudent">
            <button class="contact-btn" @click="contactMerchant(job)"><span class="btn-icon">💬</span><span class="btn-text">联系</span></button>
            <button class="report-btn" @click="reportJob(job)"><span class="btn-icon">🚫</span><span class="btn-text">举报</span></button>
          </div>
          <button class="view-applications-btn" @click="openApplicationsDialog" v-if="canEditJob(job)">查看申请</button>
          <button class="edit-btn" @click="openEditDialog(job)" v-if="canEditJob(job)">编辑</button>
          <button class="delete-btn" @click="deleteJob(job)" v-if="canEditJob(job)">删除</button>
        </div>

        <!-- 工作内容 -->
        <div class="job-content-section">
          <h2 class="section-title">工作内容</h2>
          <div class="job-content">{{ job.content }}</div>
        </div>

        <!-- ========== 评价区域 ========== -->
        <div class="evaluation-section">
          <h2 class="section-title">用户评价</h2>
          <div v-if="evaluations.length === 0" class="empty-evaluations">暂无评价，成为第一个评价的人吧！</div>
          <div v-else class="evaluation-list">
            <div v-for="evaluation in evaluations" :key="evaluation.id" class="evaluation-card">
              <div class="eval-header">
                <div class="eval-user-info">
                  <img :src="getImageUrl(evaluation.evaluatorImage)" class="eval-avatar" />
                  <span class="eval-name">{{ evaluation.evaluatorName }}</span>
                </div>
                <div class="eval-rating">
                  <span v-for="star in 5" :key="star" class="eval-star" :class="{ active: evaluation.rating >= star }">★</span>
                </div>
                <span class="eval-time">{{ formatDateTime(evaluation.created_at) }}</span>
              </div>
              <p class="eval-comment">{{ evaluation.comment }}</p>

              <div v-if="editingEvalId === evaluation.id" class="edit-form">
                <div class="rating-stars">
                  <span v-for="star in 5" :key="star" class="star" :class="{ active: editRating >= star }" @click="editRating = star">★</span>
                </div>
                <el-input type="textarea" v-model="editComment" :rows="3"></el-input>
                <div class="edit-actions">
                  <el-button size="small" @click="saveEdit(evaluation.id)">保存</el-button>
                  <el-button size="small" @click="cancelEdit">取消</el-button>
                </div>
              </div>

              <div v-if="evaluation.reply" class="eval-reply">
                <span class="reply-label">商家回复：</span>
                <p>{{ evaluation.reply }}</p>
                <span class="reply-time" v-if="evaluation.reply_time">{{ formatDateTime(evaluation.reply_time) }}</span>
              </div>

              <div class="eval-actions">
                <el-button v-if="currentUser && currentUser.id === evaluation.evaluator_id" size="small" @click="startEdit(evaluation)">修改</el-button>
                <el-button v-if="currentUser && currentUser.id === evaluation.evaluator_id" size="small" type="danger" @click="deleteEval(evaluation.id)">删除</el-button>
                <el-button v-if="isMerchant && currentUser.id === job.userId && !evaluation.reply && replyingEvalId !== evaluation.id" size="small" @click="startReply(evaluation.id)">回复</el-button>
              </div>

              <div v-if="replyingEvalId === evaluation.id" class="reply-form">
                <el-input type="textarea" v-model="replyContent" :rows="2" placeholder="请输入回复内容"></el-input>
                <div class="reply-actions">
                  <el-button size="small" @click="submitReply(evaluation.id)">提交回复</el-button>
                  <el-button size="small" @click="cancelReply">取消</el-button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="isStudent && canComment" class="comment-form">
            <h3>发表评价</h3>
            <div class="rating-stars">
              <span v-for="star in 5" :key="star" class="star" :class="{ active: newRating >= star }" @click="newRating = star">★</span>
            </div>
            <el-input type="textarea" v-model="newComment" :rows="4" placeholder="说说你的工作体验..."></el-input>
            <el-button type="primary" @click="submitComment">提交评价</el-button>
          </div>
          <div v-else-if="isStudent && !canComment" class="comment-hint">
            您需要完成至少一次该兼职的工作且工作时长超过4小时才能评价。
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">🔍</div>
        <p class="empty-text">兼职信息不存在</p>
        <button class="back-btn" @click="router.push('/parttime')">返回列表</button>
      </div>
    </div>

    <!-- 图片预览对话框 -->
    <el-dialog v-model="previewDialogVisible" title="图片预览" width="800px" destroy-on-close>
      <img :src="previewImageUrl" class="preview-image" />
    </el-dialog>

    <!-- 申请列表对话框 -->
    <el-dialog v-model="applicationsDialogVisible" title="申请列表" width="80%" @close="applications = []">
      <div v-if="loadingApplications" class="loading-state">
        <span class="loading-icon">⏳</span>
        <p class="loading-text">加载中...</p>
      </div>
      <div v-else-if="applications.length > 0" class="applications-list">
        <div v-for="application in applications" :key="application.id" class="application-item">
          <div class="application-header">
            <span class="student-name">{{ application.studentName }}</span>
            <span class="application-status" :class="`status-${application.status}`">{{ formatApplicationStatus(application.status) }}</span>
          </div>
          <div class="application-info">
            <div class="info-row">
              <span class="info-label">信用分：</span>
              <span class="info-value">{{ application.creditScore }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">时间安排：</span>
              <span class="info-value">{{ formatTimeAvailability(application.timeAvailability) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请时间：</span>
              <span class="info-value">{{ application.createdAt ? new Date(application.createdAt).toLocaleString() : '' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请内容：</span>
              <span class="info-value application-content">{{ application.applicationContent || '无' }}</span>
            </div>
            <!-- 日结选中的日期 -->
            <div class="info-row" v-if="application.selectedDates">
              <span class="info-label">选中日期：</span>
              <span class="info-value daily-dates">
                <span v-for="d in parseSelectedDates(application.selectedDates)" :key="d.date" class="date-tag">
                  {{ d.date }} {{ getSlotName(d.timeSlot) }}
                </span>
              </span>
            </div>
          </div>
          <div class="application-actions" v-if="application.status === 'pending'">
            <button class="accept-btn" @click="handleApplication(application.id, 'accepted')">接受</button>
            <button class="reject-btn" @click="handleApplication(application.id, 'rejected')">拒绝</button>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon">📋</div>
        <p class="empty-text">暂无申请</p>
      </div>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="jobDialogVisible" :title="jobForm.id ? '编辑兼职' : '发布兼职'" width="800px">
      <el-form :model="jobForm" label-width="120px">
        <el-form-item label="标题">
          <el-input v-model="jobForm.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="工作内容">
          <el-input v-model="jobForm.content" type="textarea" :rows="4" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="图片上传">
          <el-upload class="avatar-uploader" action="#" :auto-upload="false" :show-file-list="false" :on-change="handleImageUpload" :disabled="imageUploading" accept="image/*">
            <img v-if="jobForm.imageUrl" :src="getImageUrl(jobForm.imageUrl)" style="max-width:200px;max-height:200px;object-fit:cover;border-radius:6px;" />
            <span v-else class="avatar-uploader-icon">+</span>
          </el-upload>
          <div class="upload-tip">点击上传兼职图片（可选）</div>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="jobForm.address" placeholder="请输入地址/工作地点" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="jobForm.phone" placeholder="请输入发布者联系电话" />
        </el-form-item>
        <el-form-item label="薪资">
          <el-input v-model="jobForm.salary" placeholder="请输入薪资 例如：15元一小时" />
        </el-form-item>
        <el-form-item label="招聘人数">
          <el-input v-model="jobForm.recruitmentLimit" placeholder="请输入招聘人数（如：3）" />
        </el-form-item>
        <el-form-item label="标签">
          <div class="tag-selector">
            <el-tag v-for="tag in presetTags" :key="tag" :type="jobForm.tags.includes(tag) ? 'primary' : 'info'" :effect="jobForm.tags.includes(tag) ? 'dark' : 'plain'" @click="toggleTag(tag)" class="tag-item">{{ tag }}</el-tag>
          </div>
          <el-input v-model="customTag" placeholder="输入自定义标签，按Enter添加" @keyup.enter="addCustomTag" style="margin-top: 10px" />
          <div class="selected-tags" v-if="jobForm.tags.length > 0">
            <span class="selected-tags-label">已选标签：</span>
            <el-tag v-for="tag in jobForm.tags" :key="tag" closable @close="removeTag(tag)" class="selected-tag">{{ tag }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="结算类型">
          <el-select v-model="jobForm.remunerationType" placeholder="请选择结算类型" @change="handleRemunerationTypeChange">
            <el-option label="日结（按天/按小时）" value="daily" />
            <el-option label="周结（固定每周工作）" value="weekly" />
            <el-option label="计件" value="piece" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作时间" v-if="jobForm.remunerationType !== 'piece'">
          <div class="time-slot-selector">
            <div v-for="day in weekDays" :key="day.id" class="day-section">
              <div class="day-title">{{ day.name }}</div>
              <div class="period-buttons">
                <el-button
                    v-for="period in timePeriods"
                    :key="period.id"
                    :type="isTimeSlotSelected(day.id, period.id) ? 'primary' : 'info'"
                    :plain="!isTimeSlotSelected(day.id, period.id)"
                    @click="toggleTimeSlot(day.id, period.id)"
                    size="small"
                    class="period-btn"
                >
                  {{ period.name }}
                </el-button>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="兼职类型">
          <el-select v-model="jobForm.category" placeholder="请选择兼职类型">
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jobDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveJob">保存</el-button>
      </template>
    </el-dialog>

    <!-- 日结申请对话框 -->
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

    <!-- 通用申请对话框（周结等非日结兼职） -->
    <el-dialog v-model="generalApplyDialogVisible" title="申请兼职" width="500px">
      <div v-if="generalApplyJob">
        <h4 style="margin-bottom:12px;font-size:16px;">{{ generalApplyJob.title }}</h4>
        <p style="color:#666;margin-bottom:12px;">薪资：{{ generalApplyJob.salary }} | 地址：{{ generalApplyJob.address }}</p>
        <el-input
            v-model="generalApplyContent"
            type="textarea"
            placeholder="申请理由（选填）"
            style="margin-top:10px"
            :rows="4"
        />
      </div>
      <template #footer>
        <el-button @click="generalApplyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGeneralApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 联系商户对话框 -->
    <el-dialog v-model="contactDialogVisible" title="联系商户" width="500px">
      <el-form :model="contactForm" label-width="80px">
        <el-form-item label="商户名称">
          <el-input :model-value="contactJob?.userName" disabled />
        </el-form-item>
        <el-form-item label="兼职名称">
          <el-input :model-value="contactJob?.title" disabled />
        </el-form-item>
        <el-form-item label="留言内容">
          <el-input v-model="contactForm.message" type="textarea" :rows="4" placeholder="请输入您的留言" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="contactDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitContactMerchant">发送消息</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import GeneralNav from '../nav/GeneralNav.vue'
import { ElMessage, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton, ElUpload, ElTag, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
const pendingSelectedDates = ref(null);
const router = useRouter()
const route = useRoute()

// 当前兼职信息
const job = ref(null)
const loading = ref(true)

// 当前用户信息
const currentUser = ref(null)

// 图片预览
const previewDialogVisible = ref(false)
const previewImageUrl = ref('')

// 编辑对话框
const jobDialogVisible = ref(false)
const jobForm = ref({
  id: null, title: '', content: '', address: '', phone: '', salary: '',
  time: null, remunerationType: '', category: 'all', imageUrl: '', recruitmentLimit: '', tags: []
})
const imageUploading = ref(false)
const customTag = ref('')
const weekdays = ref([])
const timeSlots = ref([])
const startTime = ref('')
const endTime = ref('')
const presetTags = ref(['急招', '周末优先', '短期兼职', '长期招聘', '校园内', '线上可做', '日结', '高薪资', '新手友好', '招满即止'])
const weekdayOptions = [
  { label: '周一', value: '周一' }, { label: '周二', value: '周二' }, { label: '周三', value: '周三' },
  { label: '周四', value: '周四' }, { label: '周五', value: '周五' }, { label: '周六', value: '周六' }, { label: '周日', value: '周日' }
]
const weekDays = [
  { id: 1, name: '周一' }, { id: 2, name: '周二' }, { id: 3, name: '周三' },
  { id: 4, name: '周四' }, { id: 5, name: '周五' }, { id: 6, name: '周六' }, { id: 7, name: '周日' }
]
const timePeriods = [
  { id: 'morning', name: '上午' },
  { id: 'afternoon', name: '下午' },
  { id: 'evening', name: '晚上' }
]

const categoryOptions = [
  { label: '所有兼职', value: 'all' },
  { label: '勤工俭学', value: 'work_study' },
  { label: '校园送', value: 'campus_delivery' },
  { label: '临时兼职', value: 'temporary' }
]

// 联系商户
const contactDialogVisible = ref(false)
const contactForm = ref({ message: '' })
const contactJob = ref(null)

// 角色判断
const isStudent = computed(() => currentUser.value?.identity === '学生')
const isMerchant = computed(() => currentUser.value?.identity !== '学生' && currentUser.value?.identity !== '管理员')
const isAdmin = computed(() => currentUser.value?.identity === '管理员')
const canEditJob = (jobData) => isAdmin.value || (isMerchant.value && jobData.userId == currentUser.value.id)

// 工作时间表格数据
const workSchedule = computed(() => {
  if (!job.value?.time) return null
  return generateWorkSchedule(job.value.time)
})

// 申请列表
const applicationsDialogVisible = ref(false)
const applications = ref([])
const loadingApplications = ref(false)

// 日结申请相关
const dailyApplyDialogVisible = ref(false)
const currentDailyJob = ref(null)
const availableDates = ref([])
const selectedSlotsMap = ref({})
const dailyApplyContent = ref('')

// 通用申请对话框（周结等非日结兼职）
const generalApplyDialogVisible = ref(false)
const generalApplyJob = ref(null)
const generalApplyContent = ref('')

// 评价相关
const evaluations = ref([])
const newRating = ref(5)
const newComment = ref('')
const canComment = ref(false)
const eligibleWorkAssignmentId = ref(null)

const editingEvalId = ref(null)
const editComment = ref('')
const editRating = ref(5)
const replyingEvalId = ref(null)
const replyContent = ref('')

// ====================== 工具函数 ======================
const previewImage = (url) => { previewImageUrl.value = url; previewDialogVisible.value = true }
const formatDateTime = (datetime) => datetime ? new Date(datetime).toLocaleString() : ''
const getImageUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  if (path.startsWith('/uploads/')) return `http://localhost:8082${path}`
  return `http://localhost:8082/uploads${path}`
}

const getRemunerationText = (type) => {
  return type === 'daily' ? '日结' : type === 'weekly' ? '周结' : ''
}



const formatJobTime = (timeStr) => {
  if (!timeStr) return '未设置'
  try {
    const obj = typeof timeStr === 'string' ? JSON.parse(timeStr) : timeStr
    if (obj.timeSlots?.length) {
      const dayMap = { 1:'周一',2:'周二',3:'周三',4:'周四',5:'周五',6:'周六',7:'周日' }
      const periodMap = { morning:'上午', afternoon:'下午', evening:'晚上' }
      const slotsByDay = {}
      obj.timeSlots.forEach(s => {
        const day = s.day
        if (!slotsByDay[day]) slotsByDay[day] = []
        slotsByDay[day].push(periodMap[s.period])
      })
      const lines = []
      for (let i=1; i<=7; i+=2) {
        const d1 = i, d2 = i+1
        let line = ''
        if (slotsByDay[d1]) line += `${dayMap[d1]} ${slotsByDay[d1].join(' ')}`
        if (slotsByDay[d2]) {
          if (line) line += '、'
          line += `${dayMap[d2]} ${slotsByDay[d2].join(' ')}`
        }
        if (line) lines.push(line)
      }
      return lines.join('、')
    }
    return timeStr
  } catch { return timeStr }
}

const generateWorkSchedule = (timeStr) => {
  if (!timeStr) return null
  try {
    const obj = typeof timeStr === 'string' ? JSON.parse(timeStr) : timeStr
    const data = []
    for (let i=0; i<7; i++) data.push([false,false,false])
    if (obj.timeSlots) {
      obj.timeSlots.forEach(s => {
        const dayIdx = s.day - 1
        let periodIdx = 0
        if (s.period === 'morning') periodIdx = 0
        else if (s.period === 'afternoon') periodIdx = 1
        else if (s.period === 'evening') periodIdx = 2
        if (dayIdx >=0 && dayIdx<7 && periodIdx>=0 && periodIdx<3) data[dayIdx][periodIdx] = true
      })
    }
    return { data }
  } catch { return null }
}

// ====================== 日结日期选择 ======================
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

const getAvailableDailyDates = (job, studentTimePref) => {
  if (!job.time) return []

  let timeObj
  try {
    timeObj = JSON.parse(job.time)
  } catch {
    return []
  }
  if (!timeObj.timeSlots || !Array.isArray(timeObj.timeSlots)) return []

  // 获取该兼职各时段的已招 / 需招人数
  const requirements = job.requirements || []

  // 解析学生时间偏好
  let studentSlots = []
  if (studentTimePref) {
    try {
      studentSlots = JSON.parse(studentTimePref)
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
      if (!req) return true
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

    if (availableSlots.length > 0) {
      const dateStr =
          currentDate.getFullYear() +
          '-' +
          String(currentDate.getMonth() + 1).padStart(2, '0') +
          '-' +
          String(currentDate.getDate()).padStart(2, '0')

      result.push({
        date: dateStr,
        dayOfWeek: currentDay,
        slots: availableSlots
      })
    }

    currentDate.setDate(currentDate.getDate() + 1)
  }

  return result
}

// 打开日结对话框时传入学生时间偏好


const openDailyApplyDialog = (job) => {
  currentDailyJob.value = job
  const user = SessionManager.getCurrentUserInfo()
  availableDates.value = getAvailableDailyDates(job, user?.timepreference)
  selectedSlotsMap.value = {}
  dailyApplyContent.value = ''
  dailyApplyDialogVisible.value = true
}
const submitDailyApply = async () => {
  const selectedDates = []
  for (const [date, slots] of Object.entries(selectedSlotsMap.value)) {
    if (slots.length > 0) {
      slots.forEach(slot => selectedDates.push({ date, timeSlot: slot }))
    }
  }
  if (selectedDates.length === 0) { ElMessage.warning('请至少选择一天'); return }

  const user = currentUser.value
  try {
    const res = await axios.post('/api/application/create', {
      jobId: currentDailyJob.value.id,
      studentId: user.id,
      merchantId: currentDailyJob.value.userId,
      studentName: user.name,
      timeAvailability: user.timepreference,
      creditScore: user.credit || 60,
      applicationContent: dailyApplyContent.value,
      selectedDates: JSON.stringify(selectedDates)  // 确保这一行存在且有值
    })
    console.log('提交的 selectedDates:', JSON.stringify(selectedDates))
    if (res.data.success) {
      ElMessage.success('申请成功')
      dailyApplyDialogVisible.value = false
    } else {
      ElMessage.error(res.data.message)
    }
  } catch { ElMessage.error('申请失败') }
}

// 通用申请提交（周结等非日结兼职）
const submitGeneralApply = async () => {
  const user = currentUser.value
  if (!generalApplyJob.value) return
  try {
    const res = await axios.post('/api/application/create', {
      jobId: generalApplyJob.value.id,
      studentId: user.id,
      merchantId: generalApplyJob.value.userId,
      studentName: user.name,
      timeAvailability: user.timepreference,
      creditScore: user.credit || 60,
      applicationContent: generalApplyContent.value
    })
    if (res.data.success) {
      ElMessage.success('申请成功')
      generalApplyDialogVisible.value = false
    } else {
      ElMessage.error(res.data.message)
    }
  } catch { ElMessage.error('申请失败') }
}

// ====================== 数据加载 ======================
const getJobDetail = async () => {
  try {
    loading.value = true
    const res = await axios.get(`/api/job/${route.params.id}`)
    if (res.data.success) {
      job.value = res.data.data
      if (job.value.tags && typeof job.value.tags === 'string') {
        try { job.value.tags = JSON.parse(job.value.tags) } catch { job.value.tags = [] }
      } else { job.value.tags = job.value.tags || [] }
      // 将hourly结算类型转换为daily
      if (job.value.remunerationType === 'hourly') {
        job.value.remunerationType = 'daily'
      }
    } else {
      ElMessage.error('获取兼职详情失败')
    }
  } catch (e) { ElMessage.error('网络错误') }
  finally { loading.value = false }
}

// ====================== 申请列表 ======================
const openApplicationsDialog = async () => {
  applicationsDialogVisible.value = true
  await loadApplications()
}
const loadApplications = async () => {
  loadingApplications.value = true
  try {
    const res = await axios.get(`/api/application/job/${job.value.id}`)
    if (res.data.success) applications.value = res.data.data
    else ElMessage.error('获取申请列表失败')
  } catch (e) { ElMessage.error('获取申请列表失败') }
  finally { loadingApplications.value = false }
}
const formatTimeAvailability = (time) => {
  if (!time) return '未设置'
  try {
    const arr = typeof time === 'string' ? JSON.parse(time) : time
    if (!Array.isArray(arr)) return '格式错误'
    const dayMap = {1:'周一',2:'周二',3:'周三',4:'周四',5:'周五',6:'周六',7:'周日'}
    const periodMap = {1:'上午',2:'下午',3:'晚上','morning':'上午','afternoon':'下午','evening':'晚上'}
    return arr.map(s => `${dayMap[s.day]||'未知'} ${periodMap[s.period]||'未知'}`).join('、')
  } catch { return time }
}
const parseSelectedDates = (datesJson) => {
  if (!datesJson) return []
  try { return JSON.parse(datesJson) } catch { return [] }
}
const formatApplicationStatus = (s) => ({ pending:'待处理', accepted:'已接受', rejected:'已拒绝' }[s] || s)
const handleApplication = async (id, status, forceAccept = false) => {
  try {
    const res = await axios.put('/api/application/status', {
      id, status, operatorId: currentUser.value.id, forceAccept
    })
    if (res.data.success) {
      ElMessage.success(status === 'accepted' ? '已接受申请' : '已拒绝申请')
      await loadApplications()
      await getJobDetail()
    } else if (res.data.message?.includes('TIME_CONFLICT')) {
      ElMessageBox.confirm(`时间冲突：${res.data.message}，是否强行接受？`, '提示', { type: 'warning' })
          .then(() => handleApplication(id, status, true))
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) { ElMessage.error('操作失败') }
}

// ====================== 编辑兼职 ======================
const openEditDialog = (j) => {
  jobForm.value = {
    id: j.id, userId: j.userId, title: j.title, content: j.content, address: j.address||'', phone: j.phone||'',
    salary: j.salary||'', time: j.time||'', remunerationType: j.remunerationType||'',
    category: j.category||'all', imageUrl: j.imageUrl||'', recruitmentLimit: j.recruitmentLimit||'',
    tags: Array.isArray(j.tags) ? [...j.tags] : (j.tags ? JSON.parse(j.tags) : [])
  }
  timeSlots.value = []
  if (j.time) {
    try {
      let t = j.time
      if (typeof t === 'string') t = JSON.parse(t)
      if (t && t.timeSlots && Array.isArray(t.timeSlots)) {
        timeSlots.value = t.timeSlots.map(s => ({ day: s.day, period: s.period }))
      }
    } catch {}
  }
  jobDialogVisible.value = true
}
const isTimeSlotSelected = (dayId, periodId) => {
  return timeSlots.value.some(slot => slot.day === dayId && slot.period === periodId)
}
const toggleTimeSlot = (dayId, periodId) => {
  const index = timeSlots.value.findIndex(slot => slot.day === dayId && slot.period === periodId)
  if (index > -1) timeSlots.value.splice(index, 1)
  else timeSlots.value.push({ day: dayId, period: periodId })
}
const handleImageUpload = async (file) => {
  const formData = new FormData(); formData.append('file', file.raw)
  try {
    imageUploading.value = true
    const res = await axios.post('/api/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.data.success) {
      ElMessage.success('图片上传成功')
      jobForm.value.imageUrl = res.data.data
    } else ElMessage.error(res.data.message)
  } catch (e) { ElMessage.error('上传失败') }
  finally { imageUploading.value = false }
}
const toggleTag = (tag) => {
  const i = jobForm.value.tags.indexOf(tag)
  if (i>-1) jobForm.value.tags.splice(i,1)
  else jobForm.value.tags.push(tag)
}
const addCustomTag = () => {
  const tag = customTag.value.trim()
  if (tag && !jobForm.value.tags.includes(tag)) {
    jobForm.value.tags.push(tag)
    customTag.value = ''
  }
}
const removeTag = (tag) => {
  const i = jobForm.value.tags.indexOf(tag)
  if (i>-1) jobForm.value.tags.splice(i,1)
}
const handleRemunerationTypeChange = () => { jobForm.value.time = null; timeSlots.value = [] }
const saveJob = async () => {
  try {
    const user = currentUser.value
    let jobData = { ...jobForm.value }
    if (timeSlots.value.length > 0) {
      jobData.time = JSON.stringify({ timeSlots: timeSlots.value })
    }
    jobData.tags = JSON.stringify(jobForm.value.tags)
    const res = jobForm.value.id
        ? await axios.put('/api/job/update', jobData, { params: { userId: user.id } })
        : await axios.post('/api/job/add', jobData, { params: { userId: user.id } })
    if (res.data.success) {
      ElMessage.success(jobForm.value.id ? '修改成功' : '发布成功')
      jobDialogVisible.value = false
      await getJobDetail()
    } else ElMessage.error(res.data.message)
  } catch (e) { ElMessage.error('保存失败') }
}
// 删除兼职/公告
const deleteJob = async (job) => {
  try {
    const user = SessionManager.getCurrentUserInfo()
    const userId = user.id

    if (isAnnouncementMode.value) {
      // 删除公告：也增加确认
      ElMessageBox.confirm(
          `确定要删除公告"${job.title}"吗？此操作不可恢复。`,
          '删除确认',
          {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning'
          }
      ).then(async () => {
        const response = await axios.delete(`/api/announcement/${job.id}`, {
          params: {userId}
        })
        if (response.data.success) {
          ElMessage.success('公告删除成功')
          await getAnnouncements()
        } else {
          ElMessage.error(response.data.message || '删除失败')
        }
      }).catch(() => {
        // 用户取消操作，不执行任何动作
      })
    } else {
      // 删除兼职：弹出确认框
      ElMessageBox.confirm(
          `确定要删除兼职"${job.title}"吗？删除后相关的申请记录和工作安排也将被清除，此操作不可恢复。`,
          '删除确认',
          {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning'
          }
      ).then(async () => {
        const response = await axios.delete(`/api/job/${job.id}`, {
          params: {userId}
        })
        if (response.data.success) {
          ElMessage.success('兼职删除成功')
          await getParttimeJobs()
        } else {
          ElMessage.error(response.data.message || '删除失败')
        }
      }).catch(() => {
        // 用户取消操作，不执行任何动作
      })
    }
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败，请重试')
  }
}
const reportJob = async (jobData) => {
  router.push({
    path: '/feedback',
    query: {
      type: '用户举报',
      content: `举报兼职：${jobData.title} (ID: ${jobData.id})\n请详细描述举报原因\n`,
      reportedUserName: jobData.userName
    }
  })
}

// ====================== 申请兼职 ======================
const applyJob = (j) => {
  const user = currentUser.value
  if (!user) return ElMessage.error('请先登录')
  if (user.identity !== '学生') return ElMessage.error('仅学生可申请')
  if (!user.rname) return ElMessage.error('请先进行实名认证')
  if (j.remunerationType === 'daily' || j.remunerationType === 'hourly') {
    openDailyApplyDialog(j)
    return
  }
  // 周结等非日结兼职：弹出通用申请对话框
  generalApplyJob.value = j
  generalApplyContent.value = ''
  generalApplyDialogVisible.value = true
}
const submitApplication = async () => {
  // 该函数在JobDetail中不再单独使用，保留以防误删
}
const contactMerchant = (j) => {
  contactJob.value = j
  contactForm.value.message = ''
  contactDialogVisible.value = true
}
const submitContactMerchant = async () => {
  if (!contactForm.value.message.trim()) return ElMessage.warning('请输入留言')
  try {
    await axios.post('/api/message/send', {
      senderId: currentUser.value.id,
      receiverId: contactJob.value.userId,
      type: 'contact_merchant',
      content: contactForm.value.message,
      relatedId: contactJob.value.id
    })
    ElMessage.success('消息已发送')
    contactDialogVisible.value = false
  } catch { ElMessage.error('发送失败') }
}

// ====================== 评价功能 ======================
const loadEvaluations = async () => {
  if (!job.value) return
  try {
    const res = await axios.get(`/api/evaluation/job/${job.value.id}`)
    if (res.data.success) evaluations.value = res.data.data
  } catch (e) { console.error(e) }
}
const checkCommentEligibility = async () => {
  if (!isStudent.value || !job.value) return
  try {
    const studentId = currentUser.value.id
    const res = await axios.get('http://localhost:8082/api/attendance/history', { params: { studentId } })
    if (res.data.success) {
      const all = res.data.data || []
      const jobCompleted = all.filter(a => a.jobId == job.value.id && a.status === 'completed' && a.check_out_time)
      const hasEligible = jobCompleted.some(a => (new Date(a.check_out_time) - new Date(a.check_in_time)) >= 4*3600*1000)
      canComment.value = hasEligible
      if (hasEligible) {
        const eligible = jobCompleted.find(a => (new Date(a.check_out_time) - new Date(a.check_in_time)) >= 4*3600*1000)
        eligibleWorkAssignmentId.value = eligible.assignmentId
      }
    }
  } catch (e) { console.error(e) }
}
const submitComment = async () => {
  if (!newComment.value.trim()) return ElMessage.warning('请输入评价内容')
  try {
    const res = await axios.post('/api/evaluation/submit', {
      evaluatorId: currentUser.value.id,
      evaluatedId: job.value.userId,
      jobId: job.value.id,
      workAssignmentId: eligibleWorkAssignmentId.value,
      rating: newRating.value,
      comment: newComment.value,
      isPositive: newRating.value >= 3
    })
    if (res.data.success) {
      ElMessage.success('评价成功')
      newComment.value = ''
      newRating.value = 5
      loadEvaluations()
      checkCommentEligibility()
    } else ElMessage.error(res.data.message || '评价失败')
  } catch (e) { ElMessage.error('评价失败') }
}
const startEdit = (evaluation) => {
  editingEvalId.value = evaluation.id
  editComment.value = evaluation.comment
  editRating.value = evaluation.rating
}
const saveEdit = async (evaluationId) => {
  if (!editComment.value.trim()) return ElMessage.warning('评价内容不能为空')
  try {
    const res = await axios.put(`/api/evaluation/${evaluationId}`, {
      rating: editRating.value, comment: editComment.value, userId: currentUser.value.id
    })
    if (res.data.success) { ElMessage.success('修改成功'); editingEvalId.value = null; loadEvaluations() }
    else ElMessage.error(res.data.message)
  } catch { ElMessage.error('修改失败') }
}
const cancelEdit = () => { editingEvalId.value = null }
const deleteEval = async (evaluationId) => {
  try {
    await ElMessageBox.confirm('确定删除这条评价吗？', '提示', { type: 'warning' })
    const res = await axios.delete(`/api/evaluation/${evaluationId}`, { params: { userId: currentUser.value.id } })
    if (res.data.success) { ElMessage.success('删除成功'); loadEvaluations(); checkCommentEligibility() }
    else ElMessage.error(res.data.message)
  } catch {}
}
const startReply = (evaluationId) => { replyingEvalId.value = evaluationId; replyContent.value = '' }
const submitReply = async (evaluationId) => {
  if (!replyContent.value.trim()) return ElMessage.warning('请输入回复内容')
  try {
    const res = await axios.post(`/api/evaluation/${evaluationId}/reply`, {
      merchantId: currentUser.value.id, reply: replyContent.value
    })
    if (res.data.success) { ElMessage.success('回复成功'); replyingEvalId.value = null; loadEvaluations() }
    else ElMessage.error(res.data.message)
  } catch { ElMessage.error('回复失败') }
}
const cancelReply = () => { replyingEvalId.value = null }

// ====================== 生命周期 ======================
onMounted(async () => {
  const user = SessionManager.getCurrentUserInfo()
  if (user) currentUser.value = user
  else {
    const userStr = localStorage.getItem('userInfo')
    if (userStr) currentUser.value = JSON.parse(userStr)
    else { router.push('/userlogin'); return }
  }
  await getJobDetail()
  await loadEvaluations()
  if (isStudent.value) await checkCommentEligibility()
})
// 判断是否今天
const isToday = (dateStr) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const d = new Date(dateStr);
  return d.getTime() === today.getTime();
};

// 星期名称
const getDayName = (dayNum) => {
  const map = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' };
  return map[dayNum] || '';
};

// 时间段中文名
const getSlotName = (slot) => {
  const map = { morning: '上午', afternoon: '下午', evening: '晚上' };
  return map[slot] || slot;
};
</script>

<style scoped>
.job-detail-container { min-height: 100vh; background-color: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.loading-state { text-align: center; padding: 100px 0; }
.loading-icon { font-size: 48px; margin-bottom: 20px; }
.job-detail { background-color: #fff; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1); padding: 30px; }
.job-header-section { margin-bottom: 30px; }
.back-button-container { margin-bottom: 20px; }
.back-btn { padding: 8px 16px; border: 1px solid #409eff; background: white; color: #409eff; border-radius: 4px; cursor: pointer; }
.back-btn:hover { background: #409eff; color: white; }
.job-title-section { display: flex; align-items: center; margin-bottom: 20px; }
.job-title { font-size: 24px; font-weight: bold; margin: 0; flex: 1; }
.job-salary { font-size: 18px; font-weight: bold; color: #ff6b6b; background: #fff5f5; padding: 6px 16px; border-radius: 16px; }
.job-image-tags { display: flex; gap: 20px; align-items: flex-start; }
.job-image { flex: 0 0 200px; }
.job-image-full { width: 100%; height: 150px; object-fit: cover; border-radius: 8px; cursor: pointer; }
.job-tags { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; }
.tag { padding: 4px 12px; background: #f0f0f0; border-radius: 12px; font-size: 14px; color: #666; }
.tag.remuneration-tag { background: #e6f7ff; border: 1px solid #91d5ff; color: #1890ff; }
.job-info-section { margin-bottom: 30px; padding: 20px; background: #f9f9f9; border-radius: 8px; }
.info-grid { display: flex; gap: 30px; }
.info-column { flex: 1; }
.schedule-column { width: 350px; flex-shrink: 0; }
.info-item { display: flex; margin-bottom: 12px; font-size: 14px; }
.info-label { width: 100px; font-weight: 600; color: #666; }
.info-value { flex: 1; color: #333; }
.status-full { color: #ff6b6b; font-weight: bold; }
.apply-section { margin-bottom: 30px; display: flex; gap: 15px; flex-wrap: wrap; }
.apply-btn { background: #52c41a; color: white; border: none; padding: 10px 30px; border-radius: 4px; cursor: pointer; font-size: 18px; }
.apply-btn.disabled { background: #ccc; cursor: not-allowed; }
.small-btn-container { display: flex; gap: 10px; }
.contact-btn, .report-btn { padding: 8px 16px; border-radius: 4px; cursor: pointer; font-size: 14px; }
.edit-btn, .delete-btn, .view-applications-btn { padding: 8px 16px; border-radius: 4px; cursor: pointer; }
.job-content-section { margin-top: 30px; }
.section-title { font-size: 18px; font-weight: bold; margin-bottom: 16px; }
.job-content { white-space: pre-wrap; }
.mini-schedule-container { padding: 10px; }
.schedule-title { font-size: 16px; font-weight: bold; margin-bottom: 15px; text-align: center; }
.mini-schedule-table { border: 1px solid #e4e7ed; border-radius: 6px; overflow: hidden; margin-bottom: 15px; }
.schedule-header { display: flex; background-color: #f5f7fa; border-bottom: 1px solid #e4e7ed; }
.header-cell { flex: 1; padding: 8px 4px; text-align: center; font-size: 12px; font-weight: bold; color: #333; border-right: 1px solid #e4e7ed; }
.header-cell:last-child { border-right: none; }
.header-cell.time-cell { flex: 0 0 60px; min-width: 60px; }
.schedule-row { display: flex; border-bottom: 1px solid #e4e7ed; }
.schedule-row:last-child { border-bottom: none; }
.row-header { flex: 0 0 60px; min-width: 60px; padding: 10px 4px; text-align: center; font-size: 12px; font-weight: bold; background-color: #fafafa; border-right: 1px solid #e4e7ed; }
.schedule-cell { flex: 1; padding: 10px 4px; text-align: center; font-size: 12px; border-right: 1px solid #e4e7ed; min-height: 36px; display: flex; align-items: center; justify-content: center; }
.schedule-cell:last-child { border-right: none; }
.schedule-cell.has-work { background-color: #f0f9ff; color: #67c23a; font-weight: bold; }
.schedule-cell:not(.has-work) { background-color: #fff; color: #999; }
.schedule-legend { display: flex; justify-content: center; gap: 20px; margin-top: 10px; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-color { width: 16px; height: 16px; border-radius: 3px; }
.legend-color.has-work { background: #f0f9ff; border: 1px solid #c2e7b0; }
.legend-color.no-work { background: #fff; border: 1px solid #d9d9d9; }
.legend-text { font-size: 12px; color: #666; }
.evaluation-section { margin-top: 40px; padding-top: 20px; border-top: 1px solid #eee; }
.empty-evaluations { color: #999; text-align: center; padding: 20px; }
.evaluation-list { display: flex; flex-direction: column; gap: 16px; }
.evaluation-card { padding: 16px; background: #fafafa; border-radius: 8px; border: 1px solid #eaeaea; }
.eval-header { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.eval-avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.eval-name { font-weight: 600; }
.eval-rating { display: flex; gap: 2px; }
.eval-star { color: #ddd; font-size: 16px; }
.eval-star.active { color: #f5a623; }
.eval-time { margin-left: auto; font-size: 12px; color: #999; }
.eval-comment { margin-bottom: 8px; }
.edit-form, .reply-form { margin-top: 10px; }
.rating-stars { display: flex; gap: 6px; margin-bottom: 10px; }
.star { font-size: 24px; color: #ddd; cursor: pointer; }
.star.active { color: #f5a623; }
.comment-hint { color: #999; font-size: 13px; margin-top: 10px; }
.eval-reply { margin-top: 10px; padding: 10px; background: #f0f7ff; border-radius: 6px; }
.reply-label { font-weight: 600; color: #1890ff; }
.application-item { border: 1px solid #e8e8e8; border-radius: 8px; padding: 15px; margin-bottom: 10px; }
.application-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.student-name { font-weight: bold; font-size: 16px; }
.application-status { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: bold; }
.status-pending { background: #fff3cd; color: #856404; }
.status-accepted { background: #d4edda; color: #155724; }
.status-rejected { background: #f8d7da; color: #721c24; }
.info-row { display: flex; margin-bottom: 6px; font-size: 14px; }
.info-label { color: #666; min-width: 80px; }
.info-value { color: #333; flex: 1; }
.application-content { white-space: pre-wrap; }
.daily-dates { display: flex; flex-wrap: wrap; gap: 4px; }
.date-tag { background: #e6f7ff; border: 1px solid #91d5ff; color: #096dd9; padding: 1px 8px; border-radius: 3px; font-size: 12px; }
.application-actions { margin-top: 10px; display: flex; gap: 10px; }
.accept-btn, .reject-btn { padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }
.accept-btn { background: #52c41a; color: white; }
.reject-btn { background: #f5222d; color: white; }
.time-slot-selector { display: flex; flex-wrap: wrap; gap: 20px; margin-top: 10px; }
.day-section { flex: 1; min-width: 120px; }
.day-title { font-weight: bold; margin-bottom: 8px; color: #333; }
.period-buttons { display: flex; flex-direction: column; gap: 4px; }
.period-btn { width: 100%; }
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
</style>