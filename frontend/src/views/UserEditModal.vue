<template>
  <Modal :open="open" title="编辑用户" width="560px" @close="onClose">
    <div v-if="loading" class="edit-loading">
      <span class="spin" />
      <span class="d">加载中…</span>
    </div>

    <template v-else>
      <!-- 只读信息 -->
      <div class="ro-box">
        <div class="ro-row">
          <span class="ro-k">登录名</span><span class="ro-v mono">{{ detail?.username || '—' }}</span>
          <span class="ro-k">学号 / 工号</span>
          <span class="ro-v mono">{{ detail?.studentNo || detail?.staffNo || '—' }}</span>
        </div>
        <div class="ro-row">
          <span class="ro-k">信誉分</span><span class="ro-v">{{ detail?.creditScore ?? '—' }}</span>
          <span class="ro-k">最近登录</span><span class="ro-v">{{ detail?.lastLoginAt || '从未登录' }}</span>
        </div>
        <p class="ro-tip">登录名、学号 / 工号与信誉分不可在此修改（学号工号走信息更改申请，信誉分走调整流水）。</p>
      </div>

      <!-- 可编辑信息 -->
      <div class="form-grid">
        <div class="full">
          <label class="lbl">姓名 <span class="req">*</span></label>
          <input v-model="form.realName" class="field" maxlength="50" placeholder="如 张建国" />
          <div v-if="errors.realName" class="f-err">{{ errors.realName }}</div>
        </div>

        <div class="full">
          <label class="lbl">手机号</label>
          <input v-model="form.phone" class="field" maxlength="11" placeholder="留空表示清除" />
          <div v-if="errors.phone" class="f-err">{{ errors.phone }}</div>
        </div>

        <div class="full">
          <label class="lbl">角色 <span class="req">*</span></label>
          <div class="role-chips">
            <button
              v-for="r in roles"
              :key="r.code"
              type="button"
              class="role-chip"
              :class="{ on: form.roleCodes.includes(r.code) }"
              @click="toggleRole(r.code)"
            >
              {{ r.name }}
            </button>
          </div>
          <div v-if="errors.roleCodes" class="f-err">{{ errors.roleCodes }}</div>
        </div>

        <div v-if="isStudent" class="full">
          <label class="lbl">班级 <span class="req">*</span></label>
          <input v-model="form.className" class="field" maxlength="50" placeholder="如 计算机 2026-1 班" />
          <div v-if="errors.className" class="f-err">{{ errors.className }}</div>
        </div>

        <div v-else class="full">
          <label class="lbl">所属部门 <span class="req">*</span></label>
          <select v-model="form.deptId" class="field">
            <option :value="0" disabled>请选择部门</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.label }}</option>
          </select>
          <div v-if="errors.deptId" class="f-err">{{ errors.deptId }}</div>
        </div>

        <div class="full">
          <label class="lbl">状态</label>
          <div class="status-row">
            <button
              type="button"
              class="status-btn"
              :class="{ on: form.status === 1 }"
              @click="form.status = 1"
            >
              启用
            </button>
            <button
              type="button"
              class="status-btn danger"
              :class="{ on: form.status === 0 }"
              @click="form.status = 0"
            >
              禁用
            </button>
            <span class="status-hint">禁用后该账号将立即退出登录</span>
          </div>
        </div>
      </div>

      <p v-if="errorText" class="form-err">{{ errorText }}</p>
    </template>

    <template #footer>
      <button class="btn" @click="onClose">取消</button>
      <button class="btn btn-primary" :disabled="loading || saving" @click="onSubmit">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Modal from '../ui/Modal.vue'
import { confirmDialog } from '../ui/dialog'
import { toastOk } from '../ui/toast'
import {
  getDeptTree,
  getUserDetail,
  listRoles,
  updateUserBasic,
  type UserBasicUpdatePayload,
  type UserVO
} from '../api/system'

const props = defineProps<{ open: boolean; userId: number | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved', user: UserVO): void }>()

interface RoleOption {
  code: string
  name: string
}
interface DeptOption {
  id: number
  label: string
}

const loading = ref(false)
const saving = ref(false)
const detail = ref<UserVO | null>(null)
const errorText = ref('')
const roles = ref<RoleOption[]>([])
const deptOptions = ref<DeptOption[]>([])

const form = reactive({
  realName: '',
  phone: '',
  roleCodes: [] as string[],
  className: '',
  deptId: 0,
  status: 1
})

const errors = reactive<Record<string, string>>({})

const isStudent = computed(() => form.roleCodes.includes('STUDENT'))

/** 打开弹窗且有 userId 时加载详情与字典。 */
watch(
  () => [props.open, props.userId] as const,
  ([open, id]) => {
    if (open && id) {
      load(id)
    }
  },
  { immediate: true }
)

async function load(id: number) {
  loading.value = true
  errorText.value = ''
  clearErrors()
  try {
    const [user, roleList, deptTree] = await Promise.all([
      getUserDetail(id),
      roles.value.length ? Promise.resolve(null) : listRoles(),
      deptOptions.value.length ? Promise.resolve(null) : getDeptTree()
    ])
    if (roleList) {
      roles.value = (roleList as any[])
        .filter((r) => r.status === 1)
        .map((r) => ({ code: r.code, name: r.name }))
    }
    if (deptTree) {
      deptOptions.value = flattenDepts(deptTree as any)
    }
    detail.value = user
    form.realName = user.realName || ''
    form.phone = user.phone || ''
    form.roleCodes = [...(user.roleCodes || [])]
    form.className = user.className || ''
    form.deptId = user.deptId ?? 0
    form.status = user.status ?? 1
  } catch (e: any) {
    errorText.value = e?.message || '加载用户信息失败'
  } finally {
    loading.value = false
  }
}

function flattenDepts(nodes: any[], depth = 0): DeptOption[] {
  const out: DeptOption[] = []
  for (const n of nodes || []) {
    const prefix = depth === 0 ? '' : '　'.repeat(depth)
    out.push({ id: n.id, label: `${prefix}${n.name}` })
    if (n.children?.length) {
      out.push(...flattenDepts(n.children, depth + 1))
    }
  }
  return out
}

function toggleRole(code: string) {
  const i = form.roleCodes.indexOf(code)
  if (i >= 0) {
    form.roleCodes.splice(i, 1)
  } else {
    form.roleCodes.push(code)
  }
  // 角色切换后，班级/部门的必填项随之变化，清除旧提示
  errors.roleCodes = ''
  errors.className = ''
  errors.deptId = ''
}

function clearErrors() {
  Object.keys(errors).forEach((k) => (errors[k] = ''))
}

function validate(): boolean {
  clearErrors()
  if (!form.realName.trim()) errors.realName = '请输入姓名'
  else if (!/^[\u4e00-\u9fa5A-Za-z·\s]{1,50}$/.test(form.realName.trim())) {
    errors.realName = '姓名仅支持中文、字母、空格与·'
  }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone.trim())) {
    errors.phone = '手机号格式不正确'
  }
  if (!form.roleCodes.length) errors.roleCodes = '至少保留一个角色'
  const staff = form.roleCodes.some((c) => ['REPAIRER', 'MANAGER', 'ADMIN'].includes(c))
  if (isStudent.value && staff) {
    errors.roleCodes = '学生与教职工角色不能同时选择'
  }
  if (isStudent.value && !form.className.trim()) errors.className = '学生必须填写班级'
  if (!isStudent.value && staff && !form.deptId) errors.deptId = '教职工必须选择所属部门'
  return Object.values(errors).every((v) => !v)
}

async function onSubmit() {
  if (saving.value || !props.userId || !detail.value) return
  if (!validate()) return

  const prev = detail.value
  const removingAdmin = prev.roleCodes?.includes('ADMIN') && !form.roleCodes.includes('ADMIN')
  const disabling = prev.status === 1 && form.status === 0

  if (disabling) {
    const ok = await confirmDialog({
      title: '禁用账号',
      message: `禁用后「${prev.realName}」将立即退出登录且无法继续使用系统，确定继续？`,
      okText: '禁用'
    })
    if (!ok) return
  } else if (removingAdmin) {
    const ok = await confirmDialog({
      title: '移除管理员角色',
      message: `移除后「${prev.realName}」将失去系统管理权限，确定继续？`,
      okText: '移除'
    })
    if (!ok) return
  }

  const payload: UserBasicUpdatePayload = {
    realName: form.realName.trim(),
    phone: form.phone.trim(),
    status: form.status,
    roleCodes: [...form.roleCodes]
  }
  if (isStudent.value) {
    payload.className = form.className.trim()
    payload.deptId = 0
  } else {
    payload.className = ''
    payload.deptId = Number(form.deptId)
  }

  saving.value = true
  errorText.value = ''
  try {
    const saved = await updateUserBasic(props.userId, payload)
    toastOk('已保存')
    emit('saved', saved)
    emit('close')
  } catch (e: any) {
    // 拦截器已统一 toast，此处仅做内联提示，避免重复弹窗
    errorText.value = e?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

function onClose() {
  if (saving.value) return
  emit('close')
}
</script>

<style scoped>
.edit-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 44px 0;
  color: var(--ink-3);
  font-size: 13px;
}
.ro-box {
  background: #f7f8fb;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
}
.ro-row {
  display: grid;
  grid-template-columns: 74px 1fr 84px 1fr;
  align-items: center;
  gap: 6px 8px;
  font-size: 12.5px;
}
.ro-row + .ro-row {
  margin-top: 8px;
}
.ro-k {
  color: var(--ink-3);
}
.ro-v {
  color: var(--ink);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ro-tip {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--ink-3);
  line-height: 1.6;
}
.req {
  color: var(--crit);
}
.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.role-chip {
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: 0.15s;
}
.role-chip:hover {
  border-color: #f6c9cd;
  background: var(--brand-l2);
}
.role-chip.on {
  border-color: var(--brand);
  background: var(--brand-l);
  color: var(--brand);
  font-weight: 600;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-btn {
  padding: 5px 16px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: 0.15s;
}
.status-btn.on {
  border-color: var(--ok);
  background: #eaf7f0;
  color: #12805c;
  font-weight: 600;
}
.status-btn.danger.on {
  border-color: var(--crit);
  background: #fdecee;
  color: var(--crit);
}
.status-hint {
  font-size: 11.5px;
  color: var(--ink-3);
}
.f-err {
  margin-top: 6px;
  font-size: 12px;
  color: var(--crit);
}
</style>
