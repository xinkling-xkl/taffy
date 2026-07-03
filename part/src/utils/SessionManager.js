// 会话管理类，用于管理标签页级别的独立会话
class SessionManager {
  constructor() {
    this.USER_KEY = 'current_user';
    this.TOKEN_KEY = 'current_token';
  }

  // 存储用户会话（标签页级别）
  saveSession(user, token) {
    const sessionData = {
      user,
      token,
      loginTime: new Date().toISOString(),
      lastActivity: new Date().toISOString()
    };
    
    // 使用sessionStorage存储，实现标签页级别独立
    sessionStorage.setItem(this.USER_KEY, JSON.stringify(sessionData));
    sessionStorage.setItem(this.TOKEN_KEY, token);
    
    return user.id.toString();
  }

  // 获取当前用户
  getCurrentUser() {
    const sessionData = sessionStorage.getItem(this.USER_KEY);
    return sessionData ? JSON.parse(sessionData) : null;
  }

  // 注销用户
  logout() {
    sessionStorage.removeItem(this.USER_KEY);
    sessionStorage.removeItem(this.TOKEN_KEY);
    return true;
  }

  // 清除所有会话
  clearAllSessions() {
    sessionStorage.removeItem(this.USER_KEY);
    sessionStorage.removeItem(this.TOKEN_KEY);
  }

  // 获取当前token
  getCurrentToken() {
    return sessionStorage.getItem(this.TOKEN_KEY);
  }

  // 获取当前用户信息
  getCurrentUserInfo() {
    const sessionData = this.getCurrentUser();
    return sessionData ? sessionData.user : null;
  }

  // 更新当前用户信息
  updateUser(user) {
    const sessionData = this.getCurrentUser();
    if (sessionData) {
      sessionData.user = { ...sessionData.user, ...user };
      sessionStorage.setItem(this.USER_KEY, JSON.stringify(sessionData));
    }
  }

  // 检查是否已登录
  isLoggedIn() {
    return this.getCurrentUser() !== null;
  }
}

// 导出单例
export default new SessionManager();
