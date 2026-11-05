<template>
  <div>
    <div class="page-hd">
      <div>
        <h1>信息更改审核</h1>
        <p class="sub">审核维修工提交的姓名 / 工号 / 所属区域更改申请，通过后信息才生效</p>
      </div>
      <button class="btn btn-sm" @click="load">
        <Icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <h3>申请列表</h3>
        <span class="cnt">共 {{ total }} 条</span>
        <div class="toolbar" style="margin-left: auto">
          <select v-model="query.status" class="field" style="width: 118px">
            <option value="">全部状态</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已打回</option>
          </select>
          <button class="btn btn-sm" @click="load">查询</button>
        </div>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th style="width: 110px">申请人</th>
            <th style="width: 90px">工号</th>
            <th style="width: 90px">变更项目</th>
            <th>变更内容</th>
            <th>申请理由</th>
            <th style="width: 90px; text-align: center">状态</th>
            <th style="width: 150px">提交时间</th>
            <th style="width: 90px">操作</th>
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
                <span class="t">没有匹配的申请</span>
              </div>
            </td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.applicantName || '—' }}</td>
            <td class="mono">{{ row.applicantStaffNo || '—' }}</td>
            <td>{{ row.fieldLabel }}</td>
            <td>
              <span class="mono" style="color: var(--ink-3)">{{ row.oldValue || '—' }}</span>
              <span style="margin: 0 6px; color: var(--brand)">→</span>
              <b>{{ row.newValue }}</b>
            </td>
            <td>
              <div class="cmp-text" :title="row.reason">{{ row.reason }}</div>
            </td>
            <td style="text-align: center">
              <span class="tag" :class="statusTagClass(row.status)">{{ row.statusLabel }}</span>
            </td>
            <td class="num" style="color: var(--ink-3); font-size: 12.5px">{{ row.createdAt?.slice(0, 16) }}</td>
            <td>
              <button v-if="row.status === 'PENDING'" class="btn btn-sm btn-primary" @click="openApprove(row)">
                通过
              </button>
              <button v-else-if="row.status === 'PENDING'" class="btn btn-sm btn-ghost" @click="openReject(row)">
                打回
              </button>
              <span v-else style="color: var(--ink-3); font-size: 12.5px">
                {{ row.status === 'REJECTED' && row.rejectReason ? '已打回' : '已通过' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <span class="info">第 {{ query.page }} / {{ pages }} 页 · 每页 {{ query.size }} 条</span>
        <button class="pg" :disabled="query.page <= 1" @click="go(query.page - 1)">上一页</button>
        <button class="pg" :disabled="query.page >= pages" @click="go(query.page + 1)">下一页</button>
      </div>
    </div>

    <!-- 打回原因弹窗 -->
    <div v-if="rejectTarget" class="mask" @click.self="rejectTarget = null">
      <div class="modal">
        <div class="modal-hd">
          <h3>打回申请</h3>
          <button class="close" @click="rejectTarget = null">✕</button>
        </div>
        <div class="modal-bd">
          <p style="margin-bottom: 10px">
            打回「{{ rejectTarget.applicantName }}」的{{ rejectTarget.fieldLabel }}更改申请，请填写原因（申请人可见）。
          </p>
          <textarea v-model="rejectReason" class="field" rows="3" placeholder="请输入打回原因" />
        </div>
        <div class="modal-ft">
          <button class="btn" @click="rejectTarget = null">取消</button>
          <button class="btn btn-danger" :disabled="rejecting" @click="doReject">确认打回</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import { confirmDialog } from '../ui/dialog'
import { toastErr, toastOk } from '../ui/toast'
import {
  reviewProfileChange,
  reviewProfileChangeList,
  type ProfileChangeVO
} from '../api/profile'

const rows = ref<ProfileChangeVO[]>([])
const total = ref(0)
const loading = ref(false)
const rejecting = ref(false)
const rejectTarget = ref<ProfileChangeVO | null>(null)
const rejectReason = ref('')

const query = reactive({ status: '', page: 1, size: 20 })
const pages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await reviewProfileChangeList(query)
    rows.value = page?.list || []
    total.value = page?.total || 0
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function go(page: number) {
  query.page = page
  load()
}

function statusTagClass(status: string): string {
  if (status === 'APPROVED') return 'tag-ok'
  if (status === 'REJECTED') return 'tag-crit'
  return 'tag-warn'
}

async function openApprove(row: ProfileChangeVO) {
  const ok = await confirmDialog({
    title: '通过申请',
    message: `确认通过 ${row.applicantName} 的「${row.fieldLabel}」更改：${row.oldValue || '—'} → ${row.newValue}？通过后将立即生效。`,
    okText: '确认通过'
  })
  if (!ok) return
  try {
    await reviewProfileChange(row.id, { status: 'APPROVED' })
    toastOk('已通过，信息已更新')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

function openReject(row: ProfileChangeVO) {
  rejectTarget.value = row
  rejectReason.value = ''
}

async function doReject() {
  if (!rejectTarget.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    toastErr('请填写打回原因')
    return
  }
  rejecting.value = true
  try {
    await reviewProfileChange(rejectTarget.value.id, { status: 'REJECTED', rejectReason: reason })
    toastOk('已打回')
    rejectTarget.value = null
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    rejecting.value = false
  }
}
</script>

<style scoped>
.cmp-text {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-2);
}
</style>
