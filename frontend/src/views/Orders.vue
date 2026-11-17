<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const list = ref([])
const loading = ref(true)

const STATUS_MAP = {
  CREATED: ['待支付', 'warn'],
  PAID: ['已支付', 'ok'],
  CHECKED_IN: ['已核销', 'ok'],
  CANCELLED: ['已取消', 'gray'],
  EXPIRED: ['已超时', 'gray'],
  REFUNDING: ['退款中', 'warn'],
  REFUNDED: ['已退款', 'gray'],
}

async function load() {
  loading.value = true
  try { list.value = await http.get('/api/orders', { params: { pageNo: 1, pageSize: 50 } }) }
  catch (e) { window.__toast?.(e.message, true) }
  loading.value = false
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 19) : '' }

async function pay(o) {
  try {
    const vo = await http.post(`/api/orders/${o.orderNo}/pay`)
    if (vo.payType === 'ALIPAY' && vo.payForm) {
      const w = window.open('', '_blank')
      if (!w) return window.__toast?.('浏览器拦截了新窗口，请允许弹窗', true)
      w.document.write(vo.payForm)
      w.document.close()
    } else if (vo.payUrl) {
      window.open(vo.payUrl, '_blank')
      window.__toast?.('已打开模拟收银台，支付后本页自动可刷新查看')
    }
    setTimeout(load, 3000)
  } catch (e) { window.__toast?.(e.message, true) }
}

async function cancel(o) {
  try {
    await http.post(`/api/orders/${o.orderNo}/cancel`)
    window.__toast?.('已取消，名额已释放')
    load()
  } catch (e) { window.__toast?.(e.message, true) }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-title">我的订单</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">暂无订单，去<a style="color:var(--primary)" href="/">活动广场</a>逛逛</div>
    <div v-else style="display:grid;gap:12px">
      <div class="card" v-for="o in list" :key="o.id">
        <div class="spread">
          <div>
            <h3>{{ o.activityTitle }} · {{ o.sessionTitle }}</h3>
            <div class="meta">
              订单号：{{ o.orderNo }} · {{ o.quantity }} 张 · 下单时间 {{ fmt(o.createdAt) }}
              <template v-if="o.payExpireAt && o.status === 'CREATED'">
                <br />⏰ 支付截止 {{ fmt(o.payExpireAt) }}（超时自动关单）
              </template>
              <template v-if="o.closeReason"><br />关单原因：{{ o.closeReason }}</template>
            </div>
          </div>
          <div style="text-align:right">
            <span class="tag" :class="STATUS_MAP[o.status]?.[1]">{{ STATUS_MAP[o.status]?.[0] || o.status }}</span>
            <div class="price" style="margin:6px 0 10px">¥{{ o.totalAmount }}</div>
            <div class="row" v-if="o.status === 'CREATED'" style="justify-content:flex-end">
              <button class="btn small ghost" @click="cancel(o)">取消订单</button>
              <button class="btn small primary" @click="pay(o)">去支付</button>
            </div>
            <div class="row" v-else-if="['CANCELLED','EXPIRED'].includes(o.status)" style="justify-content:flex-end">
              <span class="meta">名额已释放</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>
