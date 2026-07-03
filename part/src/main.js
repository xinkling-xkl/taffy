import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import axios from 'axios';
import SessionManager from './utils/SessionManager.js';


const app = createApp(App);

// 修复ResizeObserver循环错误的根本解决方案
const originalResizeObserver = window.ResizeObserver;
window.ResizeObserver = class extends originalResizeObserver {
    constructor(callback) {
        const wrappedCallback = (entries, observer) => {
            // 使用requestAnimationFrame来避免循环错误
            requestAnimationFrame(() => {
                try {
                    callback(entries, observer);
                } catch (e) {
                    // 忽略ResizeObserver相关的错误
                    if (e.message === 'ResizeObserver loop completed with undelivered notifications.') {
                        return;
                    }
                    throw e;
                }
            });
        };
        super(wrappedCallback);
    }
};

// 抑制ResizeObserver循环错误
const resizeObserverErrorHandler = (e) => {
    if (e.message === 'ResizeObserver loop completed with undelivered notifications.') {
        // 完全忽略这个错误，不输出到控制台
        return;
    }
    console.error(e);
};

// 处理未捕获的Promise拒绝
const unhandledRejectionHandler = (e) => {
    if (e.reason && e.reason.message === 'ResizeObserver loop completed with undelivered notifications.') {
        return;
    }
    console.error('Unhandled promise rejection:', e);
};

// 添加多个事件监听器来捕获不同类型的ResizeObserver错误
window.addEventListener('error', resizeObserverErrorHandler);
window.addEventListener('unhandledrejection', unhandledRejectionHandler);

// 也可以通过重写console.error来过滤特定错误
const originalConsoleError = console.error;
console.error = (...args) => {
    const message = args[0];
    if (typeof message === 'string' && message.includes('ResizeObserver loop completed with undelivered notifications')) {
        return;
    }
    originalConsoleError.apply(console, args);
};

// axios请求拦截器
axios.interceptors.request.use(
  config => {
    // 从SessionManager获取当前token
    const token = SessionManager.getCurrentToken();
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// axios响应拦截器：自动解包后端 Result<T> 格式 → 兼容旧前端 success/data 模式
axios.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        const unwrapped = { success: true, message: body.message }
        if (body.data != null) {
          if (typeof body.data === 'object' && !Array.isArray(body.data)) {
            unwrapped.data = body.data
            Object.assign(unwrapped, body.data)
          } else {
            unwrapped.data = body.data
          }
        }
        response.data = unwrapped
      } else {
        response.data = { success: false, message: body.message || '请求失败' }
      }
    }
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      SessionManager.logout()
      router.push({ name: 'UserLogin' })
    }
    return Promise.reject(error)
  }
)

app.config.globalProperties.$http = axios;

// 3. 注册插件
app.use(ElementPlus); // 注册 Element Plus
app.use(router);      // 注册路由

// 4. 挂载应用
app.mount('#app');
