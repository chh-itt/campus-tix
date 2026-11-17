import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', component: () => import('../views/Home.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/activities/:id', component: () => import('../views/ActivityDetail.vue') },
  { path: '/orders', component: () => import('../views/Orders.vue'), meta: { auth: true } },
  { path: '/tickets', component: () => import('../views/Tickets.vue'), meta: { auth: true } },
  { path: '/notices', component: () => import('../views/Notices.vue'), meta: { auth: true } },
  { path: '/waitlist', component: () => import('../views/Waitlist.vue'), meta: { auth: true } },
  { path: '/organizer', component: () => import('../views/Organizer.vue'), meta: { auth: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  if (to.meta.auth && !localStorage.getItem('ct_token')) {
    return '/login'
  }
})

export default router
