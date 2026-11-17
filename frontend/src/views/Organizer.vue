<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const activities = ref([])
const loading = ref(true)
const showCreate = ref(false)
const checkInNo = ref('')
const checkInResult = ref(null)
const detailOf = ref(null) // 当前展开场次管理的活动

const form = ref({ title: '', category: 'MUSIC', venue: '', description: '' })
const sessionForm = ref({ title: '', startTime: '', saleEnd: '', price: 0, quota: 100 })

const STATUS_MAP = {
  DRAFT: ['草稿', 'gray'], PUBLISHED: ['已发布', 'ok'],
  OFFLINE: ['已下线', 'warn'], FINISHED: ['已结束', 'gray'],
}

async function load() {
  loading.value = true
  try { activities.value = await http.get('/api/organizer/activities') }
  catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

async function create() {
  const f = form.value
  if (!f.title.trim()) return window.__toast?.('请输入活动标题', true)
  try {
    const id = await http.post('/api/organizer/activities', f)
    window.__toast?.('活动已创建（草稿），请添加场次后发布')
    showCreate.value = false
    form.value = { title: '', category: 'MUSIC', venue: '', description: '' }
    await load()
    openSessions({ id, status: 'DRAFT' })
  } catch (e) { window.__toast?.(e.message, true) }
}

async function openSessions(a) {
  try {
    a.sessions = await http.get(`/api/organizer/activities/${a.id}/sessions`)
    detailOf.value = a
  } catch (e) { window.__toast?.(e.message, true) }
}

async function addSession(a) {
  const s = sessionForm.value
  if (!s.title || !s.startTime || !s.saleEnd) return window.__toast?.('请完整填写场次信息', true)
  try {
    await http.post(`/api/organizer/activities/${a.id}/sessions`, {
      title: s.title,
      startTime: s.startTime.replace(' ', 'T') + ':00',
      saleEnd: s.saleEnd.replace(' ', 'T') + ':00',
      price: Number(s.price),
      quota: Number(s.quota),
    })
    window.__toast?.('场次已添加')
    s.title = ''; s.startTime = ''; s.saleEnd = ''
    openSessions(a)
  } catch (e) { window.__toast?.(e.message, true) }
}

async function publish(a) {
  try {
    await http.post(`/api/organizer/activities/${a.id}/publish`)
    window.__toast?.('已发布，库存已写入 Redis')
    load()
    if (detailOf.value?.id === a.id) detailOf.value = null
  } catch (e) { window.__toast?.(e.message, true) }
}

async function offline(a) {
  try {
    await http.post(`/api/organizer/activities/${a.id}/offline`)
    window.__toast?.('已下线')
    load()
  } catch (e) { window.__toast?.(e.message, true) }
}

async function doCheckIn() {
  if (!checkInNo.value.trim()) return
  try {
    checkInResult.value = { ok: true, vo: await http.post('/api/organizer/check-in', { ticketNo: checkInNo.value.trim() }) }
    window.__toast?.('核销成功')
  } catch (e) {
    checkInResult.value = { ok: false, message: e.message }
  }
  checkInNo.value = ''
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">主办方工作台</div>

    <div class="card" style="margin-bottom:20px">
      <h3>🎫 扫码核销</h3>
      <div class="row" style="margin-top:12px">
        <input class="input" style="flex:1;max-width:360px" v-model="checkInNo"
               placeholder="输入票号，如 CT1923…" @keyup.enter="doCheckIn" />
        <button class="btn primary" @click="doCheckIn">核销</button>
      </div>
      <div v-if="checkInResult" style="margin-top:12px">
        <div v-if="checkInResult.ok" class="tag ok">
          核销成功：{{ checkInResult.vo.activityTitle }} · {{ checkInResult.vo.sessionTitle }}
        </div>
        <div v-else class="tag warn">核销失败：{{ checkInResult.message }}</div>
      </div>
    </div>

    <div class="spread" style="margin-bottom:14px">
      <h3>我的活动（{{ activities.length }}）</h3>
      <button class="btn primary" @click="showCreate = true">+ 新建活动</button>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!activities.length" class="empty">还没有活动，点击右上角新建</div>
    <div v-else style="display:grid;gap:12px">
      <div class="card" v-for="a in activities" :key="a.id">
        <div class="spread">
          <div>
            <h3>{{ a.title }} <span class="tag" :class="STATUS_MAP[a.status]?.[1]">{{ STATUS_MAP[a.status]?.[0] }}</span></h3>
            <div class="meta">{{ a.category }} · 📍 {{ a.venue || '待定' }}</div>
          </div>
          <div class="row">
            <button class="btn small ghost" @click="openSessions(a)">场次管理</button>
            <button v-if="['DRAFT','OFFLINE'].includes(a.status)" class="btn small primary" @click="publish(a)">发布</button>
            <button v-if="a.status === 'PUBLISHED'" class="btn small danger" @click="offline(a)">下线</button>
          </div>
        </div>

        <template v-if="detailOf && detailOf.id === a.id">
          <div style="margin-top:16px;border-top:1px solid var(--border);padding-top:14px">
            <h4 style="margin-bottom:10px">场次列表</h4>
            <div class="meta" v-if="!a.sessions.length">暂无场次，先添加一个</div>
            <div v-for="s in a.sessions" :key="s.id" class="spread"
                 style="padding:8px 0;border-bottom:1px dashed var(--border)">
              <span>{{ s.title }} · {{ fmt(s.startTime) }} · ¥{{ s.price }} · 剩余 {{ s.remaining }}/{{ s.quota }}</span>
              <span class="tag" :class="s.status === 'ON_SALE' ? 'ok' : 'gray'">{{ s.status }}</span>
            </div>

            <h4 style="margin:16px 0 10px">添加场次</h4>
            <div class="row" style="flex-wrap:wrap;gap:8px">
              <input class="input" style="width:140px" v-model="sessionForm.title" placeholder="场次名" />
              <input class="input" style="width:190px" v-model="sessionForm.startTime" placeholder="开场 2026-01-01 19:00" />
              <input class="input" style="width:190px" v-model="sessionForm.saleEnd" placeholder="售票截止 2026-01-01 18:00" />
              <input class="input" style="width:100px" v-model="sessionForm.price" placeholder="票价 ¥" />
              <input class="input" style="width:100px" v-model="sessionForm.quota" placeholder="名额" />
              <button class="btn small primary" @click="addSession(a)">添加</button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="modal-mask" v-if="showCreate" @click.self="showCreate = false">
      <div class="modal">
        <h3>新建活动</h3>
        <div class="field"><label>活动标题</label>
          <input class="input" v-model="form.title" /></div>
        <div class="field"><label>分类</label>
          <select v-model="form.category">
            <option v-for="c in ['MUSIC','SHOW','TECH','SPORT','OTHER']" :key="c" :value="c">{{ c }}</option>
          </select></div>
        <div class="field"><label>场地</label>
          <input class="input" v-model="form.venue" /></div>
        <div class="field"><label>活动介绍</label>
          <textarea rows="4" v-model="form.description"></textarea></div>
        <div class="row" style="justify-content:flex-end">
          <button class="btn ghost" @click="showCreate = false">取消</button>
          <button class="btn primary" @click="create">创建</button>
        </div>
      </div>
    </div>
  </main>
</template>
