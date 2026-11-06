<template>
  <div>
    <div class="page-hd">
      <div>
        <h1>投诉管理</h1>
        <p class="sub">按管辖部门受理，结论为「成立」时扣减被投诉维修人员信誉分 10 分</p>
      </div>
      <button class="btn btn-sm" @click="load">
        <Icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <!-- 概览：点击切换状态筛选 -->
    <div class="kpis" style="margin-bottom: 14px">
      <div
        v-for="k in cards"
        :key="k.status"
        class="kpi"
        :class="{ clickable: true, active: query.status === k.status }"
        @click="pickStatus(k.status)"
      >
        <div class="l">
          <Icon :name="k.icon" :size="14" />
          {{ k.label }}
        </div>
        <div class="v">{{ k.value }}</div>
        <div class="d">
          <span class="muted">{{ k.desc }}</span>
        </div>
      </div>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <h3>投诉列表</h3>
        <span class="cnt">共 {{ total }} 条 · 投诉率 {{ rate }}%</span>

        <div class="toolbar" style="margin-left: auto">
          <input
            v-model="query.keyword"
            class="field"
            style="width: 200px"
            placeholder="工单号 / 内容 / 报修人"
            @keyup.enter="onSearch"
          />
          <select v-model="query.type" class="field" style="width: 128px">
            <option value="">全部类型</option>
            <option v-for="t in types" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
          <select v-model="query.status" class="field" style="width: 118px">
            <option value="">全部状态</option>
            <option value="PENDING">待受理</option>
            <option value="PROCESSING">处理中</option>
            <option value="RESOLVED">已成立</option>
            <option value="REJECTED">不成立</option>
          </select>
          <button class="btn btn-sm" @click="onReset">重置</button>
          <button class="btn btn-sm btn-primary" @click="onSearch">查询</button>
        </div>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 132px">工单号</th>
            <th style="width: 96px">类型</th>
            <th style="width: 90px">投诉人</th>
            <th style="width: 96px">被投诉人</th>
            <th>投诉内容</th>
            <th style="width: 88px; text-align: center">状态</th>
            <th style="width: 152px">提交时间</th>
            <th style="width: 84px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8">
              <div class="empty">
                <span class="spin" />
                <span class="d">加载中…</span>
              </div>
            </td>
          </tr>

          <tr v-else-if="rows.length === 0">
            <td colspan="8">
              <div class="empty">
                <span class="ico">◍</span>
                <span class="t">没有匹配的投诉</span>
                <span class="d">换个筛选条件试试</span>
              </div>
            </td>
          </tr>

          <tr v-for="row in rows" :key="row.id">
            <td class="mono">{{ row.orderNo || '—' }}</td>
            <td>{{ row.typeLabel }}</td>
            <td>{{ row.studentName || '—' }}</td>
            <td>{{ row.repairerName || '—' }}</td>
            <td>
              <div class="cmp-text" :title="row.content">{{ row.content }}</div>
              <div v-if="row.handleResult" class="cmp-result">处理：{{ row.handleResult }}</div>
            </td>
            <td style="text-align: center">
              <span class="tag" :class="statusTagClass(row.status)">{{ row.statusLabel }}</span>
            </td>
            <td class="num" style="color: var(--ink-3); font-size: 12.5px">{{ row.createdAt }}</td>
            <td>
              <button v-if="!isClosed(row.status)" class="btn btn-sm btn-ghost" @click="openHandle(row)">
                处理
              </button>
              <span v-else style="color: var(--ink-3); font-size: 12.5px">已终结</span>
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

    <!-- 处理弹窗 -->
    <Modal :open="handleOpen" title="处理投诉" width="520px" @close="handleOpen = false">
      <div v-if="current" class="handle-form">
        <div class="hf-row">
          <span class="hf-l">工单</span>
          <span class="mono">{{ current.orderNo }}</span>
        </div>
        <div class="hf-row">
          <span class="hf-l">投诉人</span>
          <span>{{ current.studentName }} · {{ current.typeLabel }}</span>
        </div>
        <div class="hf-content">{{ current.content }}</div>

        <div class="hf-row" style="margin-top: 14px">
          <span class="hf-l">结论</span>
          <select v-model="handle.status" class="field" style="width: 180px">
            <option value="PROCESSING">处理中（受理，暂不定性）</option>
            <option value="RESOLVED">成立（扣维修人员 10 分）</option>
            <option value="REJECTED">不成立（不扣分）</option>
          </select>
        </div>

        <textarea
          v-model="handle.result"
          class="field"
          rows="4"
          maxlength="500"
          style="margin-top: 10px"
          placeholder="处理意见（结论为成立/不成立时必填，会展示给投诉人）"
        />

        <div v-if="handleError" class="form-err">{{ handleError }}</div>
      </div>

      <template #footer>
        <button class="btn" @click="handleOpen = false">取消</button>
        <button
          class="btn"
          :class="handle.status === 'RESOLVED' ? 'btn-danger' : 'btn-primary'"
          :disabled="busy"
          @click="submitHandle"
        >
          {{ busy ? '提交中…' : '提交' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import Modal from '../ui/Modal.vue'
import { toastErr, toastOk } from '../ui/toast'
import { handleComplaint, pageComplaints, type ComplaintPageVO } from '../api/complaint'
import type { ComplaintVO } from '../api/order'

const loading = ref(false)
const rows = ref<ComplaintVO[]>([])
const total = ref(0)
const rate = ref(0)
const summary = ref<ComplaintPageVO | null>(null)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  type: '',
  status: ''
})

const types = [
  { value: 'ATTITUDE', label: '服务态度' },
  { value: 'SLOW', label: '响应拖沓' },
  { value: 'QUALITY', label: '维修质量' },
  { value: 'OTHER', label: '其他问题' }
]

const pages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

const pageList = computed(() => {
  const totalPages = pages.value
  const cur = query.page
  const win: number[] = []
  const start = Math.max(1, Math.min(cur - 2, totalPages - 4))
  for (let i = start; i < start + 5 && i <= totalPages; i++) win.push(i)
  return win
})

const cards = computed(() => [
  {
    status: 'PENDING',
    label: '待受理',
    value: summary.value?.pendingCount ?? 0,
    desc: '需要尽快给出结论',
    icon: 'clock'
  },
  {
    status: 'PROCESSING',
    label: '处理中',
    value: summary.value?.processingCount ?? 0,
    desc: '已受理，核实中',
    icon: 'search'
  },
  {
    status: 'RESOLVED',
    label: '已成立',
    value: summary.value?.resolvedCount ?? 0,
    desc: '已扣减维修人员信誉分',
    icon: 'alert'
  },
  {
    status: 'REJECTED',
    label: '不成立',
    value: summary.value?.rejectedCount ?? 0,
    desc: '核查后驳回',
    icon: 'check'
  }
])

async function load() {
  loading.value = true
  try {
    const data = await pageComplaints({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      type: query.type || undefined,
      status: query.status || undefined
    })
    rows.value = data.page?.list || []
    total.value = data.page?.total || 0
    rate.value = data.complaintRate ?? 0
    summary.value = data
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.type = ''
  query.status = ''
  query.page = 1
  load()
}

function pickStatus(status: string) {
  query.status = query.status === status ? '' : status
  query.page = 1
  load()
}

function go(page: number) {
  if (page < 1 || page > pages.value || page === query.page) return
  query.page = page
  load()
}

/** 投诉状态标签统一为石板灰，与工单状态标签保持同一色系 */
function statusTagClass(_status: string) {
  return 'tag-slate'
}

function isClosed(status: string) {
  return status === 'RESOLVED' || status === 'REJECTED'
}

/* ---------------- 处理 ---------------- */

const handleOpen = ref(false)
const busy = ref(false)
const handleError = ref('')
const current = ref<ComplaintVO | null>(null)
const handle = reactive({ status: 'RESOLVED', result: '' })

function openHandle(row: ComplaintVO) {
  current.value = row
  handle.status = 'RESOLVED'
  handle.result = ''
  handleError.value = ''
  handleOpen.value = true
}

async function submitHandle() {
  if (!current.value) return
  handleError.value = ''
  if (handle.status !== 'PROCESSING' && !handle.result.trim()) {
    handleError.value = '结论为成立或不成立时必须填写处理意见'
    return
  }
  busy.value = true
  try {
    await handleComplaint(current.value.id, {
      status: handle.status,
      result: handle.result.trim() || undefined
    })
    toastOk(
      handle.status === 'RESOLVED'
        ? '已判定成立，维修人员信誉分已扣减'
        : handle.status === 'REJECTED'
          ? '已判定不成立'
          : '已受理，状态更新为处理中'
    )
    handleOpen.value = false
    load()
  } catch (e: any) {
    handleError.value = e?.message || '提交失败，请稍后重试'
    toastErr(handleError.value)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.kpi.clickable {
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.kpi.clickable:hover {
  border-color: var(--brand);
  transform: translateY(-1px);
}
.kpi.active {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-l);
}
.cmp-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
}
.cmp-result {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}
.handle-form .hf-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}
.hf-l {
  width: 60px;
  flex: none;
  color: var(--ink-3);
  font-size: 12.5px;
}
.hf-content {
  border: 1px solid var(--line-2);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-2);
  background: #fafbfc;
}
</style>
