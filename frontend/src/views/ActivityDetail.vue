<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const detail = ref(null)
const loadErr = ref('')
const faqs = ref([])
const openFaq = ref(-1)
const aiQuestion = ref('')
const aiAnswer = ref(null)
const aiLoading = ref(false)
const buying = ref(0)

async function load() {
  loadErr.value = ''
  try {
    detail.value = await http.get('/api/activities/' + route.params.id)
  } catch (e) {
    loadErr.value = e.message || '加载失败'
    return
  }
  try {
    faqs.value = (await http.get('/api/ai/faq/list', { params: { activityId: route.params.id } })) || []
  } catch { /* FAQ 可为空 */ }
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
function saleable(s) {
  const now = new Date()
  return s.status === 'ON_SALE' && new Date(s.saleStart) <= now && now <= new Date(s.saleEnd)
}
function isFree(s) { return Number(s.price) === 0 }

async function buy(s) {
  if (!auth.isLoggedIn) return router.push('/login')
  buying.value = s.id
  try {
    // 幂等 token：先领号再下单
    const token = await http.get('/api/orders/token')
    await http.post('/api/orders', { sessionId: s.id, quantity: 1, idempotencyToken: token })
    window.__toast?.('下单成功，请在 2 分钟内到【我的订单】完成支付')
    router.push('/orders')
  } catch (e) {
    window.__toast?.(e.message, true)
    if (e.code === 410) load() // 售罄刷新剩余量
  }
  buying.value = 0
}

async function joinWaitlist(s) {
  if (!auth.isLoggedIn) return router.push('/login')
  try {
    await http.post('/api/waitlist/' + s.id + '/join')
    window.__toast?.('已加入候补队列，名额释放将自动为你创建限时订单')
  } catch (e) { window.__toast?.(e.message, true) }
}

async function askAi() {
  if (!aiQuestion.value.trim()) return
  aiLoading.value = true
  try {
    aiAnswer.value = await http.post('/api/ai/faq/ask', { activityId: route.params.id, question: aiQuestion.value })
  } catch (e) { window.__toast?.(e.message, true) }
  aiLoading.value = false
}

onMounted(load)
</script>

<template>
  <main class="page" v-if="detail">
    <span class="tag">{{ detail.category }}</span>
    <div class="page-title" style="margin-top:8px">{{ detail.title }}</div>
    <div class="card" style="margin-bottom:20px">
      <div class="meta" style="font-size:13px">📍 {{ detail.venue || '场地待定' }}</div>
      <p style="margin-top:12px;line-height:1.9;color:var(--text)">{{ detail.description }}</p>
    </div>

    <h3 style="margin-bottom:12px">可选场次</h3>
    <div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(300px,1fr))">
      <div class="card" v-for="s in detail.sessions" :key="s.id">
        <div class="spread">
          <h3>{{ s.title }}</h3>
          <span class="tag" :class="s.status === 'ON_SALE' ? 'ok' : 'gray'">{{ s.status === 'ON_SALE' ? '售票中' : s.status }}</span>
        </div>
        <div class="meta">
          🕐 {{ fmt(s.startTime) }} ~ {{ fmt(s.endTime) }}<br />
          🎟️ 剩余 {{ s.remaining }} / {{ s.quota }}（已售 {{ s.sold }}）
        </div>
        <div class="spread" style="margin-top:12px">
          <span class="price" :class="{ free: isFree(s) }">{{ isFree(s) ? '免费' : '¥' + s.price }}</span>
          <template v-if="saleable(s)">
            <button v-if="s.remaining > 0" class="btn primary" :disabled="buying === s.id" @click="buy(s)">立即报名</button>
            <button v-else class="btn ghost" @click="joinWaitlist(s)">已售罄 · 排队候补</button>
          </template>
          <span v-else class="meta">未开售 / 已截止</span>
        </div>
      </div>
    </div>

    <h3 style="margin:26px 0 12px">常见问题</h3>
    <div class="card">
      <div class="faq-item" v-for="(f, i) in faqs" :key="i">
        <div class="faq-q" @click="openFaq = openFaq === i ? -1 : i">{{ f }}</div>
      </div>
      <div class="empty" v-if="!faqs.length">暂无 FAQ</div>
    </div>

    <h3 style="margin:26px 0 12px">智能客服（AI 问答）</h3>
    <div class="card">
      <div class="row">
        <input class="input" style="flex:1" v-model="aiQuestion"
               placeholder="例如：售罄了还能买到票吗？/ 怎么退票？" @keyup.enter="askAi" />
        <button class="btn primary" :disabled="aiLoading" @click="askAi">提问</button>
      </div>
      <div v-if="aiAnswer" style="margin-top:14px">
        <div class="faq-a">{{ aiAnswer.answer }}</div>
        <div class="meta" style="margin-top:8px">
          依据资料：{{ aiAnswer.sources.join('、') }}
        </div>
      </div>
    </div>
  </main>
  <main class="page" v-else-if="loadErr">
    <div class="empty">
      加载失败：{{ loadErr }}<br /><br />
      <button class="btn primary" @click="load">重试</button>
    </div>
  </main>
  <main class="page" v-else><div class="empty">加载中…</div></main>
</template>
