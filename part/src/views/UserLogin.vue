<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SessionManager from '../utils/SessionManager.js'
import request from '../services/request.js'

const router = useRouter()

// 表单数据
const form = ref({
  input: '',
  password: ''
})

// 登录处理函数
const handleLogin = async () => {
  // 表单验证
  if (!form.value.input) {
    ElMessage.warning('用户名/手机号不能为空')
    return
  }
  
  if (!form.value.password) {
    ElMessage.warning('密码不能为空')
    return
  }
  
  try {
    const data = await request('/api/user/login', {
      method: 'POST',
      body: JSON.stringify({
        input: form.value.input,
        password: form.value.password
      })
    })
    
    if (data.success) {
      // 登录成功，使用SessionManager保存会话
      SessionManager.saveSession(data.user, data.token)
      
      console.log('登录成功，用户身份:', data.user.identity)
      console.log('准备跳转到:', data.user.identity === '管理员' ? '/admin' : '/parttime')
      
      // 检查sessionStorage状态
      console.log('sessionStorage current_user:', sessionStorage.getItem('current_user'))
      console.log('sessionStorage current_token:', sessionStorage.getItem('current_token'))
      console.log('SessionManager.isLoggedIn():', SessionManager.isLoggedIn())
      
      ElMessage.success('登录成功')
      
      // 尝试跳转
      try {
        if (data.user.identity === '管理员') {
          console.log('执行跳转到 /admin')
          router.push('/admin')
        } else {
          console.log('执行跳转到 /parttime')
          router.push('/parttime')
        }
      } catch (error) {
        console.error('跳转失败:', error)
        ElMessage.error('跳转失败，请手动访问目标页面')
      }
    } else {
      // 登录失败
      ElMessage.error(data.message || '用户名/手机号或密码有误')
    }
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error('登录失败，请稍后重试')
  }
}

// 跳转到注册页面
const goToRegistration = () => {
  router.push('/userregistration')
}

// 跳转到忘记密码页面
const goToForgotPassword = () => {
  router.push('/forgotpassword')
}
</script>

<template>
  <div class="login-container">
    <!-- 欢迎文字 -->
    <div class="welcome-text">
      <h1>欢迎来到校园兼职平台</h1>
    </div>
    
    <!-- 登录框 -->
    <div class="login-box">
      <!-- 登录表单 -->
      <div class="login-form">
        <div class="input-group">
          <input 
            type="text" 
            v-model="form.input"
            placeholder="请输入用户名或手机号"
            class="login-input"
          >
        </div>
        
        <div class="input-group">
          <input 
            type="password" 
            v-model="form.password"
            placeholder="请输入密码"
            class="login-input"
          >
        </div>
        
        <!-- 注册和忘记密码链接 -->
        <div class="form-links">
          <a href="#" class="link" @click.prevent="goToRegistration">注册</a>
          <a href="#" class="link" @click.prevent="goToForgotPassword">忘记密码</a>
        </div>
        
        <button class="login-btn" @click="handleLogin">
          登录
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

.login-type-selector {
  display: flex;
  margin-bottom: 30px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f5;
}

.type-btn {
  flex: 1;
  padding: 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  color: #666;
  transition: all 0.3s ease;
}

.type-btn.active {
  background: #667eea;
  color: white;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-links {
  display: flex;
  justify-content: space-between;
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