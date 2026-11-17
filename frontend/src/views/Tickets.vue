<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const list = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try { list.value = await http.get('/api/tickets/mine') }
  catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">我的票</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">还没有票，支付成功后电子票会出现在这里</div>
    <div v-else class="grid" style="grid-template-columns:repeat(auto-fill,minmax(300px,1fr))">
      <div class="card" v-for="t in list" :key="t.ticketNo">
        <div class="spread">
          <h3>{{ t.activityTitle }}</h3>
          <span class="tag" :class="t.status === 'VALID' ? 'ok' : t.status === 'USED' ? 'gray' : 'warn'">
            {{ t.status === 'VALID' ? '待入场' : t.status === 'USED' ? '已核销' : '已作废' }}
          </span>
        </div>
        <div class="meta">{{ t.sessionTitle }} · 🕐 {{ fmt(t.startTime) }} · 📍 {{ t.venue }}</div>
        <div class="ticket-code" style="margin-top:12px">{{ t.ticketNo }}</div>
        <div class="meta" style="margin-top:8px">入场时出示此码，工作人员扫码核销</div>
      </div>
    </div>
  </main>
</template>
