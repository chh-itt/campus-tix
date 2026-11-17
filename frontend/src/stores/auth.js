import { defineStore } from 'pinia'
import http from '../api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('ct_user') || 'null'),
  }),
  getters: {
    isLoggedIn: s => !!s.user,
    isOrganizer: s => s.user && (s.user.role === 'ORGANIZER' || s.user.role === 'ADMIN'),
  },
  actions: {
    async login(username, password) {
      const vo = await http.post('/api/auth/login', { username, password })
      this.user = { id: vo.id, username: vo.username, role: vo.role }
      localStorage.setItem('ct_token', vo.token)
      localStorage.setItem('ct_user', JSON.stringify(this.user))
    },
    async register(payload) {
      const vo = await http.post('/api/auth/register', payload)
      this.user = { id: vo.id, username: vo.username, role: vo.role }
      localStorage.setItem('ct_token', vo.token)
      localStorage.setItem('ct_user', JSON.stringify(this.user))
    },
    async refreshMe() {
      if (!localStorage.getItem('ct_token')) return
      try {
        this.user = await http.get('/api/auth/me')
        localStorage.setItem('ct_user', JSON.stringify(this.user))
      } catch { /* 401 由拦截器处理 */ }
    },
    logout() {
      this.user = null
      localStorage.removeItem('ct_token')
      localStorage.removeItem('ct_user')
    },
  },
})
