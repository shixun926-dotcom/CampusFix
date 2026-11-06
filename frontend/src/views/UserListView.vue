<template>
  <div>
    <div class="page-hd">
      <div>
        <span class="eyebrow">Identity & Access</span>
        <h1>用户管理</h1>
        <p class="sub">账号由管理员统一开通，学生无需注册</p>
      </div>
      <div class="hd-btns">
        <button class="btn btn-sm" @click="load">
          <Icon name="refresh" :size="15" />刷新
        </button>
        <button v-if="canImport" class="btn btn-sm" @click="openImport">
          <Icon name="file" :size="15" />批量导入
        </button>
        <button v-if="canAdd" class="btn btn-sm btn-primary" @click="openStudent">
          <Icon name="user" :size="15" />添加学生
        </button>
      </div>
    </div>

    <div class="status-strip">
      <span class="strip-label"><Icon name="users" :size="13" />本页概览</span>
      <span class="strip-chip"><span class="dot" style="background: #4f6ef7" />账号 <b>{{ rows.length }}</b></span>
      <span class="strip-chip"><span class="dot" style="background: #12a150" />学生 <b>{{ pageSummary.students }}</b></span>
      <span class="strip-chip"><span class="dot" style="background: #e63946" />维修人员 <b>{{ pageSummary.repairers }}</b></span>
      <span class="strip-chip"><span class="dot" style="background: #9aa3b2" />已禁用 <b>{{ pageSummary.disabled }}</b></span>
    </div>

    <div class="tablewrap">
      <div class="tbl-hd">
        <h3>用户列表</h3>
        <span class="cnt">共 {{ total }} 条</span>

        <div v-if="selectedIds.length" class="bulk-bar">
          <span class="bulk-cnt">已选 <b>{{ selectedIds.length }}</b> 名</span>
          <button v-if="canDelete" class="btn btn-sm btn-danger" :disabled="deleting" @click="onBatchDelete">
            <Icon name="trash" :size="14" />{{ deleting ? '删除中…' : '批量删除' }}
          </button>
          <button class="btn btn-sm btn-ghost" @click="clearSelection">取消选择</button>
        </div>

        <div class="toolbar" style="margin-left: auto">
          <input
            v-model="query.keyword"
            class="field"
            style="width: 200px"
            placeholder="账号 / 姓名 / 手机号"
            @keyup.enter="onSearch"
          />
          <select v-model="query.roleCode" class="field" style="width: 126px">
            <option value="">全部角色</option>
            <option value="STUDENT">学生</option>
            <option value="REPAIRER">维修人员</option>
            <option value="MANAGER">后勤管理员</option>
            <option value="ADMIN">系统管理员</option>
          </select>
          <select v-model="query.status" class="field" style="width: 106px">
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="0">禁用</option>
          </select>
          <button class="btn btn-sm" @click="onReset">重置</button>
          <button class="btn btn-sm btn-primary" @click="onSearch">查询</button>
        </div>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th class="chk-col">
              <span
                v-if="canDelete"
                class="chk"
                :class="{ on: allChecked, part: someChecked }"
                :title="allChecked ? '取消全选' : '全选本页'"
                @click="toggleAll"
              />
            </th>
            <th>用户</th>
            <th style="width: 150px">班级</th>
            <th style="width: 118px">部门</th>
            <th style="width: 140px">角色</th>
            <th style="width: 124px">手机号</th>
            <th style="width: 118px">信誉分</th>
            <th style="width: 80px; text-align: center">状态</th>
            <th style="width: 150px">最后登录</th>
            <th style="width: 172px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10">
              <div class="empty"><span class="spin" /><span class="d">加载中…</span></div>
            </td>
          </tr>

          <tr v-else-if="rows.length === 0">
            <td colspan="10">
              <div class="empty">
                <span class="ico"><Icon name="users" :size="24" /></span>
                <span class="t">没有匹配的账号</span>
                <span class="d">换个关键字，或清空筛选条件试试</span>
              </div>
            </td>
          </tr>

          <tr v-for="row in rows" :key="row.id" :class="{ 'row-on': isSelected(row.id) }">
            <td class="chk-col">
              <span
                v-if="canDelete"
                class="chk"
                :class="{ on: isSelected(row.id), off: isSelf(row) }"
                :title="isSelf(row) ? '当前登录账号不可删除' : ''"
                @click="toggleOne(row)"
              />
            </td>
            <td>
              <div class="user-cell">
                <span class="avatar-sm user-avatar" :class="avatarTone(row)">{{ row.realName?.charAt(0) || 'U' }}</span>
                <div class="user-main">
                  <div class="u-name">
                    {{ row.realName }}
                    <span v-if="row.status === 0" class="tag tag-ink">已禁用</span>
                  </div>
                  <div class="u-no mono">{{ row.username }}</div>
                </div>
              </div>
            </td>
            <td>
              <span v-if="row.className">{{ row.className }}</span>
              <span v-else class="muted">—</span>
            </td>
            <td>
              <span v-if="row.deptName">{{ row.deptName }}</span>
              <span v-else class="muted">—</span>
            </td>
            <td>
              <span
                v-for="(name, i) in row.roleNames"
                :key="i"
                class="tag"
                :class="roleTagClass(row.roleCodes?.[i])"
                style="margin-right: 5px"
              >
                {{ name }}
              </span>
            </td>
            <td class="num" style="color: var(--ink-2)">{{ maskPhone(row.phone) }}</td>
            <td>
              <div class="credit-cell">
                <span class="credit-num" :class="{ low: row.creditScore < 60 }">{{ row.creditScore }}</span>
                <span class="credit-bar">
                  <i
                    :style="{ width: Math.min(100, row.creditScore) + '%' }"
                    :class="{ low: row.creditScore < 60 }"
                  />
                </span>
              </div>
            </td>
            <td style="text-align: center">
              <span
                class="switch"
                :class="{ on: row.status === 1 }"
                :title="row.status === 1 ? '点击禁用' : '点击启用'"
                @click="onToggle(row)"
              />
            </td>
            <td class="num muted" style="font-size: 12.5px">
              {{ row.lastLoginAt || '从未登录' }}
            </td>
            <td>
              <div class="row-acts">
                <button v-if="canEdit" class="btn btn-sm btn-ghost" @click="openEdit(row)">
                  <Icon name="edit" :size="14" />编辑
                </button>
                <button class="btn btn-sm btn-ghost reset-btn" @click="onResetPwd(row)">
                  <Icon name="key" :size="14" />重置
                </button>
                <button
                  v-if="canDelete"
                  class="btn btn-sm btn-ghost del-btn"
                  :disabled="isSelf(row)"
                  :title="isSelf(row) ? '不能删除当前登录账号' : ''"
                  @click="onDelete(row)"
                >
                  <Icon name="trash" :size="14" />删除
                </button>
              </div>
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

    <!-- ==================== 添加单个学生 ==================== -->
    <Modal :open="studentOpen" title="添加学生" @close="closeStudent">
      <div class="form-grid">
        <div class="full">
          <label class="lbl">姓名 <span style="color: var(--crit)">*</span></label>
          <input v-model="form.realName" class="field" placeholder="如 张三" />
        </div>
        <div>
          <label class="lbl">学号 <span style="color: var(--crit)">*</span></label>
          <input v-model="form.studentNo" class="field" placeholder="4-30 位数字或字母" />
        </div>
        <div>
          <label class="lbl">班级 <span style="color: var(--crit)">*</span></label>
          <input v-model="form.className" class="field" placeholder="如 计算机2201" />
        </div>
        <div class="full">
          <label class="lbl">手机号</label>
          <input v-model="form.phone" class="field" placeholder="选填，11 位手机号" />
        </div>
      </div>

      <p v-if="formErr" class="form-err">{{ formErr }}</p>
      <p class="form-err" style="color: var(--ink-3)">
        账号即为学号，初始密码 Cf@123456，学生首次登录后需修改。
      </p>

      <template #footer>
        <button class="btn" @click="closeStudent">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitStudent">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <!-- ==================== 批量导入 ==================== -->
    <Modal :open="importOpen" title="批量导入学生" width="660px" @close="closeImport">
      <div class="notice">
        <span class="g">!</span>
        <div>
          表头必须与模板完全一致（<b>姓名 / 学号 / 班级 / 手机号</b>），不要增删列或调整顺序。
          <div style="margin-top: 8px">
            <button class="btn btn-sm" @click="onDownloadTpl">
              <Icon name="file" :size="14" />下载模板
            </button>
          </div>
        </div>
      </div>

      <div
        class="dropzone"
        :class="{ on: dragging }"
        style="margin-top: 14px"
        @click="pickFile"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="t">点击选择文件，或把 Excel 拖到这里</div>
        <div class="d">支持 .xlsx / .xls，单次最多 2000 行，空白行自动跳过</div>
        <div v-if="file" class="file"><Icon name="file" :size="14" />{{ file.name }}</div>
      </div>
      <input
        ref="fileInput"
        type="file"
        accept=".xlsx,.xls"
        style="display: none"
        @change="onFileChange"
      />

      <div v-if="result" class="result">
        <div class="result-summary">
          <div class="res-card">
            <div class="l">表格行数</div>
            <div class="n">{{ result.total }}</div>
          </div>
          <div class="res-card ok">
            <div class="l">导入成功</div>
            <div class="n">{{ result.success }}</div>
          </div>
          <div class="res-card bad">
            <div class="l">导入失败</div>
            <div class="n">{{ result.failed }}</div>
          </div>
        </div>

        <p v-if="result.failed === 0" style="margin-top: 12px; color: #0a7a0a; font-size: 13px">
          全部导入成功，学生可用「学号 + Cf@123456」登录。
        </p>

        <div v-if="result.errors.length" class="err-list">
          <table class="tbl compact">
            <thead>
              <tr>
                <th style="width: 64px">行号</th>
                <th style="width: 110px">学号</th>
                <th style="width: 88px">姓名</th>
                <th>失败原因</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(e, i) in result.errors" :key="i">
                <td class="num">{{ e.rowNum }}</td>
                <td class="mono">{{ e.studentNo || '—' }}</td>
                <td>{{ e.realName || '—' }}</td>
                <td style="color: var(--crit)">{{ e.reason }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <button class="btn" @click="closeImport">{{ result ? '关闭' : '取消' }}</button>
        <button v-if="!result" class="btn btn-primary" :disabled="!file || uploading" @click="doImport">
          {{ uploading ? '导入中…' : '开始导入' }}
        </button>
        <button v-else class="btn btn-primary" @click="finishImport">完成</button>
      </template>
    </Modal>

    <!-- ==================== 编辑用户 ==================== -->
    <UserEditModal
      :open="editOpen"
      :user-id="editUserId"
      @close="editOpen = false"
      @saved="onEditSaved"
    />

    <!-- ==================== 删除结果（批量删除有跳过项时展示） ==================== -->
    <Modal :open="delResultOpen" title="删除结果" width="560px" @close="delResultOpen = false">
      <div class="del-sum">
        <span class="del-ok"><Icon name="check" :size="15" />已删除 {{ delResult.success }} 名</span>
        <span v-if="delResult.failed.length" class="del-bad">
          <Icon name="alert" :size="15" />跳过 {{ delResult.failed.length }} 名
        </span>
      </div>

      <table v-if="delResult.failed.length" class="tbl compact" style="margin-top: 12px">
        <thead>
          <tr>
            <th style="width: 190px">成员</th>
            <th>未删除原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in delResult.failed" :key="f.id">
            <td>{{ f.name }}</td>
            <td class="del-reason">{{ f.reason }}</td>
          </tr>
        </tbody>
      </table>

      <p class="del-tip">
        被删除的成员将无法再登录，其历史工单、投诉与操作记录仍保留在系统中；如需重新开通，用同学号 / 工号新建即可。
      </p>

      <template #footer>
        <button class="btn btn-primary" @click="delResultOpen = false">知道了</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import UserEditModal from './UserEditModal.vue'
import Modal from '../ui/Modal.vue'
import { confirmDialog } from '../ui/dialog'
import { toastErr, toastOk } from '../ui/toast'
import { useAuthStore } from '../stores/auth'
import {
  createStudent,
  deleteUser,
  deleteUsersBatch,
  downloadStudentTemplate,
  importStudents,
  pageUsers,
  resetPassword,
  toggleUserStatus,
  type DeleteResult,
  type ImportResult,
  type UserVO
} from '../api/system'

const auth = useAuthStore()
const canAdd = computed(() => auth.has('user:add'))
const canImport = computed(() => auth.has('user:import'))
const canEdit = computed(() => auth.has('user:edit'))
const canDelete = computed(() => auth.has('user:delete'))

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 20, keyword: '', roleCode: '', status: '' as string })

/* ---------------------------- 多选（删除用） ---------------------------- */

/** 当前页已勾选的成员 id；翻页 / 重新查询后自动剔除已不在本页的项 */
const selectedIds = ref<number[]>([])
const deleting = ref(false)

/** 当前登录账号 id：档案接口返回字段为 userId（兼容测试/旧快照的 id） */
const currentUserId = computed(() => {
  const u: any = auth.user
  return Number(u?.userId ?? u?.id ?? 0)
})
const isSelf = (row: any) => currentUserId.value > 0 && Number(row.id) === currentUserId.value
const isSelected = (id: number) => selectedIds.value.includes(id)
const allChecked = computed(
  () => rows.value.length > 0 && rows.value.every((r) => isSelected(r.id) || isSelf(r))
)
const someChecked = computed(
  () => !allChecked.value && rows.value.some((r) => isSelected(r.id))
)

/** 单选：当前登录账号不允许勾选（后端同样会拒绝） */
function toggleOne(row: any) {
  if (isSelf(row)) return
  const i = selectedIds.value.indexOf(row.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(row.id)
}

/** 全选本页：已勾满时再点即取消 */
function toggleAll() {
  if (allChecked.value) {
    selectedIds.value = []
    return
  }
  selectedIds.value = rows.value.filter((r) => !isSelf(r)).map((r) => r.id)
}

function clearSelection() {
  selectedIds.value = []
}

const pageSummary = computed(() => {
  const hasRole = (row: any, code: string) => (row.roleCodes || []).includes(code)
  return {
    students: rows.value.filter((r) => hasRole(r, 'STUDENT')).length,
    repairers: rows.value.filter((r) => hasRole(r, 'REPAIRER')).length,
    disabled: rows.value.filter((r) => r.status === 0).length
  }
})

function avatarTone(row: any) {
  const codes: string[] = row.roleCodes || []
  if (codes.includes('ADMIN')) return 'tone-admin'
  if (codes.includes('MANAGER')) return 'tone-manager'
  if (codes.includes('REPAIRER')) return 'tone-repairer'
  return 'tone-student'
}

const pages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const pageList = computed(() => {
  const totalPages = pages.value
  const cur = query.page
  const win: number[] = []
  const start = Math.max(1, Math.min(cur - 2, totalPages - 4))
  for (let i = start; i < start + 5 && i <= totalPages; i++) win.push(i)
  return win
})

async function load() {
  loading.value = true
  try {
    const data: any = await pageUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roleCode: query.roleCode || undefined,
      status: query.status === '' ? undefined : Number(query.status)
    })
    rows.value = data.list || []
    total.value = data.total || 0
    // 翻页或删除后，剔除已不在本页/已删除的勾选项
    selectedIds.value = selectedIds.value.filter((id) => rows.value.some((r: any) => r.id === id))
  } catch {
    rows.value = []
    total.value = 0
    selectedIds.value = []
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
  query.roleCode = ''
  query.status = ''
  query.page = 1
  load()
}

function go(page: number) {
  if (page < 1 || page > pages.value || page === query.page) return
  query.page = page
  load()
}

async function onToggle(row: any) {
  const disabling = row.status === 1
  const ok = await confirmDialog({
    title: disabling ? '禁用账号' : '启用账号',
    message: disabling
      ? `禁用后 ${row.realName}（${row.username}）将无法登录，且已登录的会话会被立即踢下线。`
      : `启用后 ${row.realName}（${row.username}）可以重新登录系统。`,
    okText: disabling ? '确认禁用' : '确认启用',
    danger: disabling
  })
  if (!ok) return

  try {
    await toggleUserStatus(row.id, disabling ? 0 : 1)
    row.status = disabling ? 0 : 1
    toastOk(disabling ? '已禁用，该用户已被强制下线' : '已启用')
  } catch {
    // 统一错误提示
  }
}

async function onResetPwd(row: any) {
  const ok = await confirmDialog({
    title: '重置密码',
    message: `将 ${row.realName}（${row.username}）的密码重置为系统默认值？该用户当前会话会被注销。`,
    okText: '重置'
  })
  if (!ok) return
  try {
    const data: any = await resetPassword(row.id)
    toastOk(`已重置，新密码：${data.password}`)
  } catch {
    toastErr('重置失败，请稍后重试')
  }
}

/* ---------------------------- 删除 / 批量删除 ---------------------------- */

const delResultOpen = ref(false)
const delResult = reactive<DeleteResult>({ success: 0, failed: [] })

/** 单条删除：二次确认 → 调接口 → 就地移除该行 */
async function onDelete(row: any) {
  const ok = await confirmDialog({
    title: '删除成员',
    message: `确定删除 ${row.realName}（${row.username}）？删除后该账号立即失效、无法登录；其历史工单与操作记录仍会保留。`,
    okText: '删除',
    danger: true
  })
  if (!ok) return

  try {
    await deleteUser(row.id)
    rows.value = rows.value.filter((r) => r.id !== row.id)
    total.value = Math.max(0, total.value - 1)
    selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    toastOk(`已删除 ${row.realName}`)
  } catch {
    // 统一错误提示（后端会说明不能删的原因）
  }
}

/**
 * 批量删除：逐条校验在后端完成，可能部分成功。
 * 全部成功只弹轻提示；有跳过项时打开结果弹窗，逐条说明原因。
 */
async function onBatchDelete() {
  const chosen = rows.value.filter((r) => selectedIds.value.includes(r.id) && !isSelf(r))
  if (!chosen.length) return

  const names = chosen.slice(0, 3).map((r) => r.realName).join('、')
  const rest = chosen.length > 3 ? ` 等 ${chosen.length} 名` : ''
  const ok = await confirmDialog({
    title: '批量删除成员',
    message: `将删除已选的 ${chosen.length} 名成员（${names}${rest}）。删除后账号立即失效；仍有未完结工单的成员会被跳过。`,
    okText: `删除 ${chosen.length} 名`,
    danger: true
  })
  if (!ok) return

  deleting.value = true
  try {
    const res = await deleteUsersBatch(chosen.map((r) => r.id))
    delResult.success = res?.success ?? 0
    delResult.failed = res?.failed ?? []
    clearSelection()
    await load()
    if (delResult.failed.length) {
      delResultOpen.value = true
    } else {
      toastOk(`已删除 ${delResult.success} 名成员`)
    }
  } catch {
    // 统一错误提示
  } finally {
    deleting.value = false
  }
}

/* ---------------------------- 编辑用户 ---------------------------- */

const editOpen = ref(false)
const editUserId = ref<number | null>(null)

function openEdit(row: any) {
  editUserId.value = row.id
  editOpen.value = true
}

/** 用后端返回的最新 VO 就地替换该行，避免整页重新加载 */
function onEditSaved(user: UserVO) {
  const i = rows.value.findIndex((r) => r.id === user.id)
  if (i >= 0) rows.value[i] = { ...rows.value[i], ...user }
}

/** 角色标签统一为石板灰，角色差异由文字承载（避免整页出现多套色相） */
function roleTagClass(_code?: string) {
  return 'tag-slate'
}

function maskPhone(phone?: string) {
  if (!phone || phone.length < 7) return phone || '—'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

/* ---------------------------- 添加学生 ---------------------------- */

const studentOpen = ref(false)
const saving = ref(false)
const formErr = ref('')
const form = reactive({ realName: '', studentNo: '', className: '', phone: '' })

function openStudent() {
  form.realName = ''
  form.studentNo = ''
  form.className = ''
  form.phone = ''
  formErr.value = ''
  studentOpen.value = true
}

function closeStudent() {
  studentOpen.value = false
}

async function submitStudent() {
  formErr.value = ''
  if (!form.realName.trim()) {
    formErr.value = '请输入姓名'
    return
  }
  if (!/^[0-9A-Za-z]{4,30}$/.test(form.studentNo.trim())) {
    formErr.value = '学号需为 4-30 位数字或字母'
    return
  }
  if (!form.className.trim()) {
    formErr.value = '请输入班级'
    return
  }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) {
    formErr.value = '手机号格式不正确'
    return
  }

  saving.value = true
  try {
    await createStudent({
      realName: form.realName.trim(),
      studentNo: form.studentNo.trim(),
      className: form.className.trim(),
      phone: form.phone.trim()
    })
    studentOpen.value = false
    toastOk('学生已添加，初始密码 Cf@123456')
    query.page = 1
    load()
  } catch (e: any) {
    formErr.value = e?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/* ---------------------------- 批量导入 ---------------------------- */

const importOpen = ref(false)
const file = ref<File | null>(null)
const dragging = ref(false)
const uploading = ref(false)
const result = ref<ImportResult | null>(null)
const fileInput = ref<HTMLInputElement>()

function openImport() {
  file.value = null
  result.value = null
  dragging.value = false
  importOpen.value = true
}

function closeImport() {
  importOpen.value = false
}

function finishImport() {
  importOpen.value = false
  query.page = 1
  load()
}

function pickFile() {
  fileInput.value?.click()
}

function onFileChange(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) acceptFile(f)
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) acceptFile(f)
}

function acceptFile(f: File) {
  if (!/\.(xlsx|xls)$/i.test(f.name)) {
    toastErr('仅支持 .xlsx / .xls 文件')
    return
  }
  file.value = f
  result.value = null
}

async function doImport() {
  if (!file.value) return
  uploading.value = true
  try {
    const res = await importStudents(file.value)
    result.value = res
    if (res.success > 0) {
      toastOk(`成功导入 ${res.success} 名学生`)
    } else {
      toastErr('没有导入任何数据，请检查失败原因')
    }
  } catch {
    // 表头不符等整体性错误由请求拦截器提示
    result.value = null
  } finally {
    uploading.value = false
  }
}

async function onDownloadTpl() {
  try {
    const blob: any = await downloadStudentTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '学生导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    toastErr('模板下载失败，请稍后重试')
  }
}

onMounted(async () => {
  await load()
  // 权限快照在登录时写入本地，后台新增权限（如 user:delete）后不会自动生效，
  // 因此本页懒刷新一次：无删除权限时静默拉取最新档案，拿到后按钮自动出现。
  if (!canDelete.value) {
    try {
      await auth.fetchProfile()
    } catch {
      // 刷新失败不影响页面其余功能
    }
  }
})
</script>

<style scoped>
.hd-btns {
  display: flex;
  gap: 8px;
}

/* 概览条 */
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
  background: #f5f6fa;
  color: var(--ink-2);
  font-size: 12px;
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

/* 用户单元格 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 13px;
  color: #fff;
  box-shadow: 0 3px 8px rgba(20, 24, 40, 0.12);
}
.user-avatar.tone-admin {
  background: linear-gradient(140deg, #ff7a68, #e63946);
}
.user-avatar.tone-manager {
  background: linear-gradient(140deg, #ffb056, #f59e0b);
}
.user-avatar.tone-repairer {
  background: linear-gradient(140deg, #6e8bff, #4f6ef7);
}
.user-avatar.tone-student {
  background: linear-gradient(140deg, #3fc4c2, #0ea5a4);
}
.user-main { min-width: 0; }
.u-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.u-no {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 1px;
}
.u-no.mono {
  font-family: ui-monospace, "Cascadia Mono", Consolas, Menlo, monospace;
}
.muted { color: var(--ink-3); }

/* 信誉分 */
.credit-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}
.credit-num {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  min-width: 26px;
}
.credit-num.low { color: var(--crit); }
.credit-bar {
  flex: 1;
  height: 6px;
  border-radius: 4px;
  background: var(--line-2);
  overflow: hidden;
  min-width: 40px;
}
.credit-bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #4ade80, #12a150);
  transition: width 0.4s ease;
}
.credit-bar i.low {
  background: linear-gradient(90deg, #f87171, #dc3545);
}
.row-acts {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.reset-btn {
  color: var(--ink-2);
  padding: 0 8px;
}
.reset-btn:hover {
  background: var(--slate-100);
  color: var(--slate-700);
}

/* ---------------- 多选与删除（沿用石板灰色系与既有 danger 语义色） ---------------- */

.chk-col {
  width: 40px;
  text-align: center;
}
.chk {
  display: inline-flex;
  width: 16px;
  height: 16px;
  border-radius: 5px;
  border: 1.5px solid var(--line);
  background: #fff;
  cursor: pointer;
  position: relative;
  transition: border-color 0.15s, background 0.15s;
  vertical-align: middle;
}
.chk:hover {
  border-color: var(--slate-400);
}
.chk.on,
.chk.part {
  background: var(--slate-900);
  border-color: var(--slate-900);
}
.chk.on::after {
  content: "";
  position: absolute;
  left: 4px;
  top: 1px;
  width: 4px;
  height: 8px;
  border: 2px solid #fff;
  border-top: 0;
  border-left: 0;
  transform: rotate(42deg);
}
.chk.part::after {
  content: "";
  position: absolute;
  left: 3px;
  top: 6px;
  width: 8px;
  height: 2px;
  border-radius: 1px;
  background: #fff;
}
.chk.off {
  opacity: 0.35;
  cursor: not-allowed;
}
.chk.off:hover {
  border-color: var(--line);
}
table.tbl tbody tr.row-on {
  background: var(--slate-50);
}

.bulk-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 12px;
  border-left: 1px solid var(--line-2);
}
.bulk-cnt {
  font-size: 12.5px;
  color: var(--slate-600);
}
.bulk-cnt b {
  color: var(--slate-900);
}

.del-btn {
  color: var(--crit);
  padding: 0 8px;
}
.del-btn:hover:not(:disabled) {
  background: #fdf3f4;
  color: var(--crit);
}
.del-btn:disabled {
  color: var(--ink-3);
  opacity: 0.6;
}

.del-sum {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
}
.del-ok {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--slate-900);
  font-weight: 600;
}
.del-bad {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--crit);
  font-weight: 600;
}
.del-reason {
  color: var(--ink-2);
}
.del-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.65;
}
</style>
