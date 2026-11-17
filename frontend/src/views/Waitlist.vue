<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const list = ref([])
const loading = ref(true)

const STATUS_MAP = {
  WAITING: ['排队中', 'warn'],
  NOTIFIED: ['已转正待支付', 'ok'],
  FULFILLED: ['已完成', 'ok'],
  EXPIRED: ['已过期', 'gray'],
  CANCELLED: ['已取消', 'gray'],
}

async function load() {
  loading.value = true
  try { list.value = await http.get('/api/waitlist/mine') }
  catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

async function cancel(w) {
  try {
    await http.post(`/api/waitlist/${w.sessionId}/cancel`)
    window.__toast?.('已退出候补队列')
    load()
  } catch (e) { window.__toast?.(e.message, true) }
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 19) : '' }

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">候补队列</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">暂无候补记录，在活动详情页遇到售罄场次可加入候补</div>
    <div v-else style="display:grid;gap:12px">
      <div class="card" v-for="w in list" :key="w.id">
        <div class="spread">
          <div>
            <h3>场次 #{{ w.sessionId }}</h3>
            <div class="meta">
              状态：<span class="tag" :class="STATUS_MAP[w.status]?.[1]">{{ STATUS_MAP[w.status]?.[0] || w.status }}</span>
              <template v-if="w.notifiedOrderNo"><br />转正订单：{{ w.notifiedOrderNo }}（请到【我的订单】限时支付）</template>
              <br />加入时间：{{ fmt(w.createdAt) }}
            </div>
          </div>
          <button v-if="w.status === 'WAITING'" class="btn small ghost" @click="cancel(w)">退出排队</button>
        </div>
      </div>
    </div>
  </main>
</template>
