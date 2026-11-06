<template>
  <div>
    <div class="page-hd">
      <div>
        <span class="eyebrow">{{ pageEyebrow }}</span>
        <h1>{{ pageTitle }}</h1>
        <p class="sub">{{ pageSub }}</p>
      </div>
      <div class="hd-btns">
        <button class="btn btn-sm" @click="onChanged">
          <Icon name="refresh" :size="15" />刷新
        </button>
        <button class="btn btn-sm btn-primary" @click="newOpen = true">
          <Icon name="plus" :size="15" />提交报修
        </button>
      </div>
    </div>

    <section v-if="categories.length" class="cat-panel">
      <div class="cat-hd">
        <span class="cat-hd-ic"><Icon name="filter" :size="15" /></span>
        <div class="cat-hd-txt">
          <h3>故障分类</h3>
          <p>按类别查看工单，点击卡片即可筛选</p>
        </div>
        <span class="cat-hd-spacer" />
        <template v-if="activeCategory">
          <span class="cat-selected">当前：{{ activeCategory.name }}</span>
          <button class="clear-cat" @click="selectCategory(null)">
            <Icon name="refresh" :size="13" />查看全部
          </button>
        </template>
        <span v-else-if="allCount !== null" class="cat-total">共 {{ allCount }} 张工单</span>
      </div>

      <div class="cat-grid">
        <button class="cat-tile" :class="{ on: !query.categoryId }" @click="selectCategory(null)">
          <span class="cat-ic all"><Icon name="grid" :size="19" /></span>
          <span class="cat-main">
            <span class="cat-name">全部工单</span>
            <span class="cat-meta"><b>{{ allCount ?? '—' }}</b> 张</span>
          </span>
          <span class="cat-go"><Icon name="arrowRight" :size="14" /></span>
        </button>

        <button
          v-for="c in categories"
          :key="c.id"
          class="cat-tile"
          :class="{ on: query.categoryId === c.id }"
          @click="selectCategory(c.id)"
        >
          <span
            class="cat-ic"
            :style="{ color: metaOf(c).color, background: metaOf(c).color + '18' }"
          >
            <Icon :name="metaOf(c).icon" :size="19" />
          </span>
          <span class="cat-main">
            <span class="cat-name">{{ c.name }}</span>
            <span class="cat-meta"><b>{{ categoryCounts[c.id] ?? '—' }}</b> 张</span>
          </span>
          <span class="cat-go"><Icon name="arrowRight" :size="14" /></span>
        </button>
      </div>
    </section>

    <div class="status-strip">
      <span class="strip-label"><Icon name="filter" :size="13" />本页状态</span>
      <button class="strip-chip" :class="{ on: !query.status }" @click="filterStatus('')">
        <span class="dot" style="background: #9aa3b2" />全部
        <b>{{ statusSnapshot.total || rows.length }}</b>
      </button>
      <button
        v-for="c in statusChips"
        :key="c.status"
        class="strip-chip"
        :class="{ on: query.status === c.status }"
        @click="filterStatus(c.status)"
      >
        <span class="dot" :style="{ background: c.color }" />{{ c.label }}
        <b>{{ c.count }}</b>
      </button>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <div class="tabs">
          <button class="tab" :class="{ on: tab === 'mine' }" @click="switchTab('mine')">我的工单</button>
          <button v-if="auth.has('order:grab')" class="tab" :class="{ on: tab === 'pool' }" @click="switchTab('pool')">
            抢单池
          </button>
        </div>
        <span class="cnt">共 {{ total }} 条</span>

        <div class="toolbar" style="margin-left: auto">
          <input
            v-model="query.keyword"
            class="field"
            style="width: 200px"
            placeholder="标题 / 单号 / 位置"
            @keyup.enter="onSearch"
          />
          <select v-model="query.status" class="field" style="width: 132px">
            <option value="">全部状态</option>
            <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
          <button class="btn btn-sm" @click="onReset">重置</button>
          <button class="btn btn-sm btn-primary" @click="onSearch">查询</button>
        </div>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 124px">单号</th>
            <th>标题</th>
            <th style="width: 218px">位置</th>
            <th style="width: 116px">类型</th>
            <th style="width: 100px">状态</th>
            <th style="width: 116px">维修人</th>
            <th style="width: 108px">上报时间</th>
            <th style="width: 104px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8">
              <div class="empty">
                <span class="spin" />
                <span class="d">正在加载工单…</span>
              </div>
            </td>
          </tr>

          <tr v-else-if="rows.length === 0">
            <td colspan="8">
              <div class="empty">
                <span class="ico"><Icon name="receipt" :size="24" /></span>
                <span class="t">
                  {{ tab === 'pool' ? '抢单池已清空' : activeCategory ? '该分类下暂无工单' : '没有匹配的工单' }}
                </span>
                <span class="d">
                  {{
                    tab === 'pool'
                      ? '当前所有工单都已有维修人'
                      : activeCategory
                        ? '可以切换到其他分类，或查看全部工单'
                        : '换个条件，或点右上角提交一张新报修'
                  }}
                </span>
              </div>
            </td>
          </tr>

          <tr v-for="row in viewRows" :key="row.id" class="row-click" @click="openDetail(row.id)">
            <td class="mono" style="font-size: 12.5px">{{ row.orderNo }}</td>
            <td>
              <div class="title-cell">
                <span class="t">{{ row.title }}</span>
                <span v-if="row.isWarranty" class="tag tag-ok">质保</span>
                <span v-else-if="row.reworkFlag" class="tag tag-warn">返修</span>
              </div>
            </td>
            <td>
              <span class="pin-line">
                <Icon name="pin" :size="13" />
                <span>{{ row.locationPath }}</span>
              </span>
            </td>
            <td>
              <span class="cat-cell">
                <span
                  class="cat-ic-sm"
                  :style="{ color: row.cat.color, background: row.cat.color + '16' }"
                >
                  <Icon :name="row.cat.icon" :size="13" />
                </span>
                {{ row.categoryName || '—' }}
              </span>
            </td>
            <td><OrderStatusTag :status="row.status" :label="row.statusLabel" /></td>
            <td>
              <span v-if="row.assigneeName" class="cell-flex">
                <span class="avatar-sm">{{ row.assigneeName.charAt(0) }}</span>
                <span class="assignee">{{ row.assigneeName }}</span>
              </span>
              <span v-else class="muted">—</span>
            </td>
            <td class="num">
              <div class="cell-stack">
                <span class="t" style="font-size: 12.5px">{{ (row.createdAt || '').slice(0, 10) }}</span>
                <span class="cell-sub">{{ (row.createdAt || '').slice(11, 16) }}</span>
              </div>
            </td>
            <td>
              <button class="btn btn-sm btn-ghost act-btn" @click.stop="openDetail(row.id)">
                {{ tab === 'pool' ? '抢单' : '详情' }}<Icon name="arrowRight" :size="13" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>

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

    <NewOrderModal :open="newOpen" @close="newOpen = false" @created="onCreated" />
    <OrderDetailDrawer :order-id="detailId" @close="closeDetail" @changed="onChanged" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../ui/Icon.vue'
import OrderStatusTag from '../ui/OrderStatusTag.vue'
import NewOrderModal from './NewOrderModal.vue'
import OrderDetailDrawer from './OrderDetailDrawer.vue'
import { listCategories, pageOrders, pagePool, type CategoryVO, type OrderVO } from '../api/order'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const tab = ref<'mine' | 'pool'>(
  route.query.tab === 'pool' && auth.has('order:grab') ? 'pool' : 'mine'
)

/** 学生 / 维修人员使用客户端文案，管理端保持「工单管理」 */
const isStaff = computed(() => auth.hasRole('MANAGER') || auth.hasRole('ADMIN'))
const isRepairer = computed(() => !isStaff.value && auth.hasRole('REPAIRER'))
const isStudent = computed(
  () => !isStaff.value && !isRepairer.value && auth.hasRole('STUDENT')
)
const pageTitle = computed(() =>
  isStudent.value ? '我的报修' : isRepairer.value ? '我的任务' : '工单管理'
)
const pageEyebrow = computed(() =>
  isStudent.value ? 'My Reports' : isRepairer.value ? 'My Tasks' : 'Service Desk'
)
const pageSub = computed(() => {
  if (tab.value === 'pool') return '全部待处理工单，可按自己的专长选择接单'
  if (isStudent.value) return '查看报修进度、确认完成和评价服务'
  if (isRepairer.value) return '按顺序处理你名下的工单，也可以去抢单池接新单'
  return '与你相关的工单流转记录，状态变化全程可追溯'
})
const loading = ref(false)
const rows = ref<OrderVO[]>([])
const total = ref(0)
const newOpen = ref(false)
const detailId = ref<number | null>(null)

const categories = ref<CategoryVO[]>([])
const categoryCounts = ref<Record<number, number>>({})
const allCount = ref<number | null>(null)

const query = reactive({
  page: 1,
  size: 15,
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  // 支持多状态逗号分隔（后端 parseStatuses 解析），供工作台 KPI 卡下钻
  status: typeof route.query.status === 'string' ? route.query.status : '',
  categoryId: null as number | null
})

const statusOptions = [
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'PENDING', label: '待处理' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'ACCEPTED', label: '已接单' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUSPENDED', label: '挂起' },
  { value: 'PENDING_CONFIRM', label: '待确认' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' }
]

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING_REVIEW: { label: '待审核', color: '#f59e0b' },
  PENDING: { label: '待处理', color: '#e63946' },
  ASSIGNED: { label: '已分配', color: '#8a90a0' },
  ACCEPTED: { label: '已接单', color: '#4f6ef7' },
  PROCESSING: { label: '处理中', color: '#4f6ef7' },
  SUSPENDED: { label: '挂起', color: '#dc3545' },
  PENDING_CONFIRM: { label: '待确认', color: '#f59e0b' },
  COMPLETED: { label: '已完成', color: '#12a150' },
  REJECTED: { label: '已驳回', color: '#dc3545' },
  CANCELLED: { label: '已取消', color: '#9aa3b2' }
}

/**
 * 本页状态条：计数与选项列表取自「未按状态筛选」的那次加载，
 * 避免筛选后接口只返回单一状态导致其余选项消失（无法再切换）。
 */
const statusSnapshot = ref<{ total: number; chips: { status: string; label: string; color: string; count: number }[] }>({
  total: 0,
  chips: []
})

function buildStatusChips(list: any[]) {
  const counts = new Map<string, number>()
  list.forEach((r) => counts.set(r.status, (counts.get(r.status) || 0) + 1))
  return Object.entries(STATUS_META)
    .filter(([key]) => counts.get(key))
    .map(([status, meta]) => ({ status, label: meta.label, color: meta.color, count: counts.get(status) || 0 }))
}

const statusChips = computed(() => statusSnapshot.value.chips)

/* 分类卡片：图标按故障类型映射；配色统一为石板灰色系
 * （沿用「公共设施」「其他」的色系，移除蓝/紫/青/橙等其它色相） */
type CategoryMeta = { icon: string; color: string }

const CATEGORY_ACCENT = '#64748b'

const CATEGORY_META: Record<string, CategoryMeta> = {
  PLUMBING: { icon: 'droplet', color: CATEGORY_ACCENT },
  NETWORK: { icon: 'wifi', color: CATEGORY_ACCENT },
  AIRCON: { icon: 'snowflake', color: CATEGORY_ACCENT },
  DOOR: { icon: 'door', color: CATEGORY_ACCENT },
  LIGHT: { icon: 'lightbulb', color: CATEGORY_ACCENT },
  FURNITURE: { icon: 'sofa', color: CATEGORY_ACCENT },
  PUBLIC: { icon: 'building', color: CATEGORY_ACCENT },
  SANITARY: { icon: 'sparkles', color: CATEGORY_ACCENT },
  OTHER: { icon: 'file', color: CATEGORY_ACCENT }
}

const NAME_CODE: [string, string][] = [
  ['水电', 'PLUMBING'],
  ['网络', 'NETWORK'],
  ['空调', 'AIRCON'],
  ['门窗', 'DOOR'],
  ['照明', 'LIGHT'],
  ['家具', 'FURNITURE'],
  ['公共', 'PUBLIC'],
  ['卫生', 'SANITARY'],
  ['其他', 'OTHER']
]

const FALLBACK_META: CategoryMeta = { icon: 'wrench', color: CATEGORY_ACCENT }

function metaByName(name?: string): CategoryMeta {
  if (!name) return FALLBACK_META
  const hit = NAME_CODE.find(([key]) => name.includes(key))
  return (hit && CATEGORY_META[hit[1]]) || FALLBACK_META
}

function metaOf(c: CategoryVO): CategoryMeta {
  return CATEGORY_META[c.code] || metaByName(c.name)
}

const activeCategory = computed(() => categories.value.find((c) => c.id === query.categoryId) || null)
const viewRows = computed(() => rows.value.map((row) => ({ ...row, cat: metaByName(row.categoryName) })))

const pages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const pageList = computed(() => {
  const win: number[] = []
  const start = Math.max(1, Math.min(query.page - 2, pages.value - 4))
  for (let i = start; i < start + 5 && i <= pages.value; i++) win.push(i)
  return win
})

async function load() {
  loading.value = true
  try {
    const params = {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      categoryId: query.categoryId || undefined
    }
    const data: any = tab.value === 'pool' ? await pagePool(params) : await pageOrders(params)
    rows.value = data.list || []
    total.value = data.total || 0
    // 仅在没有状态筛选时刷新快照，保证筛选期间选项列表稳定
    if (!query.status) {
      statusSnapshot.value = { total: rows.value.length, chips: buildStatusChips(rows.value) }
    } else if (!statusSnapshot.value.chips.length) {
      seedStatusSnapshot()
    }
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 带状态参数直达本页时，补一次无状态查询以恢复状态选项 */
async function seedStatusSnapshot() {
  try {
    const params = {
      page: 1,
      size: query.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined
    }
    const data: any = tab.value === 'pool' ? await pagePool(params) : await pageOrders(params)
    const list = data.list || []
    statusSnapshot.value = { total: list.length, chips: buildStatusChips(list) }
  } catch {
    // 快照失败不影响主流程
  }
}

function switchTab(t: 'mine' | 'pool') {
  if (tab.value === t) return
  tab.value = t
  query.page = 1
  query.status = ''
  // 同步到地址栏，工作台「抢单池」入口可以直达
  router.replace({ query: { ...route.query, tab: t === 'pool' ? 'pool' : undefined } })
  load()
  loadCounts()
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.status = ''
  query.categoryId = null
  query.page = 1
  load()
}

function selectCategory(id: number | null) {
  query.categoryId = query.categoryId === id ? null : id
  query.page = 1
  load()
  // 小屏上分类网格较高，选完直接带到列表，避免用户以为没反应
  if (window.innerWidth <= 720) {
    requestAnimationFrame(() => {
      document.querySelector('.tablewrap')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }
}

/** 各分类工单数：按当前页签的数据范围，每个分类取一次 total（size=1） */
let countsSeq = 0
async function loadCounts() {
  if (!categories.value.length) return
  const seq = ++countsSeq
  const fetchPage = tab.value === 'pool' ? pagePool : pageOrders
  const results = await Promise.allSettled([
    fetchPage({ page: 1, size: 1 }),
    ...categories.value.map((c) => fetchPage({ page: 1, size: 1, categoryId: c.id }))
  ])
  if (seq !== countsSeq) return
  const totalAt = (index: number) => {
    const result = results[index]
    return result.status === 'fulfilled' ? Number((result.value as any)?.total ?? 0) : 0
  }
  allCount.value = totalAt(0)
  const counts: Record<number, number> = {}
  categories.value.forEach((c, i) => (counts[c.id] = totalAt(i + 1)))
  categoryCounts.value = counts
}

async function initCategories() {
  try {
    const data: any = await listCategories()
    categories.value = data || []
  } catch {
    categories.value = []
  }
  loadCounts()
}

function onChanged() {
  load()
  loadCounts()
}

function filterStatus(status: string) {
  query.status = status
  query.page = 1
  load()
}

function go(p: number) {
  if (p < 1 || p > pages.value || p === query.page) return
  query.page = p
  load()
}

function openDetail(id: number) {
  detailId.value = id
}

function closeDetail() {
  detailId.value = null
  if (route.query.id) {
    const next = { ...route.query }
    delete next.id
    router.replace({ query: next })
  }
}

function onCreated(id: number) {
  tab.value = 'mine'
  query.page = 1
  load()
  loadCounts()
  detailId.value = id
}

watch(
  () => route.query.keyword,
  (value) => {
    const kw = typeof value === 'string' ? value : ''
    if (kw === query.keyword) return
    query.keyword = kw
    query.page = 1
    load()
  }
)

/** 工作台 KPI 卡下钻：?status=A,B,C 多状态筛选 */
watch(
  () => route.query.status,
  (value) => {
    const status = typeof value === 'string' ? value : ''
    if (status === query.status) return
    query.status = status
    query.page = 1
    load()
  }
)

watch(
  () => route.query.id,
  (value) => {
    if (typeof value === 'string' && value) detailId.value = Number(value)
  }
)

/** 工作台「抢单池」入口：?tab=pool 直达对应页签 */
watch(
  () => route.query.tab,
  (value) => {
    const next = value === 'pool' && auth.has('order:grab') ? 'pool' : 'mine'
    if (next === tab.value) return
    tab.value = next
    query.page = 1
    query.status = ''
    load()
    loadCounts()
  }
)

onMounted(() => {
  const id = route.query.id
  if (typeof id === 'string' && id) detailId.value = Number(id)
  load()
  initCategories()
})
</script>

<style scoped>
.hd-btns {
  display: flex;
  gap: 8px;
}

/* 故障分类卡片 */
.cat-panel {
  background: #fff;
  border: 1px solid rgba(230, 232, 239, 0.9);
  border-radius: var(--r);
  box-shadow: var(--sh-1);
  padding: 16px 18px 18px;
  margin-bottom: 14px;
}
.cat-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.cat-hd-ic {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: rgba(100, 116, 139, 0.1);
  color: #64748b;
  flex: none;
}
.cat-hd-txt h3 {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.1px;
  line-height: 1.4;
}
.cat-hd-txt p {
  font-size: 11.5px;
  color: var(--ink-3);
  line-height: 1.4;
}
.cat-hd-spacer {
  flex: 1;
}
.cat-total {
  font-size: 12px;
  color: var(--ink-3);
}
.cat-selected {
  font-size: 11.5px;
  font-weight: 600;
  color: #475569;
  background: rgba(100, 116, 139, 0.1);
  border-radius: 7px;
  padding: 4px 9px;
  white-space: nowrap;
}
.clear-cat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--ink-2);
  font-size: 12px;
  transition: 0.15s;
}
.clear-cat:hover {
  border-color: #94a3b8;
  color: #475569;
  background: rgba(100, 116, 139, 0.06);
}

.cat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
.cat-tile {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  text-align: left;
  transition: border-color 0.16s, background 0.16s, transform 0.14s, box-shadow 0.16s;
}
.cat-tile:hover {
  border-color: #cfd6e2;
  background: #fbfcfe;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(20, 24, 40, 0.06);
}
.cat-tile.on {
  border-color: #64748b;
  background: linear-gradient(150deg, rgba(100, 116, 139, 0.08), #fff 70%);
  box-shadow: 0 4px 14px rgba(100, 116, 139, 0.16);
}
.cat-ic {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  flex: none;
  transition: transform 0.16s;
}
.cat-tile:hover .cat-ic {
  transform: scale(1.05);
}
.cat-ic.all {
  background: rgba(100, 116, 139, 0.1);
  color: #64748b;
}
.cat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}
.cat-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.15s;
}
.cat-tile.on .cat-name {
  color: #0f172a;
  font-weight: 700;
}
.cat-meta {
  font-size: 11.5px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}
.cat-meta b {
  font-weight: 700;
  color: var(--ink-2);
}
.cat-tile.on .cat-meta b {
  color: #334155;
}
.cat-go {
  margin-left: auto;
  display: grid;
  place-items: center;
  color: #c3c8d3;
  opacity: 0;
  transform: translateX(-3px);
  transition: opacity 0.16s, transform 0.16s, color 0.16s;
  flex: none;
}
.cat-tile:hover .cat-go,
.cat-tile.on .cat-go {
  opacity: 1;
  transform: none;
  color: #64748b;
}

/* 状态概览条 */
.status-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(230, 232, 239, 0.9);
  border-radius: 13px;
  box-shadow: var(--sh-1);
  backdrop-filter: blur(8px);
}
.strip-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--ink-3);
  margin-right: 4px;
}
.strip-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: #f5f6fa;
  color: var(--ink-2);
  font-size: 12px;
  transition: 0.15s;
}
.strip-chip b {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink);
  background: #fff;
  border-radius: 5px;
  padding: 1px 5px;
  line-height: 1.4;
}
.strip-chip .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}
.strip-chip:hover {
  border-color: var(--line);
  background: #fff;
}
.strip-chip.on {
  background: #fff;
  border-color: var(--slate-500);
  color: var(--slate-700);
  box-shadow: 0 2px 8px rgba(100, 116, 139, 0.14);
}
.strip-chip.on b {
  background: var(--brand-l);
  color: var(--brand);
}

/* 表头切换 */
.tabs {
  display: flex;
  gap: 4px;
  background: var(--line-2);
  border-radius: 10px;
  padding: 3px;
}
.tab {
  height: 30px;
  padding: 0 15px;
  border-radius: 8px;
  font-size: 12.5px;
  color: var(--ink-2);
  background: transparent;
  cursor: pointer;
  transition: 0.15s;
}
.tab:hover {
  color: var(--ink);
}
.tab.on {
  background: #fff;
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(16, 18, 22, 0.1);
}
.cnt {
  margin-left: 2px;
}

/* 表格单元 */
.title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.title-cell .t {
  color: var(--ink);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-click {
  cursor: pointer;
}
.cat-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--ink-2);
  white-space: nowrap;
}
.cat-ic-sm {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  flex: none;
}
.assignee {
  font-size: 12.5px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.muted {
  color: var(--ink-3);
}
.act-btn {
  color: var(--brand);
  font-weight: 500;
  padding: 0 8px;
}
.act-btn:hover {
  background: var(--brand-l);
  color: var(--brand);
}

@media (max-width: 1100px) {
  .cat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 860px) {
  .cat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 620px) {
  .cat-panel {
    padding: 14px 14px 16px;
  }
  .cat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
  .cat-tile {
    gap: 8px;
    padding: 10px 9px;
  }
  .cat-ic {
    width: 32px;
    height: 32px;
    border-radius: 10px;
  }
  .cat-name {
    font-size: 12px;
  }
  .cat-meta {
    font-size: 11px;
  }
  .cat-go {
    display: none;
  }
  .cat-hd-spacer {
    display: none;
  }
  .cat-total {
    margin-left: auto;
  }
  .cat-selected {
    margin-left: auto;
  }
}
</style>
