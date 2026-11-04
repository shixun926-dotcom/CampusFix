<template>
  <div class="app">
    <aside class="side">
      <div class="brand">
        <span class="mark">CF</span>
        <span>CampusFix</span>
        <span class="ver">v1.0</span>
      </div>

      <div class="nav-label">{{ isClient ? '我的服务' : '概览' }}</div>
      <a :class="{ on: route.path === '/dashboard' }" @click="go('/dashboard')">
        <Icon name="grid" :size="17" />
        <span>{{ dashboardNav }}</span>
      </a>

      <div v-if="!isClient" class="nav-label">业务管理</div>
      <a :class="{ on: route.path === '/orders' }" @click="go('/orders')">
        <Icon name="receipt" :size="17" />
        <span>{{ ordersNav }}</span>
      </a>
      <a
        v-if="isRepairer"
        :class="{ on: route.path === '/profile' }"
        @click="go('/profile')"
      >
        <Icon name="user" :size="17" />
        <span>个人中心</span>
      </a>
      <a
        v-if="auth.has('complaint:list')"
        :class="{ on: route.path === '/complaints' }"
        @click="go('/complaints')"
      >
        <Icon name="alert" :size="17" />
        <span>投诉管理</span>
      </a>
      <a
        v-if="auth.has('profile:change:review')"
        :class="{ on: route.path === '/profile-changes' }"
        @click="go('/profile-changes')"
      >
        <Icon name="shield" :size="17" />
        <span>信息更改审核</span>
      </a>
      <a v-if="auth.has('user:list')" :class="{ on: route.path === '/users' }" @click="go('/users')">
        <Icon name="users" :size="17" />
        <span>用户管理</span>
      </a>
      <a
        v-if="auth.has('log:list')"
        :class="{ on: route.path === '/oper-logs' }"
        @click="go('/oper-logs')"
      >
        <Icon name="file" :size="17" />
        <span>操作日志</span>
      </a>

      <div v-if="!isClient" class="nav-label">洞察</div>
      <a
        v-if="canViewStats"
        :class="{ on: route.path === '/stats' }"
        @click="go('/stats')"
      >
        <Icon name="chart" :size="17" />
        <span>数据统计</span>
      </a>
      <a :class="{ on: route.path === '/notifications' }" @click="go('/notifications')">
        <Icon name="bell" :size="17" />
        <span>消息中心</span>
        <span v-if="unread > 0" class="side-badge hot">{{ unread > 99 ? '99+' : unread }}</span>
      </a>

      <div class="side-foot">
        <div class="side-user">
          <span class="avatar">{{ initial }}</span>
          <div class="su-main">
            <div class="su-name">{{ auth.displayName }}</div>
            <div class="su-role">{{ roleText }}</div>
          </div>
          <button class="su-out" type="button" title="退出登录" @click="onLogout">
            <Icon name="logout" :size="16" />
          </button>
        </div>
        <div class="side-note">CampusFix · 智慧后勤</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="tb-title">
          <span class="page-title">{{ pageTitle }}</span>
          <span v-if="!isClient" class="crumb"><b>{{ pageGroup }}</b> / {{ pageTitle }}</span>
        </div>
        <span class="spacer" />

        <label class="top-search">
          <Icon name="search" :size="16" />
          <input
            v-model="keyword"
            type="text"
            :placeholder="searchPlaceholder"
            @keyup.enter="onSearch"
          />
        </label>

        <div ref="notiWrap" class="noti-wrap">
          <button class="icon-btn" type="button" title="通知" @click="toggleNoti">
            <Icon name="bell" :size="18" />
            <span v-if="unread > 0" class="badge-num">{{ unread > 99 ? '99+' : unread }}</span>
          </button>

          <transition name="pop">
            <div v-if="notiOpen" class="noti-panel">
              <div class="noti-hd">
                <b>通知</b>
                <span v-if="unread" class="noti-unread">未读 {{ unread }}</span>
                <span class="spacer" />
                <button
                  v-if="items.length"
                  class="noti-op"
                  type="button"
                  :disabled="unread === 0"
                  @click="onReadAll"
                >
                  全部已读
                </button>
              </div>
              <div class="noti-list">
                <div v-if="!items.length" class="noti-empty">暂无通知</div>
                <button
                  v-for="n in items"
                  :key="n.id"
                  type="button"
                  class="noti-item"
                  :class="{ unread: n.readFlag === 0 }"
                  @click="onNotiClick(n)"
                >
                  <span class="n-ic" :class="typeClass(n.type)">
                    <Icon :name="typeIcon(n.type)" :size="15" />
                  </span>
                  <span class="n-main">
                    <span class="n-title">{{ n.title }}</span>
                    <span class="n-content">{{ n.content }}</span>
                    <span class="n-time">{{ shortTime(n.createdAt) }}</span>
                  </span>
                  <span v-if="n.readFlag === 0" class="n-dot" />
                </button>
              </div>
              <button class="noti-all" type="button" @click="goAllNotifications">
                查看全部消息<Icon name="arrowRight" :size="13" />
              </button>
            </div>
          </transition>
        </div>

        <div ref="userWrap" class="user-wrap">
          <div class="user-chip" @click="menuOpen = !menuOpen">
            <span class="avatar">{{ initial }}</span>
            <div class="uc-main">
              <span class="uc-name">{{ auth.displayName }}</span>
              <span class="uc-role">{{ roleText }}</span>
            </div>
            <Icon name="chevronDown" :size="14" class="uc-arrow" :class="{ open: menuOpen }" />
          </div>

          <transition name="pop">
            <div v-if="menuOpen" class="user-menu">
              <div class="um-head">
                <span class="avatar lg">{{ initial }}</span>
                <div>
                  <div class="um-name">{{ auth.displayName }}</div>
                  <div class="um-sub">{{ auth.user?.username }} · {{ roleText }}</div>
                </div>
              </div>
              <div class="um-roles">
                <span v-for="r in auth.roles" :key="r" class="tag tag-brand">{{ roleLabel(r) }}</span>
              </div>
              <button v-if="!isClient" class="um-item" @click="openSwagger">
                <Icon name="file" :size="16" />接口文档
              </button>
              <button class="um-item danger" @click="onLogout">
                <Icon name="logout" :size="16" />退出登录
              </button>
            </div>
          </transition>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../ui/Icon.vue'
import { confirmDialog } from '../ui/dialog'
import { toast, toastOk } from '../ui/toast'
import { swaggerUrl } from '../config/env'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import {
  markAllNotificationsRead,
  markNotificationRead,
  onPush,
  pageNotifications,
  type NotificationVO
} from '../api/message'
const auth = useAuthStore()
const notiStore = useNotificationStore()
const route = useRoute()
const router = useRouter()

const menuOpen = ref(false)
const keyword = ref('')
const userWrap = ref<HTMLElement | null>(null)

const ROLE_LABELS: Record<string, string> = {
  STUDENT: '学生',
  REPAIRER: '维修人员',
  MANAGER: '后勤管理员',
  ADMIN: '系统管理员'
}

const roleLabel = (code: string) => ROLE_LABELS[code] || code
const roleText = computed(() => auth.roles.map(roleLabel).join('、') || '—')
const initial = computed(() => auth.displayName.charAt(0) || 'U')

/** 学生 / 维修人员走简化导航，管理员与后勤保留完整管理菜单 */
const isStaff = computed(() => auth.hasRole('MANAGER') || auth.hasRole('ADMIN'))
const isRepairer = computed(() => !isStaff.value && auth.hasRole('REPAIRER'))
const isStudent = computed(
  () => !isStaff.value && !isRepairer.value && auth.hasRole('STUDENT')
)
const isClient = computed(() => isStudent.value || isRepairer.value)
const dashboardNav = computed(() => (isClient.value ? '首页' : '工作台'))
const ordersNav = computed(() =>
  isStudent.value ? '我的报修' : isRepairer.value ? '我的任务' : '工单管理'
)
const searchPlaceholder = computed(() =>
  isStudent.value
    ? '搜索我的报修：标题 / 单号 / 位置'
    : isRepairer.value
      ? '搜索我的任务：标题 / 单号 / 位置'
      : '搜索工单：标题 / 单号 / 位置'
)

const pageTitle = computed(() => {
  if (route.path === '/dashboard') return dashboardNav.value
  if (route.path === '/orders') return ordersNav.value
  return (route.meta.title as string) || '工作台'
})
const pageGroup = computed(() => {
  if (isClient.value) return '我的服务'
  if (route.path === '/orders' || route.path === '/users' || route.path === '/oper-logs') return '业务管理'
  if (route.path === '/stats' || route.path === '/notifications') return '洞察'
  return '概览'
})
/** 数据统计页入口：管理端（后勤 / 管理员）可见，与后端 /stats/insight 权限一致 */
const canViewStats = computed(() => auth.has('order:review') || auth.has('order:assign'))

function go(path: string) {
  if (route.path !== path) router.push(path)
}

function onSearch() {
  const q = keyword.value.trim()
  router.push({ path: '/orders', query: q ? { keyword: q } : {} })
}

/* ---------------- 通知中心 ---------------- */

const notiOpen = ref(false)
const notiWrap = ref<HTMLElement | null>(null)
const unread = computed(() => notiStore.unreadCount)
const items = ref<NotificationVO[]>([])

async function loadList() {
  try {
    const page = await pageNotifications({ page: 1, size: 20 })
    items.value = page?.list || []
    notiStore.setUnread(page?.unreadCount ?? notiStore.unreadCount)
  } catch {
    /* 静默 */
  }
}

function toggleNoti() {
  notiOpen.value = !notiOpen.value
  if (notiOpen.value && items.value.length === 0) {
    loadList()
  }
}

async function onNotiClick(n: NotificationVO) {
  if (n.readFlag === 0) {
    try {
      await markNotificationRead(n.id)
    } catch {
      /* 已读失败不阻断跳转 */
    }
    n.readFlag = 1
    notiStore.decrement()
  }
  notiOpen.value = false
  if (n.bizId) {
    // 工单列表支持 ?id= 直达详情抽屉
    router.push({ path: '/orders', query: { id: String(n.bizId) } })
  }
}

async function onReadAll() {
  const count = await markAllNotificationsRead().catch(() => 0)
  if (count > 0) toastOk(`已标记 ${count} 条为已读`)
  await loadList()
}

const TYPE_ICONS: Record<string, string> = {
  ORDER_STATUS: 'receipt',
  CHAT: 'chat',
  COMPLAINT: 'alert',
  SYSTEM: 'shield'
}
const typeIcon = (t: string) => TYPE_ICONS[t] || 'bell'
const typeClass = (t: string) => `n-ic-${(t || 'system').toLowerCase()}`

const shortTime = (t?: string) => (t ? t.slice(5, 16) : '')

/* 实时推送：NOTIFY 角标 +1（聊天消息本体另发 CHAT 帧，此处不重复计数）。
 * 必须在组件卸载时取消订阅——否则重新登录后旧实例的监听仍挂着，
 * 同一条推送会被多个旧实例重复累加，角标越走越大 */
let unsubscribePush: (() => void) | null = null
unsubscribePush = onPush((frame) => {
  if (frame.type === 'NOTIFY') {
    notiStore.increment()
    const p = frame.payload
    if (p?.id) {
      items.value = [{ ...(p as NotificationVO) }, ...items.value].slice(0, 20)
    } else if (notiOpen.value) {
      loadList()
    }
  }
})

function goAllNotifications() {
  notiOpen.value = false
  router.push('/notifications')
}

function openSwagger() {
  menuOpen.value = false
  window.open(swaggerUrl(), '_blank')
}

async function onLogout() {
  menuOpen.value = false
  const ok = await confirmDialog({
    title: '退出登录',
    message: '退出后需要重新输入账号密码，确定继续？',
    okText: '退出'
  })
  if (!ok) return
  await auth.logout()
  notiStore.reset()
  toastOk('已退出登录')
  router.push('/login')
}

function onDocClick(e: MouseEvent) {
  if (!userWrap.value?.contains(e.target as Node)) menuOpen.value = false
  if (!notiWrap.value?.contains(e.target as Node)) notiOpen.value = false
}

onMounted(() => {
  // 首次进入拉取一次未读；之后依赖 store + 推送实时维护
  if (!notiStore.initialized) notiStore.fetchUnread()
  document.addEventListener('mousedown', onDocClick)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick)
  if (unsubscribePush) unsubscribePush()
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
.pop-enter-active,
.pop-leave-active {
  transition: opacity 0.14s, transform 0.14s;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.98);
}
</style>
