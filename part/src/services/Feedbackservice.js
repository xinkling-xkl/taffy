import request from './request.js'
import SessionManager from '../utils/SessionManager.js'

export const submitFeedback = async (feedback) => {
  return await request('/api/feedback/submit', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` },
    body: JSON.stringify(feedback)
  })
}

export const withdrawFeedback = async (id, userId) => {
  return await request(`/api/feedback/withdraw/${id}?userId=${userId}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` }
  })
}

export const deleteFeedback = async (id, userId, isAdmin) => {
  return await request(`/api/feedback/delete/${id}?userId=${userId}&isAdmin=${isAdmin}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` }
  })
}

export const resolveFeedback = async (id, reply) => {
  return await request(`/api/feedback/resolve/${id}?reply=${encodeURIComponent(reply)}`, {
    method: 'PUT',
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` }
  })
}

export const getUserFeedback = async (userId) => {
  return await request(`/api/feedback/user/${userId}`, {
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` }
  })
}

export const getAllFeedback = async () => {
  return await request('/api/feedback/all', {
    headers: { 'Authorization': `Bearer ${SessionManager.getCurrentToken()}` }
  })
}
