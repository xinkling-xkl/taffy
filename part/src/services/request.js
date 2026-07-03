/**
 * 统一请求工具 - 自动解包后端 Result<T> 格式
 * 
 * 后端返回: {code: 200, message: "...", data: {...}}
 * 解包后:   {success: true, message: "...", ...data属性平铺, data: 原始data}
 * 
 * error返回: {success: false, message: "..."}
 */
import SessionManager from '../utils/SessionManager.js'

export async function request(url, options = {}) {
  try {
    const token = SessionManager.getCurrentToken()
    const headers = { 'Content-Type': 'application/json', ...options.headers }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    const response = await fetch(url, {
      ...options,
      headers
    })
    const body = await response.json()

    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        const result = { success: true, message: body.message }
        if (body.data != null) {
          if (typeof body.data === 'object' && !Array.isArray(body.data)) {
            result.data = body.data
            Object.assign(result, body.data)
          } else {
            result.data = body.data
          }
        }
        return result
      } else {
        return { success: false, message: body.message || '请求失败' }
      }
    }
    return body
  } catch (err) {
    console.error('Request failed:', err)
    return { success: false, message: '网络错误: ' + err.message }
  }
}

export default request
