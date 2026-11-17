<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value || !password.value) return window.__toast?.('请输入账号密码', true)
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    window.__toast?.('登录成功')
    router.push(auth.isOrganizer ? '/organizer' : '/')
  } catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}
</script>

<template>
  <div class="form-narrow">
    <div class="card">
      <h3 style="margin-bottom:20px">登录 CampusTix</h3>
      <div class="field">
        <label>账号</label>
        <input class="input" v-model="username" placeholder="用户名（演示：student）" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input class="input" type="password" v-model="password" placeholder="演示密码：123456" @keyup.enter="submit" />
      </div>
      <button class="btn primary" style="width:100%" :disabled="loading" @click="submit">登 录</button>
      <p class="meta" style="margin-top:14px;text-align:center">
        没有账号？<router-link style="color:var(--primary)" to="/register">立即注册</router-link>
      </p>
      <p class="meta" style="margin-top:6px;text-align:center">
        演示账号：student / organizer / club / admin，密码均为 123456
      </p>
    </div>
  </div>
</template>
