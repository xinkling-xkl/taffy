<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const currentStep = ref(1)
const form = ref({
  input: '',
  auxiliary: '',
  newPassword: '',
  confirmPassword: ''
})
const foundUser = ref(null)
const loading = ref(false)

const handleSearchUser = async () => {
  if (!form.value.input) {
    alert('请输入用户名或手机号')
    return
  }
  
  loading.value = true
  
  try {
    const response = await axios.post('http://localhost:8082/api/user/search', {
      input: form.value.input,
      auxiliary: form.value.auxiliary
    })
    
    if (response.data.success && response.data.user) {
      foundUser.value = response.data.user
      currentStep.value = 2
    } else {
      alert('未找到您的用户信息')
    }
  } catch (error) {
    console.error('查询用户失败:', error)
    alert('查询失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleResetPassword = async () => {
  if (!form.value.newPassword) {
    alert('请输入新密码')
    return
  }
  
  if (form.value.newPassword.length < 6) {
    alert('密码长度不能少于6位')
    return
  }
  
  if (form.value.newPassword !== form.value.confirmPassword) {
    alert('两次输入的密码不一致')
    return
  }
  
  loading.value = true
  
  try {
    const response = await axios.post('http://localhost:8082/api/user/reset-password', {
      userId: foundUser.value.id,
      newPassword: form.value.newPassword
    })
    
    if (response.data.success) {
      alert('密码重置成功')
      router.push('/userlogin')
    } else {
      alert(response.data.message || '密码重置失败')
    }
  } catch (error) {
    console.error('重置密码失败:', error)
    alert('重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/userlogin')
}
</script>

<template>
  <div class="login-container">
    <div class="welcome-text">
      <h1>找回密码</h1>
    </div>
    
    <div class="login-box">
      <div class="login-form">
        <div v-if="currentStep === 1">
          <div class="step-title">第一步：验证身份</div>
          
          <div class="input-group">
            <input 
              type="text" 
              v-model="form.input"
              placeholder="请输入您的用户名/手机号"
              class="login-input"
            >
          </div>
          
          <div class="input-group">
            <input 
              type="text" 
              v-model="form.auxiliary"
              placeholder="辅助验证（学号/实名/身份证号）可选"
              class="login-input"
            >
          </div>
          
          <div class="button-group single">
            <button class="login-btn" @click="handleSearchUser" :disabled="loading">
              {{ loading ? '查询中...' : '查找用户' }}
            </button>
          </div>
        </div>
        
        <div v-else-if="currentStep === 2">
          <div class="step-title">第二步：重置密码</div>
          
          <div class="user-info" v-if="foundUser">
            <div class="info-item">
              <span class="label">用户名：</span>
              <span>{{ foundUser.name }}</span>
            </div>
            <div class="info-item" v-if="foundUser.phone">
              <span class="label">手机号：</span>
              <span>{{ foundUser.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') }}</span>
            </div>
          </div>
          
          <div class="input-group">
            <input 
              type="password" 
              v-model="form.newPassword"
              placeholder="请输入新密码"
              class="login-input"
            >
          </div>
          
          <div class="input-group">
            <input 
              type="password" 
              v-model="form.confirmPassword"
              placeholder="请确认新密码"
              class="login-input"
            >
          </div>
          
          <div class="button-group">
            <button class="back-btn" @click="currentStep = 1">
              返回上一步
            </button>
            <button class="login-btn" @click="handleResetPassword" :disabled="loading">
              {{ loading ? '重置中...' : '重置密码' }}
            </button>
          </div>
        </div>
        
        <div class="form-links">
          <a href="#" class="link" @click.prevent="goToLogin">想起密码？去登录</a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  box-sizing: border-box;
}

.welcome-text {
  text-align: center;
  margin-bottom: 20px;
}

.welcome-text h1 {
  font-size: 2.5rem;
  color: white;
  font-weight: 600;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.login-box {
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  padding: 30px;
  width: 100%;
  max-width: 450px;
  box-sizing: border-box;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.step-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  text-align: center;
  margin-bottom: 10px;
}

.user-info {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.info-item:last-child {
  margin-bottom: 0;
}

.info-item .label {
  font-weight: 500;
  color: #666;
  margin-right: 8px;
}

.form-links {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 10px 0;
}

.link {
  color: #667eea;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.3s ease;
}

.link:hover {
  color: #764ba2;
  text-decoration: underline;
}

.input-group {
  display: flex;
  flex-direction: column;
}

.login-input {
  padding: 14px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 12px;
  font-size: 16px;
  transition: border-color 0.3s ease;
}

.login-input:focus {
  outline: none;
  border-color: #667eea;
}

.login-btn {
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(102, 126, 234, 0.4);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.button-group {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 10px;
}

.button-group.single {
  justify-content: center;
}

.back-btn {
  padding: 12px 24px;
  background: white;
  color: #667eea;
  border: 2px solid #667eea;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: #f5f7fa;
}
</style>
