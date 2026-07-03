<script setup>
import GeneralNav from '../nav/GeneralNav.vue'
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitFeedback as submitFeedbackApi } from '../services/Feedbackservice.js'
import axios from 'axios'

const route = useRoute()

// 反馈内容
const feedbackContent = ref('')

// 反馈类型
const feedbackType = ref('问题反馈')

// 被举报用户ID
const reportedUserId = ref('')

// 被举报用户名
const reportedUserName = ref('')

// 最大字数限制
const maxWordCount = 300

// 计算当前字数
const currentWordCount = computed(() => {
  return feedbackContent.value.length
})

// 反馈类型选项
const feedbackTypeOptions = [
  { label: '问题反馈', value: '问题反馈' },
  { label: '建议', value: '建议' },
  { label: '对某用户或商家的举报', value: '用户举报' },
  { label: '信用申诉', value: '信用申诉' },
  { label: '其他', value: '其他' }
]

// 图片上传相关
const uploadedImage = ref('')
const imagePreviewUrl = ref('')
const imagePreviewVisible = ref(false)

// 反馈历史相关
const historyDialogVisible = ref(false)
const feedbackHistory = ref([])
const currentPage = ref(1)
const pageSize = ref(5)
const total = ref(0)
const loading = ref(false)

// 获取图片完整URL
const getImageUrl = (imagePath) => {
  if (!imagePath) return ''
  if (imagePath.startsWith('http')) return imagePath
  if (imagePath.startsWith('/uploads/')) return `http://localhost:8082${imagePath}`
  return `http://localhost:8082/uploads${imagePath}`
}

// 获取用户反馈历史
const getUserFeedbackHistory = async () => {
  const userStr = localStorage.getItem('userInfo')
  if (!userStr) {
    ElMessage.error('请先登录')
    return
  }
  
  const user = JSON.parse(userStr)
  const userId = user.id
  
  loading.value = true
  try {
    const response = await axios.get(`http://localhost:8082/api/feedback/user/${userId}`)
    if (response.data.success) {
      feedbackHistory.value = response.data.data
      total.value = response.data.data.length
    } else {
      ElMessage.error('获取反馈历史失败')
    }
  } catch (error) {
    console.error('获取反馈历史失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 打开反馈历史弹窗
const openHistoryDialog = () => {
  currentPage.value = 1
  getUserFeedbackHistory()
  historyDialogVisible.value = true
}

// 分页数据
const paginatedFeedback = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return feedbackHistory.value.slice(start, end)
})

// 处理图片上传
const handleImageUpload = async (file) => {
  const isImage = file.raw.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }

  const isLt10M = file.raw.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB!')
    return false
  }

  // 创建FormData对象
  const formData = new FormData()
  formData.append('file', file.raw)

  try {
    const response = await axios.post('http://localhost:8082/api/feedback/upload-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (response.data.success) {
      ElMessage.success('图片上传成功')
      uploadedImage.value = response.data.data
      imagePreviewUrl.value = getImageUrl(response.data.data)
    } else {
      ElMessage.error(response.data.message || '图片上传失败')
    }
  } catch (error) {
    console.error('上传图片失败:', error)
    ElMessage.error('图片上传失败，请稍后重试')
  }

  return false
}

// 删除已上传的图片
const removeImage = () => {
  uploadedImage.value = ''
  imagePreviewUrl.value = ''
  ElMessage.info('图片已删除')
}

// 预览图片
const previewImage = () => {
  if (imagePreviewUrl.value) {
    imagePreviewVisible.value = true
  }
}

// 预览反馈历史中的图片
const previewFeedbackImage = (imagePath) => {
  imagePreviewUrl.value = getImageUrl(imagePath)
  imagePreviewVisible.value = true
}

// 处理查询参数
onMounted(() => {
  // 检查URL查询参数
  const query = route.query
  
  if (query.type) {
    feedbackType.value = query.type
  }
  
  if (query.content) {
    feedbackContent.value = query.content
  }
  
  if (query.reportedUserName) {
    reportedUserName.value = query.reportedUserName
  }
  
  // 如果是从举报跳转过来的，自动选择"用户举报"类型
  if (query.jobId || query.jobTitle) {
    feedbackType.value = '用户举报'
  }
})

// 提交反馈
const submitFeedback = async () => {
  if (!feedbackContent.value.trim()) {
    ElMessage.warning('请输入反馈内容')
    return
  }
  
  if (feedbackContent.value.length > maxWordCount) {
    ElMessage.warning(`反馈内容不能超过${maxWordCount}字`)
    return
  }
  
  const userStr = localStorage.getItem('userInfo')
  if (!userStr) {
    ElMessage.error('请先登录')
    return
  }
  
  const user = JSON.parse(userStr)
  const userId = user.id
  
  // 构建反馈对象
  let finalContent = feedbackContent.value
  
  // 如果是用户举报，在内容前添加"举报类型："前缀，以便在admin界面中正确分类
  if (feedbackType.value === '用户举报') {
    finalContent = `举报类型：${feedbackType.value}\n被举报用户：${reportedUserName.value || '未指定'}\n\n${feedbackContent.value}`
  }
  if (feedbackType.value === '信用申诉') {
    finalContent = `信用申诉\n\n${feedbackContent.value}`
  }
  const feedback = {
    userId: userId,
    content: finalContent,
    image: uploadedImage.value, // 上传的图片路径
    type: feedbackType.value, // 反馈类型
    reportedUserId: feedbackType.value === '用户举报' ? reportedUserId.value : null, // 被举报用户ID
    reportedUserName: feedbackType.value === '用户举报' ? reportedUserName.value : null, // 被举报用户姓名
    status: '', // 后端会自动设置
    reply: '', // 初始为空
    data: null // 后端会自动设置
  }
  
  // 提交到后端
  try {
    const result = await submitFeedbackApi(feedback)
    if (result.success) {
      ElMessage.success('反馈提交成功')
      feedbackContent.value = ''
      uploadedImage.value = ''
      imagePreviewUrl.value = ''
    } else {
      ElMessage.error(result.message || '反馈提交失败')
    }
  } catch (error) {
    console.error('提交反馈失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}
</script>

<template>
  <div class="feedback-container">
    <!-- 导航栏 -->
    <GeneralNav />
    
    <!-- 主要内容 -->
    <main class="feedback-main">
      <div class="feedback-card">
        <h2 class="feedback-title">问题反馈</h2>
        
        <!-- 反馈类型 -->
        <div class="feedback-type-section">
          <label class="input-label">反馈类型</label>
          <div class="type-selector">
            <el-select v-model="feedbackType" placeholder="请选择反馈类型" class="type-select">
              <el-option 
                v-for="option in feedbackTypeOptions" 
                :key="option.value" 
                :label="option.label" 
                :value="option.value"
              ></el-option>
            </el-select>
          </div>
        </div>
        
        <!-- 被举报用户信息 -->
        <div v-if="feedbackType === '用户举报'" class="reported-user-section">
          <div class="user-input-group">
            <div class="user-input-item">
              <label class="input-label">被举报用户名</label>
              <el-input 
                v-model="reportedUserName" 
                placeholder="请输入被举报用户名"
                class="user-input"
              />
            </div>
          </div>
        </div>
        
        <!-- 反馈输入框 -->
        <div class="feedback-input-section">
          <label class="input-label">反馈内容</label>
          <div class="textarea-wrapper">
            <textarea 
              v-model="feedbackContent"
              class="feedback-textarea"
              :placeholder="feedbackType === '用户举报' ? '请详细描述举报原因和相关证据' : '请输入您的问题或建议，最多300字'"
              maxlength="300"
              rows="8"
            ></textarea>
            <div class="word-count">
              {{ currentWordCount }}/{{ maxWordCount }}
            </div>
          </div>
        </div>
        
        <!-- 图片上传区域 -->
        <div class="image-upload-section">
          <label class="upload-label">添加图片（可选）</label>
          <div v-if="!uploadedImage" class="upload-placeholder">
            <el-upload
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleImageUpload"
              accept="image/*"
            >
              <span class="upload-icon">📷</span>
              <span class="upload-text">点击上传图片</span>
              <span class="upload-hint">（支持jpg、png、gif格式）</span>
            </el-upload>
          </div>
          <div v-else class="image-preview">
            <img :src="imagePreviewUrl" alt="预览" @click="previewImage" />
            <button class="remove-image-btn" @click="removeImage">删除</button>
          </div>
        </div>
        
        <!-- 提交按钮 -->
        <div class="submit-section">
          <button class="submit-btn" @click="submitFeedback">
            提交反馈
          </button>
          <button class="history-btn" @click="openHistoryDialog">
            查看反馈历史
          </button>
        </div>
      </div>
    </main>

    <!-- 图片预览对话框 -->
    <el-dialog
      v-model="imagePreviewVisible"
      title="图片预览"
      width="600px"
      align-center
    >
      <div class="image-preview-dialog">
        <img :src="imagePreviewUrl" alt="图片预览" />
      </div>
    </el-dialog>

    <!-- 反馈历史对话框 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="我的反馈历史"
      width="800px"
      align-center
    >
      <div v-loading="loading" class="history-content">
        <div v-if="paginatedFeedback.length === 0" class="empty-history">
          <span class="empty-icon">📝</span>
          <p>暂无反馈记录</p>
        </div>
        <div v-else class="feedback-list">
          <div v-for="feedback in paginatedFeedback" :key="feedback.id" class="feedback-item">
            <div class="feedback-header">
              <span class="feedback-date">{{ feedback.date }}</span>
              <el-tag :type="feedback.status === '已解决' ? 'success' : 'warning'" size="small">
                {{ feedback.status || '待解决' }}
              </el-tag>
            </div>
            <div class="feedback-content-text">{{ feedback.content }}</div>
            <div v-if="feedback.image" class="feedback-image">
              <img :src="getImageUrl(feedback.image)" alt="反馈图片" @click="previewFeedbackImage(feedback.image)" />
            </div>
            <div v-if="feedback.reply" class="feedback-reply">
              <div class="reply-label">管理员回复：</div>
              <div class="reply-content">{{ feedback.reply }}</div>
            </div>
          </div>
        </div>
        
        <!-- 分页 -->
        <div v-if="total > pageSize" class="pagination-container">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            small
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.feedback-container {
  min-height: 100vh;
  background-color: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.feedback-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
}

.feedback-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  padding: 32px;
  width: 100%;
  max-width: 600px;
}

.feedback-title {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin: 0 0 24px 0;
  text-align: center;
}

.feedback-type-section {
  margin-bottom: 24px;
}

.reported-user-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #f9faff;
  border-radius: 8px;
  border-left: 4px solid #667eea;
}

.user-input-group {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.user-input-item {
  flex: 1;
  min-width: 200px;
}

.type-selector {
  width: 100%;
}

.type-select {
  width: 100%;
}

.user-input {
  width: 100%;
}

.feedback-input-section {
  margin-bottom: 24px;
}

.input-label {
  display: block;
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 12px;
}

.textarea-wrapper {
  position: relative;
}

.feedback-textarea {
  width: calc(100% - 32px);
  margin: 0 24px 0 8px;
  padding: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  font-family: inherit;
  transition: border-color 0.3s ease;
}

.feedback-textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.word-count {
  position: absolute;
  bottom: 12px;
  right: 40px;
  font-size: 12px;
  color: #909399;
}

.image-upload-section {
  margin-bottom: 24px;
}

.upload-label {
  display: block;
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 12px;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 40px 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #fafafa;
}

.upload-placeholder:hover {
  border-color: #667eea;
  background-color: #f0f2ff;
}

.upload-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.upload-text {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 12px;
  color: #909399;
}

/* 图片预览样式 */
.image-preview {
  position: relative;
  display: inline-block;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #eaeaea;
}

.image-preview img {
  max-width: 200px;
  max-height: 200px;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.image-preview img:hover {
  transform: scale(1.02);
}

.remove-image-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #f56c6c;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.remove-image-btn:hover {
  background: #f78989;
}

/* 图片预览对话框 */
.image-preview-dialog {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.image-preview-dialog img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 反馈历史对话框样式 */
.history-content {
  max-height: 500px;
  overflow-y: auto;
}

.empty-history {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feedback-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  transition: all 0.3s ease;
}

.feedback-item:hover {
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.feedback-date {
  font-size: 12px;
  color: #909399;
}

.feedback-content-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 12px;
  word-break: break-all;
}

.feedback-image {
  margin-bottom: 12px;
}

.feedback-image img {
  max-width: 150px;
  max-height: 150px;
  border-radius: 4px;
  cursor: pointer;
  transition: transform 0.3s ease;
  object-fit: cover;
}

.feedback-image img:hover {
  transform: scale(1.05);
}

.feedback-reply {
  background: #f0f9ff;
  border-left: 3px solid #409eff;
  padding: 12px;
  border-radius: 4px;
}

.reply-label {
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
  margin-bottom: 8px;
}

.reply-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.submit-section {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.submit-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.submit-btn:active {
  transform: translateY(0);
}

.history-btn {
  background: white;
  color: #667eea;
  border: 2px solid #667eea;
  border-radius: 8px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.history-btn:hover {
  background: #f0f2ff;
  transform: translateY(-2px);
}

.history-btn:active {
  transform: translateY(0);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .feedback-main {
    padding: 20px 16px;
  }
  
  .feedback-card {
    padding: 24px;
  }
  
  .feedback-title {
    font-size: 20px;
  }
  
  .upload-placeholder {
    padding: 30px 16px;
  }
  
  .upload-icon {
    font-size: 36px;
  }

  .image-preview img {
    max-width: 150px;
    max-height: 150px;
  }
}

@media (max-width: 480px) {
  .feedback-card {
    padding: 16px;
  }
  
  .feedback-title {
    font-size: 18px;
  }
  
  .feedback-textarea {
    padding: 12px;
  }
  
  .upload-placeholder {
    padding: 20px 12px;
  }
  
  .upload-icon {
    font-size: 28px;
  }
  
  .submit-btn {
    padding: 10px 24px;
    font-size: 14px;
  }

  .image-preview img {
    max-width: 120px;
    max-height: 120px;
  }
}
</style>
