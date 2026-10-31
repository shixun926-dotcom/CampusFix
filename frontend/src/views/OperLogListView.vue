<template>
  <div>
    <div class="page-hd">
      <div>
        <h1>操作日志</h1>
        <p class="sub">记录管理端与业务关键写操作的执行情况，成败均落库，用于审计与问题回溯</p>
      </div>
      <button class="btn btn-sm" @click="load">
        <Icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <h3>日志列表</h3>
        <span class="cnt">共 {{ total }} 条 · 成功 {{ okCount }} / 失败 {{ failCount }}</span>

        <div class="toolbar" style="margin-left: auto">
          <input
            v-model="query.username"
            class="field"
            style="width: 130px"
            placeholder="操作人账号"
            @keyup.enter="onSearch"
          />
          <select v-model="query.module" class="field" style="width: 128px">
            <option value="">全部模块</option>
            <option v-for="m in modules" :key="m" :value="m">{{ m }}</option>
          </select>
          <select v-model="query.status" class="field" style="width: 108px">
            <option value="">全部结果</option>
            <option value="1">成功</option>
            <option value="0">失败</option>
          </select>
          <input v-model="query.begin" type="date" class="field" style="width: 148px" />
          <span class="range-sep">至</span>
          <input v-model="query.end" type="date" class="field" style="width: 148px" />
          <button class="btn btn-sm" @click="onReset">重置</button>
          <button class="btn btn-sm btn-primary" @click="onSearch">查询</button>
        </div>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 150px">时间</th>
            <th style="width: 96px">操作人</th>
            <th style="width: 96px">模块</th>
            <th style="width: 110px">操作</th>
            <th>接口</th>
            <th style="width: 92px; text-align: center">结果</th>
            <th style="width: 76px; text-align: right">耗时</th>
            <th style="width: 72px">操作</th>
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
                <span class="t">没有匹配的日志</span>
                <span class="d">调整筛选条件后重试</span>
              </div>
            </td>
          </tr>

          <tr v-for="row in rows" :key="row.id" class="log-row" @click="openDetail(row)">
            <td class="num" style="color: var(--ink-3); font-size: 12.5px">{{ row.createdAt }}</td>
            <td>{{ row.username || '—' }}</td>
            <td><span class="tag tag-ink">{{ row.module || '—' }}</span></td>
            <td>{{ row.operation || '—' }}</td>
            <td class="mono uri" :title="row.requestUri">{{ shortUri(row.requestUri) }}</td>
            <td style="text-align: center">
              <span class="tag" :class="row.status === 1 ? 'tag-ok' : 'tag-crit'">
                {{ row.status === 1 ? '成功' : '失败' }}
              </span>
            </td>
            <td class="num" style="font-size: 12.5px">{{ row.costMs != null ? row.costMs + 'ms' : '—' }}</td>
            <td>
              <button class="btn btn-sm btn-ghost" @click.stop="openDetail(row)">详情</button>
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

    <!-- 参数详情弹窗 -->
    <Modal :open="detailOpen" :title="detail?.operation || '日志详情'" @close="detailOpen = false">
      <div v-if="detail" class="detail">
        <div class="d-row"><b>操作人</b><span>{{ detail.username || '—' }}（ID {{ detail.userId }}）</span></div>
        <div class="d-row"><b>模块 / 操作</b><span>{{ detail.module }} · {{ detail.operation }}</span></div>
        <div class="d-row"><b>接口</b><span class="mono">{{ detail.method }}</span></div>
        <div class="d-row"><b>地址</b><span class="mono">{{ detail.requestUri }}</span></div>
        <div class="d-row"><b>IP</b><span class="mono">{{ detail.ip || '—' }}</span></div>
        <div class="d-row"><b>时间 / 耗时</b><span>{{ detail.createdAt }} · {{ detail.costMs }}ms</span></div>
        <div class="d-row">
          <b>结果</b>
          <span>
            <span class="tag" :class="detail.status === 1 ? 'tag-ok' : 'tag-crit'">
              {{ detail.status === 1 ? '成功' : '失败' }}
            </span>
          </span>
        </div>
        <template v-if="detail.errorMsg">
          <div class="d-row"><b>错误信息</b><span class="err">{{ detail.errorMsg }}</span></div>
        </template>
        <div class="d-row col">
          <b>请求参数（已脱敏）</b>
          <pre class="param">{{ prettyParam }}</pre>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import Modal from '../ui/Modal.vue'
import { listOperLogs, listOperLogModules, type OperLogVO } from '../api/log'

const rows = ref<OperLogVO[]>([])
const modules = ref<string[]>([])
const total = ref(0)
const loading = ref(true)
const detailOpen = ref(false)
const detail = ref<OperLogVO | null>(null)

const query = ref({
  page: 1,
  size: 20,
  username: '',
  module: '',
  status: '' as string | number,
  begin: '',
  end: ''
})

const pages = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)))
const okCount = computed(() => rows.value.filter((r) => r.status === 1).length)
const failCount = computed(() => rows.value.filter((r) => r.status === 0).length)
const pageList = computed(() => {
  const list: number[] = []
  const from = Math.max(1, query.value.page - 2)
  const to = Math.min(pages.value, from + 4)
  for (let p = from; p <= to; p++) list.push(p)
  return list
})

const prettyParam = computed(() => {
  const raw = detail.value?.requestParam
  if (!raw) return '（无参数）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

async function load() {
  loading.value = true
  try {
    const [data, mods]: any = await Promise.all([
      listOperLogs({ ...query.value, status: query.value.status === '' ? undefined : query.value.status }),
      modules.value.length ? Promise.resolve([]) : listOperLogModules()
    ])
    rows.value = data?.list || []
    total.value = Number(data?.total) || 0
    if (mods?.length) modules.value = mods
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
  query.value = { page: 1, size: 20, username: '', module: '', status: '', begin: '', end: '' }
  load()
}

function go(p: number) {
  query.value.page = p
  load()
}

function openDetail(row: OperLogVO) {
  detail.value = row
  detailOpen.value = true
}

function shortUri(uri?: string) {
  if (!uri) return '—'
  return uri.replace(/^\/api\/v1\//, '')
}

onMounted(load)
</script>

<style scoped>
.range-sep {
  font-size: 12px;
  color: var(--ink-3);
  align-self: center;
}
.log-row {
  cursor: pointer;
}
.uri {
  font-size: 12px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 13px;
}
.d-row {
  display: flex;
  gap: 12px;
  line-height: 1.6;
}
.d-row b {
  flex: none;
  width: 96px;
  color: var(--ink-3);
  font-weight: 500;
}
.d-row.col {
  flex-direction: column;
  gap: 6px;
}
.d-row span {
  color: var(--ink);
  word-break: break-all;
}
.d-row .err {
  color: var(--crit, #c0392b);
}
.param {
  margin: 0;
  background: var(--bg);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
