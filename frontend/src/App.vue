<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const auth = useAuthStore()
const toast = ref(null)
let timer = null

function showToast(msg, isErr = false) {
  toast.value = { msg, isErr }
  clearTimeout(timer)
  timer = setTimeout(() => (toast.value = null), 2400)
}
window.__toast = showToast

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <nav class="navbar">
    <div class="navbar-inner">
      <div class="logo" @click="router.push('/')">Campus<span>Tix</span></div>
      <div class="nav-links">
        <router-link to="/">活动广场</router-link>
        <router-link v-if="auth.isLoggedIn" to="/orders">我的订单</router-link>
        <router-link v-if="auth.isLoggedIn" to="/tickets">我的票</router-link>
        <router-link v-if="auth.isLoggedIn" to="/waitlist">候补单</router-link>
        <router-link v-if="auth.isLoggedIn" to="/notices">消息</router-link>
        <router-link v-if="auth.isOrganizer" to="/organizer">主办方工作台</router-link>
      </div>
      <div class="nav-user" v-if="auth.isLoggedIn">
        <span>{{ auth.user?.nickname || auth.user?.username }}</span>
        <a class="btn small ghost" @click="logout">退出</a>
      </div>
      <div class="nav-user" v-else>
        <router-link class="btn small ghost" to="/login">登录</router-link>
        <router-link class="btn small primary" to="/register">注册</router-link>
      </div>
    </div>
  </nav>

  <router-view />

  <div v-if="toast" class="toast" :class="{ err: toast.isErr }">{{ toast.msg }}</div>
</template>
