<template>
  <div class="evaluation-page">
    <GeneralNav />
    
    <div class="evaluation-content">
      <h2 class="page-title">评价管理</h2>
      
      <!-- 待评价 -->
      <div class="pending-evaluations">
        <h3>待评价</h3>
        <div v-if="pendingEvaluations.length === 0" class="empty-evaluations">
          <span class="empty-icon">📝</span>
          <p>暂无待评价的工作</p>
        </div>
        <div v-else class="evaluation-list">
          <div v-for="item in pendingEvaluations" :key="item.id" class="evaluation-card">
            <div class="evaluation-header">
              <h4>{{ item.jobTitle }}</h4>
              <span class="work-date">{{ item.workDate }}</span>
            </div>
            
            <div class="evaluation-info">
              <p><span class="label">工作对象：</span>{{ item.otherPartyName }}</p>
              <p><span class="label">工作时间：</span>{{ getTimeSlotText(item.timeSlot) }}</p>
              <p><span class="label">工作地点：</span>{{ item.address }}</p>
            </div>
            
            <div class="evaluation-form">
              <el-form label-width="80px">
                <el-form-item label="评分">
                  <div class="rating-stars">
                    <span 
                      v-for="star in 5" 
                      :key="star"
                      class="star" 
                      :class="{ active: rating >= star }"
                      @click="rating = star"
                    >
                      ★
                    </span>
                  </div>
                </el-form-item>
                
                <el-form-item label="评价内容">
                  <el-input
                    v-model="comment"
                    type="textarea"
                    placeholder="请输入评价内容"
                    rows="4"
                  />
                </el-form-item>
                
                <el-form-item>
                  <el-button type="primary" @click="submitEvaluation(item)">
                    提交评价
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 历史评价 -->
      <div class="history-evaluations">
        <h3>历史评价</h3>
        <el-table :data="historyEvaluations" style="width: 100%">
          <el-table-column prop="jobTitle" label="工作名称" width="200" />
          <el-table-column prop="workDate" label="工作日期" width="180" />
          <el-table-column prop="otherPartyName" label="评价对象" width="150" />
          <el-table-column prop="rating" label="评分" width="100">
            <template #default="scope">
              <div class="table-rating">
                <span 
                  v-for="star in 5" 
                  :key="star"
                  class="star" 
                  :class="{ active: scope.row.rating >= star }"
                >
                  ★
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="评价内容" />
          <el-table-column prop="createdAt" label="评价时间" width="180" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElButton, ElInput, ElForm, ElFormItem, ElTable, ElTableColumn } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
import GeneralNav from '../nav/GeneralNav.vue'

const pendingEvaluations = ref([])
const historyEvaluations = ref([])
const rating = ref(5)
const comment = ref('')

// 加载待评价
const loadPendingEvaluations = async () => {
  try {
    const userId = SessionManager.getCurrentUserInfo().id
    const response = await axios.get(`http://localhost:8082/api/evaluation/pending`, {
      params: { userId: userId }
    })
    if (response.data.success) {
      pendingEvaluations.value = response.data.data
    }
  } catch (error) {
    console.error('加载待评价失败:', error)
  }
}

// 加载历史评价
const loadHistoryEvaluations = async () => {
  try {
    const userId = SessionManager.getCurrentUserInfo().id
    const response = await axios.get(`http://localhost:8082/api/evaluation/history`, {
      params: { userId: userId }
    })
    if (response.data.success) {
      historyEvaluations.value = response.data.data
    }
  } catch (error) {
    console.error('加载历史评价失败:', error)
  }
}

// 提交评价
const submitEvaluation = async (item) => {
  if (rating.value === 0) {
    ElMessage.warning('请选择评分')
    return
  }
  
  try {
    const response = await axios.post('http://localhost:8082/api/evaluation/submit', {
      evaluatorId: SessionManager.getCurrentUserInfo().id,
      evaluatedId: item.otherPartyId,
      jobId: item.jobId,
      workAssignmentId: item.id,
      rating: rating.value,
      comment: comment.value,
      isPositive: rating.value >= 3
    })
    
    if (response.data.success) {
      ElMessage.success('评价成功')
      // 重置表单
      rating.value = 5
      comment.value = ''
      // 重新加载数据
      loadPendingEvaluations()
      loadHistoryEvaluations()
    } else {
      ElMessage.error(response.data.message || '评价失败')
    }
  } catch (error) {
    console.error('提交评价失败:', error)
    ElMessage.error('提交评价失败')
  }
}

// 获取时间段文本
const getTimeSlotText = (timeSlot) => {
  const slotMap = {
    'morning': '上午',
    'afternoon': '下午',
    'evening': '晚上'
  }
  return slotMap[timeSlot] || timeSlot
}

onMounted(() => {
  loadPendingEvaluations()
  loadHistoryEvaluations()
})
</script>

<style scoped>
.evaluation-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.evaluation-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 20px;
}

.page-title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}

.pending-evaluations {
  background: white;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 30px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.pending-evaluations h3 {
  margin-bottom: 20px;
  color: #333;
}

.empty-evaluations {
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.evaluation-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(500px, 1fr));
  gap: 20px;
}

.evaluation-card {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 20px;
  background: #f9f9f9;
}

.evaluation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.evaluation-header h4 {
  margin: 0;
  color: #333;
}

.work-date {
  font-size: 14px;
  color: #666;
}

.evaluation-info {
  margin-bottom: 20px;
  line-height: 1.6;
  font-size: 14px;
  color: #666;
}

.evaluation-info .label {
  font-weight: bold;
  color: #333;
}

.evaluation-form {
  border-top: 1px solid #e8e8e8;
  padding-top: 20px;
}

.rating-stars {
  display: flex;
  gap: 10px;
}

.star {
  font-size: 24px;
  color: #d9d9d9;
  cursor: pointer;
  transition: color 0.3s;
}

.star.active {
  color: #fadb14;
}

.history-evaluations {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.history-evaluations h3 {
  margin-bottom: 20px;
  color: #333;
}

.table-rating {
  display: flex;
  gap: 4px;
}

.table-rating .star {
  font-size: 16px;
}
</style>