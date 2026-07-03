<template>
  <div class="ai-match-container">
    <GeneralNav />

    <div class="ai-match-content">
      <!-- 左侧历史记录侧边栏 -->
      <div class="history-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <h4>匹配历史</h4>
          <div class="sidebar-header-actions">
            <el-button
                size="small"
                type="primary"
                text
                @click="newChat"
                class="new-chat-btn"
            >
              + 新对话
            </el-button>
            <el-button
                v-if="historyList.length > 0"
                size="small"
                type="danger"
                text
                @click="clearAllHistory"
                class="clear-btn"
            >
              清空
            </el-button>
          </div>
        </div>
        <div class="history-list" v-if="historyList.length > 0">
          <div
              v-for="(item, idx) in historyList"
              :key="item.id"
              class="history-item"
              :class="{ active: activeHistoryId === item.id }"
              @click="loadHistoryChat(item)"
          >
            <div class="history-query">{{ item.query }}</div>
            <div class="history-meta">
              <span>{{ item.matchCount }} 个匹配</span>
              <span>{{ formatHistoryTime(item.createdAt) }}</span>
              <el-button
                  size="small"
                  type="danger"
                  text
                  class="delete-history-btn"
                  @click.stop="deleteHistoryItem(item)"
              >
                ✕
              </el-button>
            </div>
          </div>
        </div>
        <div v-else class="history-empty">
          <span class="empty-icon">💬</span>
          <p>暂无历史记录</p>
        </div>
      </div>

      <!-- 右侧聊天主区域 -->
      <div class="chat-main">
        <!-- 顶部操作栏 -->
        <div class="chat-top-bar">
          <el-button @click="router.push('/parttime')" size="small" icon="arrow-left">返回主页</el-button>
          <el-button size="small" type="warning" @click="runGapMatch" :loading="gapLoading">
            🧩 空缺匹配
          </el-button>
          <el-button size="small" text @click="sidebarCollapsed = !sidebarCollapsed" class="toggle-sidebar-btn">
            {{ sidebarCollapsed ? '展开历史' : '收起历史' }}
          </el-button>
        </div>

        <!-- 聊天消息区 -->
        <div class="chat-messages" ref="messageList">
          <div v-if="messages.length === 0" class="welcome-message">
            <div class="welcome-icon">
              <img v-if="aiAvatar" :src="getImageUrl(aiAvatar)" class="ai-avatar-img" />
              <span v-else>🤖</span>
            </div>
            <h3>智能匹配助手</h3>
            <p v-if="isMerchant || isAdmin">告诉我你需要什么时间段的学生，我将为你精准推荐合适的人选。</p>
            <p v-else>告诉我你想找什么样的兼职，我将为你精准匹配最适合的岗位。</p>
            <div class="quick-asks">
              <el-button
                  size="small"
                  type="warning"
                  @click="runGapMatch"
                  :loading="gapLoading"
                  class="quick-ask-btn gap-btn"
              >
                🧩 智能空缺匹配
              </el-button>
              <el-button
                  v-for="ask in quickAsks"
                  :key="ask"
                  size="small"
                  @click="quickSend(ask)"
                  class="quick-ask-btn"
              >
                {{ ask }}
              </el-button>
            </div>
          </div>

          <div
              v-for="(msg, idx) in messages"
              :key="idx"
              class="message-row"
              :class="msg.role"
          >
            <div class="message-avatar">
              <img v-if="msg.role === 'ai' && aiAvatar" :src="getImageUrl(aiAvatar)" style="width:100%;height:100%;border-radius:50%;object-fit:cover" />
              <span v-else-if="msg.role === 'ai'">🤖</span>
              <span v-else>👤</span>
            </div>
            <div class="message-body">
              <div class="message-bubble" v-if="msg.role === 'user'">
                {{ msg.content }}
              </div>
              <div class="ai-bubble" v-else>
                <div v-if="msg.thinking" class="thinking-box">
                  <div class="thinking-toggle" @click="msg._showThinking = !msg._showThinking">
                    <span>{{ msg._showThinking ? '🔽 思考过程' : '▶️ 思考过程（已完成）' }}</span>
                    <span class="thinking-hint">{{ msg._showThinking ? '点击收起' : '点击展开' }}</span>
                  </div>
                  <div v-if="msg._showThinking" class="thinking-content">{{ msg.thinking }}</div>
                </div>
                <div class="ai-text">{{ msg.content }}</div>
                <!-- 多方案展示 -->
                <div v-if="msg.plans && msg.plans.length" class="plans-container">
                  <div v-for="(plan, pIdx) in msg.plans" :key="'plan'+pIdx" class="plan-group">
                    <div class="plan-header">
                      <span class="plan-badge">{{ '方案' + (pIdx+1) }}</span>
                      <span class="plan-strategy">{{ plan.strategy }}</span>
                    </div>
                    <div class="plan-description" v-if="plan.description">{{ plan.description }}</div>
                    <div class="match-cards">
                      <div v-for="(match, mIdx) in plan.matches" :key="match.type + match.id" class="match-card">
                        <div class="card-type" :class="match.type">
                          {{ match.type === 'job' ? '兼职' : '学生' }}
                        </div>
                        <div class="card-score">匹配度 {{ match.score }}%</div>
                        <div class="card-main">
                          <template v-if="match.type === 'job'">
                            <h4>{{ match.title }}</h4>
                            <p>💰 {{ match.salary }}</p>
                            <p>📍 {{ match.address }}</p>
                            <p>🕒 {{ formatJobTime(match.time) }}</p>
                          </template>
                          <template v-else>
                            <h4>{{ match.name }}</h4>
                            <p>⭐ 信用分: {{ match.credit }}</p>
                            <p>📅 {{ formatStudentTime(match.timepreference) }}</p>
                            <p>📞 {{ formatPhone(match.phone) }}</p>
                          </template>
                        </div>
                        <div class="card-reason">💡 {{ match.reason }}</div>
                        <div class="card-actions">
                          <el-button
                              v-for="action in (match.suggestedActions || [])"
                              :key="action.action"
                              size="small"
                              :type="action.action === 'contact_student' ? 'primary' : action.action === 'apply_job' ? 'success' : ''"
                              :disabled="isActionDisabled(match, action, idx, (plan._startIndex || 0) + mIdx)"
                              :loading="executingActions[`${idx}_${(plan._startIndex || 0) + mIdx}_${action.action}`]"
                              @click="executeAgentAction(match, action, idx, (plan._startIndex || 0) + mIdx)"
                          >
                            {{ match._actionExecuted && match._actionExecuted[action.action] ? '✓ 已完成' : action.label }}
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 兼容旧格式：无 plans 时用 matches -->
                <div v-else-if="msg.matches && msg.matches.length" class="match-cards">
                  <div v-for="(match, mIdx) in msg.matches" :key="match.type + match.id" class="match-card">
                    <div class="card-type" :class="match.type">
                      {{ match.type === 'job' ? '兼职' : '学生' }}
                    </div>
                    <div class="card-score">匹配度 {{ match.score }}%</div>
                    <div class="card-main">
                      <template v-if="match.type === 'job'">
                        <h4>{{ match.title }}</h4>
                        <p>💰 {{ match.salary }}</p>
                        <p>📍 {{ match.address }}</p>
                        <p>🕒 {{ formatJobTime(match.time) }}</p>
                      </template>
                      <template v-else>
                        <h4>{{ match.name }}</h4>
                        <p>⭐ 信用分: {{ match.credit }}</p>
                        <p>📅 {{ formatStudentTime(match.timepreference) }}</p>
                        <p>📞 {{ formatPhone(match.phone) }}</p>
                      </template>
                    </div>
                    <div class="card-reason">💡 {{ match.reason }}</div>
                    <div class="card-actions">
                      <el-button
                          v-for="action in (match.suggestedActions || [])"
                          :key="action.action"
                          size="small"
                          :type="action.action === 'contact_student' ? 'primary' : action.action === 'apply_job' ? 'success' : ''"
                          :disabled="isActionDisabled(match, action, idx, mIdx)"
                          :loading="executingActions[`${idx}_${mIdx}_${action.action}`]"
                          @click="executeAgentAction(match, action, idx, mIdx)"
                      >
                        {{ match._actionExecuted && match._actionExecuted[action.action] ? '✓ 已完成' : action.label }}
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 学生详情弹窗 -->
        <el-dialog v-model="studentDetailVisible" title="学生详情" width="400px">
          <div v-if="currentStudentDetail" class="student-detail-content">
            <p><strong>姓名：</strong>{{ currentStudentDetail.name }}</p>
            <p><strong>信用分：</strong>⭐ {{ currentStudentDetail.credit || '-' }}</p>
            <p><strong>电话：</strong>{{ currentStudentDetail.phone || '未绑定' }}</p>
            <p><strong>空闲时间：</strong>{{ formatStudentTime(currentStudentDetail.timepreference) }}</p>
            <p><strong>匹配度：</strong>{{ currentStudentDetail.score }}%</p>
            <p><strong>匹配理由：</strong>{{ currentStudentDetail.reason }}</p>
          </div>
          <template #footer>
            <el-button @click="studentDetailVisible = false">关闭</el-button>
          </template>
        </el-dialog>

        <!-- 输入区域 -->
        <div class="chat-input">
          <el-input
              v-model="inputMessage"

              placeholder="描述您想要的兼职或学生..."
              @keyup.enter="sendMessage"
              :disabled="loading || gapLoading"
              type="textarea"
              :rows="2"
              resize="none"
          >
            <template #append>
              <el-button @click="sendMessage" :loading="loading" type="primary">
                {{ loading ? '思考中...' : '发送' }}
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import SessionManager from '../utils/SessionManager.js'
import GeneralNav from '../nav/GeneralNav.vue'

const router = useRouter()

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messageList = ref(null)

const sidebarCollapsed = ref(false)
const historyList = ref([])
const activeHistoryId = ref(null)

const currentUser = ref(null)
const isMerchant = ref(false)
const isStudent = ref(false)
const isAdmin = ref(false)
const quickAsks = ref([])

const aiAvatar = ref('')
const currentSessionId = ref(null)
const executingActions = ref({})
const gapLoading = ref(false)
const aiAbortController = ref(null)

const merchantJobs = ref([])

const studentDetailVisible = ref(false)
const currentStudentDetail = ref(null)

const fetchMerchantJobs = async () => {
  if (!isMerchant.value || !currentUser.value) return
  try {
    const res = await axios.get('/api/job/my', { params: { userId: currentUser.value.id } })
    if (res.data.success && res.data.data) {
      merchantJobs.value = res.data.data.filter(j => j.status !== '已招满')
    }
  } catch {}
}

// 加载用户信息并设置快捷提问
const loadUserInfo = () => {
  const user = SessionManager.getCurrentUserInfo()
  if (user) {
    currentUser.value = user
    isMerchant.value = user.identity === '商户'
    isStudent.value = user.identity === '学生'
    isAdmin.value = user.identity === '管理员'

    if (isMerchant.value || isAdmin.value) {
      quickAsks.value = [
        '推荐空闲的学生',
        '推荐信用分高的学生',
        '找周一到周五有空的学生'
      ]
    } else {
      quickAsks.value = [
        '想找一个周一到周五下午的兼职',
        '推荐适合我的日结兼职',
        '找周末的兼职'
      ]
    }
  } else {
    router.push('/userlogin')
  }
}

// 获取 AI 头像
const getImageUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  if (path.startsWith('/uploads/')) return `http://localhost:8082${path}`
  return `http://localhost:8082/uploads${path}`
}

const fetchAiAvatar = async () => {
  try {
    const res = await axios.get('/api/matching/ai-avatar')
    if (res.data.success && res.data.data) {
      aiAvatar.value = res.data.data
    }
  } catch {}
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  })
}

// 格式化电话
const formatPhone = (phone) => {
  if (!phone) return '未绑定'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

// 格式化兼职时间
const formatJobTime = (timeJson) => {
  if (!timeJson) return '未设置'
  try {
    const obj = typeof timeJson === 'string' ? JSON.parse(timeJson) : timeJson
    if (obj.timeSlots) {
      const dayMap = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
      const periodMap = { morning: '上午', afternoon: '下午', evening: '晚上' }
      return obj.timeSlots.map(s => dayMap[s.day] + periodMap[s.period]).join('、')
    }
  } catch {}
  return timeJson || '未设置'
}

// 格式化学生时间
const formatStudentTime = (timepref) => {
  if (!timepref) return '未设置'
  try {
    const arr = typeof timepref === 'string' ? JSON.parse(timepref) : timepref
    const dayMap = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
    const periodMap = { 1: '上午', 2: '下午', 3: '晚上', morning: '上午', afternoon: '下午', evening: '晚上' }
    arr.sort((a, b) => a.day - b.day)
    return arr.map(s => `${dayMap[s.day]} ${periodMap[s.period] || s.period}`).join(' ')
  } catch { return timepref }
}

// 格式化历史时间
const formatHistoryTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (minutes < 60) return `${Math.max(1, minutes)}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

// 加载历史记录
const loadHistory = async () => {
  try {
    const userId = currentUser.value.id
    const res = await axios.get('/api/matching/history', { params: { userId } })
    if (res.data.success) {
      historyList.value = res.data.data || []
    }
  } catch (e) {
    console.error('获取历史记录失败:', e)
  }
}

// 新建对话
const newChat = () => {
  messages.value = []
  activeHistoryId.value = null
  inputMessage.value = ''
  scrollToBottom()
}

// 快捷发送
const quickSend = (text) => {
  inputMessage.value = text
  sendMessage()
}

// 发送消息
const sendMessage = async () => {
  const text = inputMessage.value.trim();
  if (!text || loading.value || gapLoading.value) return;

  let enhancedText = text;
  if (isMerchant.value) enhancedText = `我是商户，我想找空闲学生：${text}`;
  else if (isStudent.value) enhancedText = `我是学生，我想找兼职工作：${text}`;

  messages.value.push({ role: 'user', content: text });
  inputMessage.value = '';
  loading.value = true;
  scrollToBottom();

  const aiMsgIndex = messages.value.length;
  messages.value.push({
    role: 'ai',
    thinking: '',
    _showThinking: true,
    content: '🤔 正在分析你的需求并生成方案推荐...',
    plans: [],
    matches: []
  });
  const currentAiMsg = messages.value[aiMsgIndex];

  const abortCtrl = new AbortController();
  aiAbortController.value = abortCtrl;

  try {
    const url = `/api/matching/ai-chat-stream?message=${encodeURIComponent(enhancedText)}&userId=${currentUser.value.id}`;
    const token = SessionManager.getCurrentToken();
    const response = await fetch(url, {
      method: 'GET',
      signal: abortCtrl.signal,
      headers: { 'Accept': 'text/event-stream', 'Authorization': token ? `Bearer ${token}` : '' }
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let eventType = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // 按行处理 SSE 格式
      const lines = buffer.split('\n');
      buffer = lines.pop() || ''; // 保留不完整的行

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (eventType === 'final') {
            const finalData = JSON.parse(data);
            if (finalData.matches && finalData.matches.length > 0) {
              currentAiMsg.matches = finalData.matches || [];
              currentAiMsg.content = `为你找到 ${finalData.matches.length} 个匹配项。`;
              currentAiMsg.plans = [];  // 清空 plans
            } else if (finalData.content) {
              currentAiMsg.content = finalData.content;
              currentAiMsg.matches = [];
              currentAiMsg.plans = [];
            } else {
              currentAiMsg.content = '暂时没有找到匹配的结果。';
              currentAiMsg.matches = [];
              currentAiMsg.plans = [];
            }
            currentAiMsg._showThinking = false;
            currentSessionId.value = finalData.sessionId;
            scrollToBottom();
          }
          eventType = ''; // 重置
        }
      }
    }
  } catch (err) {
    if (err.name !== 'AbortError') {
      currentAiMsg.content = '服务暂时不可用，请稍后再试。';
    }
  } finally {
    loading.value = false;
    aiAbortController.value = null;
    scrollToBottom();
  }
};

const runGapMatch = async () => {
  if (gapLoading.value) return
  gapLoading.value = true
  messages.value.push({ role: 'user', content: '帮我分析一下空缺时段，寻找合适的匹配' })
  scrollToBottom()

  const aiMsgIndex = messages.value.length
  messages.value.push({
    role: 'ai',
    thinking: '',
    _showThinking: true,
    content: '🤔 正在分析空缺时段并寻找最佳匹配...',
    plans: [],
    matches: []
  })
  const currentAiMsg = messages.value[aiMsgIndex]

  const abortCtrl = new AbortController()
  aiAbortController.value = abortCtrl

  try {
    const userId = currentUser.value.id
    const res = await axios.post('/api/matching/gap-match', {
      userId: String(userId)
    }, { signal: abortCtrl.signal })

    if (res.data.success) {
        const data = res.data.data
        currentSessionId.value = data.sessionId || null
        const matchList = data.matches || []
        const plans = data.plans || []
        const summary = data.summary || ''
        const hint = data.hint || ''
        const thinking = data.thinking || ''

        if (matchList.length > 0) {
          currentAiMsg.content = `${summary}\n\n为你找到 ${matchList.length} 个填补匹配项：`
          currentAiMsg.matches = matchList
          currentAiMsg.plans = plans
          currentAiMsg.thinking = thinking
          currentAiMsg._showThinking = thinking ? false : false
        } else {
          currentAiMsg.content = summary + (hint ? '\n\n' + hint : '')
          currentAiMsg.matches = []
          currentAiMsg.plans = []
        }
    } else {
      currentAiMsg.content = res.data.message || '空缺匹配暂时不可用'
      currentAiMsg.matches = []
      currentAiMsg.plans = []
    }
    loadHistory()
  } catch (e) {
    if (e.name === 'AbortError' || e.code === 'ERR_CANCELED') {
      currentAiMsg.content = '操作已取消。'
      currentAiMsg.matches = []
      currentAiMsg.plans = []
    } else {
      currentAiMsg.content = '空缺匹配服务暂时不可用。'
      currentAiMsg.matches = []
      currentAiMsg.plans = []
    }
  } finally {
    gapLoading.value = false
    aiAbortController.value = null
    scrollToBottom()
  }
}

const executeAgentAction = async (match, action, msgIdx, matchIdx) => {
  if (isActionDisabled(match, action, msgIdx, matchIdx)) return

  if (action.action === 'view_detail') {
    if (match.type === 'student') {
      currentStudentDetail.value = match
      studentDetailVisible.value = true
    } else {
      window.open(match.url || `/#/job/${match.id}`, '_blank')
    }
    return
  }

  if (!currentSessionId.value) {
    ElMessage.warning('会话已过期，请重新搜索')
    return
  }

  let extraParams = null
  let confirmMsg = `确认执行「${action.label}」？\n\n${action.description || ''}`

  if (action.action === 'contact_student' && isMerchant.value && merchantJobs.value.length > 0) {
    try {
      const jobOptions = merchantJobs.value.map(j => ({ label: j.title, value: j.id }))
      const jobLabels = jobOptions.map((j, i) => `${i + 1}. ${j.label}`).join('\n')
      const input = await ElMessageBox.prompt(
        `选择推荐兼职（输入序号，0跳过后直接联系）：\n\n${jobLabels}`,
        '选择兼职',
        { confirmButtonText: '确认', cancelButtonText: '取消', inputPattern: /^\d+$/, inputErrorMessage: '请输入数字序号' }
      )
      if (input && input.value) {
        const idx = parseInt(input.value)
        if (idx > 0 && idx <= merchantJobs.value.length) {
          const selectedJob = merchantJobs.value[idx - 1]
          extraParams = { jobId: selectedJob.id, jobTitle: selectedJob.title }
          confirmMsg = `将联系「${match.name}」并推荐兼职【${selectedJob.title}】\n\n确认执行？`
        }
      }
    } catch { return }
  }

  try {
    await ElMessageBox.confirm(confirmMsg, '操作确认',
      { confirmButtonText: '确认执行', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }

  const key = `${msgIdx}_${matchIdx}_${action.action}`
  executingActions.value[key] = true

  try {
    const res = await axios.post('/api/matching/execute-action', {
      sessionId: currentSessionId.value,
      matchIndex: matchIdx,
      actionName: action.action,
      extraParams: extraParams
    })

    if (res.data.success) {
      ElMessage.success(res.data.message || '操作成功')
      match._actionExecuted = match._actionExecuted || {}
      match._actionExecuted[action.action] = true
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作执行失败，请稍后重试')
  } finally {
    executingActions.value[key] = false
  }
}

const isActionDisabled = (match, action, msgIdx, matchIdx) => {
  const key = `${msgIdx}_${matchIdx}_${action.action}`
  if (executingActions.value[key]) return true
  if (match._actionExecuted && match._actionExecuted[action.action]) return true
  return false
}

const loadHistoryChat = (historyItem) => {
  activeHistoryId.value = historyItem.id
  messages.value = []
  messages.value.push({ role: 'user', content: historyItem.query })
  if (historyItem.result) {
    let matches = []
    try {
      matches = typeof historyItem.result === 'string'
          ? JSON.parse(historyItem.result)
          : historyItem.result
    } catch {}
    messages.value.push({
      role: 'ai',
      content: matches.length > 0
          ? `上次为你找到 ${matches.length} 个匹配项：`
          : '上次没有找到匹配的结果。',
      matches: matches
    })
  }
  scrollToBottom()
}

const deleteHistoryItem = async (item) => {
  try {
    await ElMessageBox.confirm('确定删除这条匹配历史？', '确认删除', { type: 'warning' })
  } catch { return }
  try {
    await axios.delete(`/api/matching/history/${item.id}`)
    historyList.value = historyList.value.filter(h => h.id !== item.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

const clearAllHistory = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有匹配历史？', '确认清空', { type: 'warning' })
  } catch { return }
  try {
    await axios.delete('/api/matching/history', { params: { userId: currentUser.value.id } })
    historyList.value = []
    ElMessage.success('已全部清空')
  } catch {
    ElMessage.error('清空失败')
  }
}

onMounted(() => {
  loadUserInfo()
  loadHistory()
  fetchAiAvatar()
  fetchMerchantJobs()
})

onBeforeUnmount(() => {
  if (aiAbortController.value) {
    aiAbortController.value.abort()
    aiAbortController.value = null
  }
})
</script>

<style scoped>
.ai-match-container {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.ai-match-content {
  display: flex;
  flex: 1;
  overflow: hidden;
  height: calc(100vh - 60px);
}

/* 左侧历史侧边栏 */
.history-sidebar {
  width: 260px;
  min-width: 260px;
  background: white;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  transition: all 0.3s;
}

.history-sidebar.collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border-right: none;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.sidebar-header h4 {
  margin: 0;
  font-size: 15px;
  color: #333;
}

.new-chat-btn {
  font-size: 12px;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.history-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.history-item:hover {
  background: #f5f7fa;
  border-color: #e0e0e0;
}

.history-item.active {
  background: #e6f0ff;
  border-color: #b3d4ff;
}

.history-query {
  font-size: 13px;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 6px;
}

.history-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #999;
}

.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #999;
}

.history-empty .empty-icon {
  font-size: 32px;
  margin-bottom: 10px;
}

.history-empty p {
  font-size: 13px;
  margin: 0;
}

/* 右侧聊天区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  min-width: 0;
}

.chat-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.toggle-sidebar-btn {
  font-size: 12px;
  color: #666;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  margin: auto;
}

.welcome-message .welcome-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.ai-avatar-img {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #e0e0e0;
}

.welcome-message h3 {
  font-size: 20px;
  color: #333;
  margin: 0 0 8px 0;
}

.welcome-message p {
  font-size: 14px;
  color: #666;
  margin: 0 0 20px;
  max-width: 400px;
}

.quick-asks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-ask-btn {
  font-size: 12px;
}

/* 消息行 */
.message-row {
  display: flex;
  gap: 10px;
  max-width: 85%;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-row.ai {
  align-self: flex-start;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-row.user .message-avatar {
  background: #e6f0ff;
}

.message-row.ai .message-avatar {
  background: #f0f0f0;
  overflow: hidden;
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-bubble {
  background: #667eea;
  color: white;
  padding: 10px 14px;
  border-radius: 12px 12px 4px 12px;
  font-size: 14px;
  white-space: pre-wrap;
}

.ai-bubble {
  padding: 6px 0;
}

.ai-text {
  background: #f0f2f5;
  padding: 10px 14px;
  border-radius: 12px 12px 12px 4px;
  font-size: 14px;
  color: #333;
}

.thinking-box {
  margin-bottom: 6px;
}
.thinking-toggle {
  cursor: pointer;
  padding: 4px 0;
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 8px;
  user-select: none;
}
.thinking-toggle:hover { color: #666; }
.thinking-hint { font-size: 11px; color: #ccc; }
.thinking-content {
  background: #f7f7f7;
  border-left: 3px solid #d9d9d9;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 4px;
  max-height: 200px;
  overflow-y: auto;
  animation: thinkingFadeIn 0.3s ease;
}
@keyframes thinkingFadeIn {
  from { opacity: 0; max-height: 0; }
  to { opacity: 1; max-height: 200px; }
}

/* 多方案容器 */
.plans-container {
  margin-top: 12px;
}
.plan-group {
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  overflow: hidden;
  background: #fafafa;
  width: 100%;
}
.plan-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.plan-badge {
  background: rgba(255,255,255,0.25);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  padding: 2px 10px;
  border-radius: 12px;
  white-space: nowrap;
}
.plan-strategy {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.plan-description {
  padding: 8px 14px;
  font-size: 13px;
  color: #666;
  background: #f5f5f5;
  border-bottom: 1px solid #eee;
}
.plan-group .match-cards {
  padding: 8px;
}

/* 匹配结果卡片 */
.match-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.match-card {
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 14px;
  width: calc(50% - 6px);
  min-width: 240px;
  position: relative;
}

.card-type {
  font-size: 11px;
  font-weight: bold;
  padding: 2px 8px;
  border-radius: 10px;
  display: inline-block;
  margin-bottom: 6px;
}

.card-type.job {
  background: #e6f7ff;
  color: #1890ff;
}

.card-type.student {
  background: #f6ffed;
  color: #52c41a;
}

.card-score {
  position: absolute;
  top: 14px;
  right: 14px;
  color: #fa8c16;
  font-weight: bold;
  font-size: 13px;
}

.card-main h4 {
  margin: 0 0 6px;
  font-size: 15px;
}

.card-main p {
  margin: 3px 0;
  font-size: 12px;
  color: #666;
}

.card-reason {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}

.card-actions {
  margin-top: 10px;
}

/* 输入区域 */
.chat-input {
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

/* 滚动条 */
.chat-messages::-webkit-scrollbar,
.history-list::-webkit-scrollbar {
  width: 5px;
}

.chat-messages::-webkit-scrollbar-thumb,
.history-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}

/* 响应式 */
@media (max-width: 768px) {
  .history-sidebar {
    width: 100%;
    min-width: 100%;
    position: absolute;
    z-index: 10;
    height: calc(100vh - 60px);
  }

  .history-sidebar.collapsed {
    width: 0;
    min-width: 0;
  }

  .match-card {
    width: 100%;
  }

  .message-row {
    max-width: 95%;
  }
}
.student-detail-content p {
  margin: 10px 0;
  font-size: 15px;
}
</style>