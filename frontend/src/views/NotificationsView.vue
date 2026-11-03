<template>
  <div>
    <div class="page-hd">
      <div>
        <h1>消息中心</h1>
        <p class="sub">工单流转、聊天与系统通知全量记录，点击可跳转对应工单</p>
      </div>
      <button class="btn btn-sm" :disabled="unread === 0" @click="onReadAll">
        <Icon name="read" :size="15" />全部已读
      </button>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <h3>通知列表</h3>
        <span class="cnt">共 {{ total }} 条 · 未读 {{ unread }}</span>
        <div class="toolbar" style="margin-left: auto">
          <select v-model="query.type" class="field" style="width: 132px" @change="onSearch">
            <option value="">全部类型</option>
            <option value="ORDER_STATUS">工单通知</option>
            <option value="CHAT">聊天</option>
            <option value="COMPLAINT">投诉</option>
            <option value="SYSTEM">系统</option>
          </select>
          <select v-model="query.unread" class="field" style="width: 112px" @change="onSearch">
            <option value="">全部状态</option>
            <option value="true">只看未读</option>
            <option value="false">已读</option>
          </select>
          <button class="btn btn-sm" @click="onReset">重置</button>
        </div>
      </div>

      <div class="nlist">
        <div v-if="loading" class="empty" style="padding: 40px 0">
          <span class="spin" />
          <span class="d">加载中…</span>
        </div>

        <div v-else-if="rows.length === 0" class="empty" style="padding: 40px 0">
          <span class="ico">◍</span>
          <span class="t">没有匹配的通知</span>
          <span class="d">换个筛选条件试试</span>
        </div>

        <button
          v-for="n in rows"
          :key="n.id"
          type="button"
          class="nrow"
          :class="{ unread: n.readFlag === 0 }"
          @click="onItemClick(n)"
        >
          <span class="n-ic" :class="typeClass(n.type)">
            <Icon :name="typeIcon(n.type)" :size="16" />
          </span>
          <span class="n-main">
            <span class="n-title">{{ n.title }}</span>
            <span class="n-content">{{ n.content }}</span>
          </span>
          <span class="n-side">
            <span class="n-time">{{ n.createdAt }}</span>
            <span v-if="n.readFlag === 0" class="n-dot">未读</span>
          </span>
        </button>
      </div>

      <div class="pager">
        <span class="info">第 {{ query.page }} / {{ pages }} 页 · 每页 {{ query.size }} 条</span>
        <button class="pg" :disabled="query.page <= 1" @click="go(query.page - 1)">上一页</button>
        <button
          v-for="p in pageList"
          :key="p"
          class="pg"
          :class="{ on: p === query.page }"
          @click="go(p)"
        >
          {{ p }}
        </button>
        <button class="pg" :disabled="query.page >= pages" @click="go(query.page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../ui/Icon.vue'
import { toastOk } from '../ui/toast'
import { useNotificationStore } from '../stores/notification'
import {
  pageNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationVO
} from '../api/message'

const router = useRouter()
const notiStore = useNotificationStore()
const rows = ref<NotificationVO[]>([])
const total = ref(0)
const loading = ref(true)

const unread = computed(() => notiStore.unreadCount)

const query = ref({ page: 1, size: 15, type: '', unread: '' as string | boolean })

const pages = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)))
const pageList = computed(() => {
  const list: number[] = []
  const from = Math.max(1, query.value.page - 2)
  const to = Math.min(pages.value, from + 4)
  for (let p = from; p <= to; p++) list.push(p)
  return list
})

const TYPE_ICONS: Record<string, string> = {
  ORDER_STATUS: 'receipt',
  CHAT: 'chat',
  COMPLAINT: 'alert',
  SYSTEM: 'shield'
}
const typeIcon = (t: string) => TYPE_ICONS[t] || 'bell'
const typeClass = (t: string) => `n-ic-${(t || 'system').toLowerCase()}`

async function load() {
  loading.value = true
  try {
    const page = await pageNotifications({
      page: query.value.page,
      size: query.value.size,
      type: query.value.type || undefined,
      unread: query.value.unread === '' ? undefined : query.value.unread === 'true'
    })
    rows.value = page?.list || []
    total.value = Number(page?.total) || 0
    notiStore.setUnread(Number(page?.unreadCount) || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.value.page = 1
  load()
}

function onReset() {
  query.value = { page: 1, size: 15, type: '', unread: '' }
  load()
}

function go(p: number) {
  query.value.page = p
  load()
}

async function onItemClick(n: NotificationVO) {
  if (n.readFlag === 0) {
    try {
      await markNotificationRead(n.id)
    } catch {
      /* 忽略 */
    }
    n.readFlag = 1
    notiStore.decrement()
  }
  if (n.bizId) {
    router.push({ path: '/orders', query: { id: String(n.bizId) } })
  }
}

async function onReadAll() {
  const count = await markAllNotificationsRead().catch(() => 0)
  if (count > 0) toastOk(`已标记 ${count} 条为已读`)
  load()
}

onMounted(load)
</script>

<style scoped>
.nlist {
  display: flex;
  flex-direction: column;
  min-height: 120px;
}
.nrow {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 13px 16px;
  border: 0;
  border-bottom: 1px solid var(--line-2);
  background: none;
  cursor: pointer;
  transition: background 0.14s;
}
.nrow:last-child { border-bottom: 0; }
.nrow:hover { background: #fafbfd; }
.nrow.unread { background: #fff7f8; }
.nrow.unread:hover { background: #fdf0f1; }
.n-ic {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  margin-top: 1px;
}
.n-ic-order_status { background: var(--brand-l); color: var(--brand); }
.n-ic-chat { background: #e8f4ee; color: #12805c; }
.n-ic-complaint { background: #fdf1e4; color: #c2660a; }
.n-ic-system { background: #eceffc; color: #4f6ef7; }
.n-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.n-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
}
.n-content {
  font-size: 12.5px;
  color: var(--ink-2);
  line-height: 1.55;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.n-side {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
}
.n-time {
  font-size: 11.5px;
  color: var(--ink-3);
}
.n-dot {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--brand);
  background: var(--brand-l);
  border-radius: 6px;
  padding: 2px 7px;
  /* 关键：未读徽标是纯文字，必须禁止换行并阻止被窄容器压缩，
     否则「未读」二字会竖向堆叠（未在上、读在下） */
  white-space: nowrap;
  flex-shrink: 0;
  line-height: 1;
  display: inline-flex;
  align-items: center;
}
</style>
