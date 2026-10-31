import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../views/LayoutView.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('../views/OrderListView.vue'),
        meta: { title: '工单管理' }
      },
      {
        path: 'complaints',
        name: 'complaints',
        component: () => import('../views/ComplaintListView.vue'),
        meta: { title: '投诉管理', perm: 'complaint:list' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UserListView.vue'),
        meta: { title: '用户管理', perm: 'user:list' }
      },
      {
        path: 'oper-logs',
        name: 'oper-logs',
        component: () => import('../views/OperLogListView.vue'),
        meta: { title: '操作日志', perm: 'log:list' }
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('../views/StatsView.vue'),
        meta: { title: '数据统计' }
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('../views/NotificationsView.vue'),
        meta: { title: '消息中心' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('cf_token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
  document.title = to.meta.title ? `${to.meta.title} · CampusFix` : 'CampusFix'
  return true
})

export default router
