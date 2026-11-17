<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const list = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try { list.value = await http.get('/api/notices/mine', { params: { pageNo: 1, pageSize: 50 } }) }
  catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

async function markRead(n) {
  if (n.isRead) return
  try {
    await http.post(`/api/notices/${n.id}/read`)
    n.isRead = 1
  } catch (e) { window.__toast?.(e.message, true) }
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 19) : '' }

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">站内消息</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">暂无消息</div>
    <div v-else style="display:grid;gap:10px">
      <div class="card" v-for="n in list" :key="n.id" @click="markRead(n)"
           :style="{ cursor: n.isRead ? 'default' : 'pointer', opacity: n.isRead ? .75 : 1 }">
        <div class="spread">
          <div>
            <h3 :style="{ fontSize: '15px' }">
              <span v-if="!n.isRead" style="color:var(--accent)">● </span>{{ n.title }}
            </h3>
            <div class="faq-a" style="margin-top:4px">{{ n.content }}</div>
          </div>
          <span class="meta" style="white-space:nowrap">{{ fmt(n.createdAt) }}</span>
        </div>
      </div>
    </div>
  </main>
</template>
