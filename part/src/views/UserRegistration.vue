<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SessionManager from '../utils/SessionManager.js'
import request from '../services/request.js'

const router = useRouter()

// 表单数据
const form = ref({
  input: '',
  password: '',
  confirmPassword: ''
})

// 注册处理函数
const handleRegister = async () => {
  // 表单验证
  if (!form.value.input) {
    alert('用户名/手机号不能为空')
    return
  }
  
  if (!form.value.password) {
    alert('密码不能为空')
    return
  }
  
  if (form.value.password !== form.value.confirmPassword) {
    alert('两次输入的密码不一致')
    return
  }
  
  try {
    const data = await request('/api/user/register', {
      method: 'POST',
      body: JSON.stringify({
        input: form.value.input,
        password: form.value.password
      })
    })
    
    if (data.success) {
      console.log('注册成功，开始自动登录')
      try {
        const loginData = await request('/api/user/login', {
          method: 'POST',
          body: JSON.stringify({
            input: form.value.input,
            password: form.value.password
          })
        })
        console.log('登录API返回:', loginData)
        if (loginData.success) {
          console.log('自动登录成功，设置localStorage和SessionManager')
          // 使用SessionManager保存会话
          SessionManager.saveSession(loginData.user, loginData.token)
          // 同时设置localStorage作为备用
          localStorage.setItem('token', loginData.token)
          localStorage.setItem('userInfo', JSON.stringify(loginData.user))
          localStorage.setItem('justRegistered', 'true')
          console.log('设置完成，准备跳转到parttime')
          console.log('localStorage内容:', {
            token: localStorage.getItem('token'),
            userInfo: localStorage.getItem('userInfo'),
            justRegistered: localStorage.getItem('justRegistered')
          })
          console.log('SessionManager状态:', {
            isLoggedIn: SessionManager.isLoggedIn(),
            user: SessionManager.getCurrentUserInfo()
          })
          router.push('/parttime')
        } else {
          console.log('自动登录失败，跳转到登录页面')
          router.push('/userlogin')
        }
      } catch (error) {
        console.error('自动登录失败:', error)
        router.push('/userlogin')
      }
    } else {
      // 注册失败
      alert(data.message || '注册失败，请稍后重试')
    }
  } catch (error) {
    console.error('注册失败:', error)
    alert('注册失败，请稍后重试')
  }
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/userlogin')
}
</script>

<template>
  <div class="login-container">
    <!-- 欢迎文字 -->
    <div class="welcome-text">
      <h1>欢迎注册校园兼职平台</h1>
    </div>
    
    <!-- 注册框 -->
    <div class="login-box">
      <!-- 注册表单 -->
      <div class="login-form">
        <div class="input-group">
          <input 
            type="text" 
            v-model="form.input"
            placeholder="手机号码/用户名注册"
            class="login-input"
          >
        </div>
        
        <div class="input-group">
          <input 
            type="password" 
            v-model="form.password"
            placeholder="密码"
            class="login-input"
          >
        </div>
        
        <div class="input-group">
          <input 
            type="password" 
            v-model="form.confirmPassword"
            placeholder="确认密码"
            class="login-input"
          >
        </div>
        
        <!-- 登录链接 -->
        <div class="form-links">
          <a href="#" class="link" @click.prevent="goToLogin">已有账号？去登录</a>
        </div>
        
        <button class="login-btn" @click="handleRegister">
          注册
        </button>
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
  padding: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(102, 126, 234, 0.4);
}

.login-btn:active {
  transform: translateY(0);
}

/* 响应式设计 */
@media (max-width: 500px) {
  .welcome-text h1 {
    font-size: 2rem;
  }
  
  .login-box {
    padding: 20px;
  }
}
</style>