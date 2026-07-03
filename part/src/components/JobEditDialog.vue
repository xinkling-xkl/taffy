<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  job: { type: Object, default: null },
  isAnnouncement: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const jobForm = ref({
  id: null,
  title: '',
  content: '',
  address: '',
  phone: '',
  salary: '',
  time: null,
  remunerationType: '',
  category: 'all',
  imageUrl: '',
  recruitmentLimit: '',
  tags: [],
  status: '进行中'
})

const presetTags = [
  '周末优先', '短期兼职', '长期招聘', '校园内', '线上可做', '日结', '高薪资', '新手友好', '招满即止'
]

const timeSlots = ref([])
const customTag = ref('')
const imageUploading = ref(false)

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

const isTimeSlotSelected = (dayId, periodId) => {
  return timeSlots.value.some(slot => slot.day === dayId && slot.period === periodId)
}

const toggleTimeSlot = (dayId, periodId) => {
  const index = timeSlots.value.findIndex(slot => slot.day === dayId && slot.period === periodId)
  if (index > -1) {
    timeSlots.value.splice(index, 1)
  } else {
    timeSlots.value.push({ day: dayId, period: periodId })
  }
}

const toggleTag = (tag) => {
  const index = jobForm.value.tags.indexOf(tag)
  if (index > -1) {
    jobForm.value.tags.splice(index, 1)
  } else {
    jobForm.value.tags.push(tag)
  }
}

const addCustomTag = () => {
  const tag = customTag.value.trim()
  if (tag && !jobForm.value.tags.includes(tag)) {
    jobForm.value.tags.push(tag)
    customTag.value = ''
  }
}

const removeTag = (tag) => {
  const index = jobForm.value.tags.indexOf(tag)
  if (index > -1) {
    jobForm.value.tags.splice(index, 1)
  }
}

const handleRemunerationTypeChange = () => {
  jobForm.value.time = null
  timeSlots.value = []
}

const handleImageUpload = async (file) => {
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

  imageUploading.value = true

  const formData = new FormData()
  formData.append('file', file.raw)

  try {
    const response = await axios.post('/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (response.data.success) {
      ElMessage.success('图片上传成功')
      jobForm.value.imageUrl = response.data.data
    } else {
      ElMessage.error(response.data.message || '图片上传失败')
    }
  } catch (error) {
    console.error('上传图片失败:', error)
    ElMessage.error('图片上传失败，请稍后重试')
  } finally {
    imageUploading.value = false
  }

  return false
}

const initForm = () => {
  timeSlots.value = []
  customTag.value = ''

  if (props.job) {
    jobForm.value = {
      id: props.job.id,
      title: props.job.title || '',
      content: props.job.content || '',
      address: props.job.address || '',
      phone: props.job.phone || '',
      salary: props.job.salary || '',
      time: props.job.time || '',
      remunerationType: props.job.remunerationType || '',
      category: props.job.category || 'all',
      imageUrl: props.job.imageUrl || '',
      status: props.job.status || '进行中',
      recruitmentLimit: props.job.recruitmentLimit || '',
      tags: Array.isArray(props.job.tags) ? [...props.job.tags] : (props.job.tags ? JSON.parse(props.job.tags) : [])
    }

    if (props.job.time && !props.isAnnouncement) {
      try {
        const timeObj = typeof props.job.time === 'string' ? JSON.parse(props.job.time) : props.job.time
        if (timeObj.timeSlots && Array.isArray(timeObj.timeSlots)) {
          timeSlots.value = timeObj.timeSlots.map(s => ({ day: s.day, period: s.period }))
        }
      } catch {}
    }
  } else {
    jobForm.value = {
      id: null,
      title: '',
      content: '',
      address: '',
      phone: '',
      salary: '',
      time: '',
      remunerationType: '',
      category: 'all',
      imageUrl: '',
      recruitmentLimit: '',
      tags: [],
      status: '进行中'
    }
  }
}

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    initForm()
  }
})

const saveJob = async () => {
  try {
    const user = SessionManager.getCurrentUserInfo()

    if (!props.isAnnouncement) {
      if (!jobForm.value.title.trim()) return ElMessage.warning('请输入标题')
      if (!jobForm.value.content.trim()) return ElMessage.warning('请输入工作内容')
      if (!jobForm.value.address.trim()) return ElMessage.warning('请输入地址')
      if (!jobForm.value.phone.trim()) return ElMessage.warning('请输入联系电话')
      if (!jobForm.value.salary.trim()) return ElMessage.warning('请输入薪资')
      if (!jobForm.value.recruitmentLimit.trim()) return ElMessage.warning('请输入招聘人数')
      if (!jobForm.value.remunerationType) return ElMessage.warning('请选择结算类型')
      if (timeSlots.value.length === 0) return ElMessage.warning('请选择工作时间')
    }

    const userId = user.id

    if (props.isAnnouncement) {
      const announcementData = {
        id: jobForm.value.id,
        title: jobForm.value.title,
        content: jobForm.value.content,
        status: 1
      }

      if (jobForm.value.id) {
        const response = await axios.put('/api/announcement/update', announcementData, { params: { userId } })
        if (response.data.success) {
          ElMessage.success('修改成功')
          emit('update:modelValue', false)
          emit('saved')
        } else {
          ElMessage.error(response.data.message)
        }
      } else {
        const response = await axios.post('/api/announcement/add', announcementData, { params: { userId } })
        if (response.data.success) {
          ElMessage.success('发布成功')
          emit('update:modelValue', false)
          emit('saved')
        } else {
          ElMessage.error(response.data.message)
        }
      }
    } else {
      let jobData = { ...jobForm.value }

      if (timeSlots.value.length > 0) {
        jobData.time = JSON.stringify({ timeSlots: [...timeSlots.value] })
      } else {
        jobData.time = null
      }

      jobData.tags = JSON.stringify(jobForm.value.tags)

      if (jobForm.value.id) {
        jobData.userId = props.job ? props.job.userId : userId
        const response = await axios.put('/api/job/update', jobData, { params: { userId } })
        if (response.data.success) {
          ElMessage.success('修改成功')
          emit('update:modelValue', false)
          emit('saved')
        } else {
          ElMessage.error(response.data.message)
        }
      } else {
        const response = await axios.post('/api/job/add', jobData, { params: { userId } })
        if (response.data.success) {
          ElMessage.success('发布成功')
          emit('update:modelValue', false)
          emit('saved')
        } else {
          ElMessage.error(response.data.message)
        }
      }
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败，请重试')
  }
}

const handleDelete = async () => {
  try {
    const user = SessionManager.getCurrentUserInfo()
    const userId = user.id

    if (props.isAnnouncement) {
      await ElMessageBox.confirm(
        `确定要删除公告"${props.job.title}"吗？此操作不可恢复。`,
        '删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await axios.delete(`/api/announcement/${props.job.id}`, { params: { userId } })
      if (response.data.success) {
        ElMessage.success('公告删除成功')
        emit('update:modelValue', false)
        emit('saved')
      } else {
        ElMessage.error(response.data.message || '删除失败')
      }
    } else {
      await ElMessageBox.confirm(
        `确定要删除兼职"${props.job.title}"吗？删除后相关的申请记录和工作安排也将被清除，此操作不可恢复。`,
        '删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await axios.delete(`/api/job/${props.job.id}`, { params: { userId } })
      if (response.data.success) {
        ElMessage.success('兼职删除成功')
        emit('update:modelValue', false)
        emit('saved')
      } else {
        ElMessage.error(response.data.message || '删除失败')
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="jobForm.id ? (isAnnouncement ? '编辑公告' : '编辑兼职') : (isAnnouncement ? '发布公告' : '发布兼职')"
    width="800px"
  >
    <el-form :model="jobForm" label-width="120px">
      <el-form-item label="标题">
        <el-input v-model="jobForm.title" placeholder="请输入标题" />
      </el-form-item>
      <el-form-item label="工作内容">
        <el-input v-model="jobForm.content" type="textarea" rows="4" placeholder="请输入内容" />
      </el-form-item>
      <el-form-item label="图片上传" v-if="!isAnnouncement">
        <el-upload
          class="avatar-uploader"
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleImageUpload"
          :disabled="imageUploading"
          accept="image/*"
        >
          <img v-if="jobForm.imageUrl" :src="jobForm.imageUrl" class="avatar" />
          <span v-else class="avatar-uploader-icon">+</span>
        </el-upload>
        <div class="upload-tip">点击上传兼职图片（可选）</div>
      </el-form-item>
      <el-form-item label="地址" v-if="!isAnnouncement">
        <el-input v-model="jobForm.address" placeholder="请输入地址/工作地点" />
      </el-form-item>
      <el-form-item label="联系电话" v-if="!isAnnouncement">
        <el-input v-model="jobForm.phone" placeholder="请输入发布者联系电话" />
      </el-form-item>
      <el-form-item label="薪资" v-if="!isAnnouncement">
        <el-input v-model="jobForm.salary" placeholder="请输入薪资 例如：15元一小时" />
      </el-form-item>
      <el-form-item label="招聘人数" v-if="!isAnnouncement">
        <el-input v-model="jobForm.recruitmentLimit" placeholder="请输入招聘人数（如：3人）" />
        <div class="form-tip">请输入招聘人数（仅数字）</div>
      </el-form-item>
      <el-form-item label="标签" v-if="!isAnnouncement">
        <div class="tag-selector">
          <el-tag
            v-for="tag in presetTags"
            :key="tag"
            :type="jobForm.tags.includes(tag) ? 'primary' : 'info'"
            :effect="jobForm.tags.includes(tag) ? 'dark' : 'plain'"
            @click="toggleTag(tag)"
            class="tag-item"
          >
            {{ tag }}
          </el-tag>
        </div>
        <el-input
          v-model="customTag"
          placeholder="输入自定义标签，按Enter添加"
          @keyup.enter="addCustomTag"
          style="margin-top: 10px"
        />
        <div class="selected-tags" v-if="jobForm.tags.length > 0">
          <span class="selected-tags-label">已选标签：</span>
          <el-tag
            v-for="tag in jobForm.tags"
            :key="tag"
            closable
            @close="removeTag(tag)"
            class="selected-tag"
          >
            {{ tag }}
          </el-tag>
        </div>
      </el-form-item>
      <el-form-item label="结算类型" v-if="!isAnnouncement">
        <el-select v-model="jobForm.remunerationType" placeholder="请选择结算类型" @change="handleRemunerationTypeChange">
          <el-option label="日结（按天/按小时）" value="daily" />
          <el-option label="周结（固定每周工作）" value="weekly" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作时间" v-if="!isAnnouncement">
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
      <el-form-item label="兼职类型" v-if="!isAnnouncement">
        <el-select v-model="jobForm.category" placeholder="请选择兼职类型">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" v-if="!isAnnouncement">
        <el-select v-model="jobForm.status" placeholder="请选择状态">
          <el-option label="正在招人" value="进行中" />
          <el-option label="已招满" value="已招满" />
          <el-option label="已结束" value="已结束" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="jobForm.id" type="danger" @click="handleDelete">删除</el-button>
      <el-button type="primary" @click="saveJob">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.avatar-uploader {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.avatar-uploader:hover {
  border-color: #667eea;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #999;
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.upload-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #999;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.tag-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  cursor: pointer;
}

.selected-tags {
  margin-top: 10px;
}

.selected-tags-label {
  font-size: 14px;
  color: #666;
  margin-right: 8px;
}

.selected-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.time-slot-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-top: 10px;
}

.day-section {
  flex: 1;
  min-width: 120px;
}

.day-title {
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
}

.period-buttons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.period-btn {
  width: 100%;
}
</style>
