<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const list = ref([])
const loading = ref(true)
const keyword = ref('')
const category = ref('')
const categories = ['MUSIC', 'SHOW', 'TECH', 'SPORT', 'OTHER']

async function load() {
  loading.value = true
  try {
    list.value = await http.get('/api/activities', {
      params: { pageNo: 1, pageSize: 12, category: category.value || undefined, keyword: keyword.value || undefined }
    })
  } catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '待定' }

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">校园活动广场</div>
    <div class="filters">
      <input class="input" style="flex:1;max-width:280px" v-model="keyword"
             placeholder="搜索活动名称…" @keyup.enter="load" />
      <select v-model="category" @change="load">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
      </select>
      <button class="btn primary" @click="load">搜索</button>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">暂无上架活动</div>
    <div v-else class="grid">
      <router-link v-for="a in list" :key="a.id" class="card clickable"
                   :to="'/activities/' + a.id">
        <span class="tag">{{ a.category }}</span>
        <h3>{{ a.title }}</h3>
        <div class="meta">
          📍 {{ a.venue || '待定' }}<br />
          🕐 最早场次：{{ fmt(a.earliestStart) }}<br />
          🎫 {{ a.sessionCount }} 个场次可选
        </div>
        <div class="spread" style="margin-top:10px">
          <span class="price" :class="{ free: a.minPrice === 0 }">
            {{ a.minPrice === 0 ? '免费' : '¥' + a.minPrice }} 起
          </span>
          <span class="meta">查看详情 →</span>
        </div>
      </router-link>
    </div>
  </main>
</template>
