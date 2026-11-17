<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = ref({ username: '', password: '', nickname: '', phone: '' })
const loading = ref(false)

async function submit() {
  const f = form.value
  if (!/^[a-zA-Z0-9_]{4,32}$/.test(f.username)) return window.__toast?.('用户名需为 4-32 位字母/数字/下划线', true)
  if (f.password.length < 6) return window.__toast?.('密码至少 6 位', true)
  loading.value = true
  try {
    await auth.register(f)
    window.__toast?.('注册成功，已自动登录')
    router.push('/')
  } catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}
</script>

<template>
  <div class="form-narrow">
    <div class="card">
      <h3 style="margin-bottom:20px">注册账号</h3>
      <div class="field">
        <label>用户名（4-32 位字母/数字/下划线）</label>
        <input class="input" v-model="form.username" />
      </div>
      <div class="field">
        <label>密码（至少 6 位）</label>
        <input class="input" type="password" v-model="form.password" />
      </div>
      <div class="field">
        <label>昵称（选填）</label>
        <input class="input" v-model="form.nickname" />
      </div>
      <div class="field">
        <label>手机号（选填）</label>
        <input class="input" v-model="form.phone" />
      </div>
      <button class="btn primary" style="width:100%" :disabled="loading" @click="submit">注 册</button>
      <p class="meta" style="margin-top:14px;text-align:center">
        已有账号？<router-link style="color:var(--primary)" to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>
