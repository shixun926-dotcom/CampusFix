<template>
  <div class="profile">
    <div class="page-hd">
      <div>
        <span class="eyebrow">Personal Center</span>
        <h1>个人中心</h1>
        <div class="sub">姓名与工号由管理员统一维护，手机号与头像可自行修改</div>
      </div>
    </div>

    <div v-if="loading" class="empty">
      <span class="spin" />
      <div class="d">加载中…</div>
    </div>

    <template v-else>
      <!-- 头像提醒：逾期未更换 → 强制提示；未更换但未逾期 → 温和提示 -->
      <div v-if="avatarOverdue" class="notice avatar-notice overdue">
        <span class="g">!</span>
        <div>
          你的头像已超过更换期限，为保证学生能识别你的身份，请立即上传真实头像。
          <a class="notice-link" @click="onPickAvatar">立即更换</a>
        </div>
      </div>
      <div v-else-if="!hasAvatar" class="notice avatar-notice">
        <span class="g">i</span>
        <div>
          你正在使用默认头像，建议尽快上传个人真实头像，便于学生识别你的身份。
          <a class="notice-link" @click="onPickAvatar">立即更换</a>
        </div>
      </div>

      <!-- ① 身份与头像区 -->
      <div class="panel id-card">
        <div class="id-left">
          <div class="avatar-box" :class="{ empty: !hasAvatar && !pendingAvatarUrl }">
            <img v-if="previewUrl" :src="previewUrl" alt="头像" />
            <span v-else class="default-avatar">{{ initial }}</span>
          </div>
          <div class="id-main">
            <div class="id-name">{{ profile?.realName || '—' }}</div>
            <div class="id-meta">
              <span class="tag tag-ink">工号 {{ profile?.staffNo || '—' }}</span>
              <span class="tag tag-brand">{{ profile?.deptName || '未分组' }}</span>
            </div>
          </div>
        </div>
        <div class="id-right">
          <button class="btn" type="button" @click="onPickAvatar">
            <Icon name="refresh" :size="15" />更换头像
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png"
            class="hidden-input"
            @change="onAvatarChange"
          />
          <div class="avatar-hint">JPG / PNG，200×200 以上，≤ 2MB</div>
        </div>
      </div>

      <!-- ② 基本信息区：姓名/工号/区域只读，仅手机号可编辑 -->
      <div class="panel form-card">
        <div class="panel-hd">
          <h3>基本信息</h3>
          <span class="sb">姓名、工号、所属区域由管理员维护，需更改请走下方「信息更改申请」</span>
        </div>
        <div class="panel-bd">
          <div class="form-grid">
            <div class="fg">
              <label class="lbl">姓名</label>
              <input class="field readonly" type="text" :value="profile?.realName || '—'" disabled />
              <div class="f-hint">由管理员导入生成，不可修改</div>
            </div>
            <div class="fg">
              <label class="lbl">工号</label>
              <input class="field readonly" type="text" :value="profile?.staffNo || '—'" disabled />
              <div class="f-hint">由管理员导入生成，不可修改</div>
            </div>
            <div class="fg">
              <label class="lbl">所属区域</label>
              <input class="field readonly" type="text" :value="profile?.deptName || '—'" disabled />
              <div class="f-hint">由管理员维护，不可修改</div>
            </div>
            <div class="fg">
              <label class="lbl">手机号 <span class="req">*</span></label>
              <input
                v-model="form.phone"
                class="field"
                :class="{ 'field-err': errors.phone }"
                type="text"
                maxlength="11"
                placeholder="请输入 11 位手机号"
                @blur="validatePhone"
              />
              <div v-if="errors.phone" class="f-err">{{ errors.phone }}</div>
            </div>
          </div>

          <div class="form-act">
            <button class="btn" type="button" :disabled="saving" @click="onReset">重置</button>
            <button class="btn btn-primary" type="button" :disabled="saving" @click="onSave">
              <span v-if="saving" class="spin sm" />保存
            </button>
          </div>
        </div>
      </div>

      <!-- ③ 信息更改申请区 -->
      <div class="panel change-card">
        <div class="panel-hd">
          <h3>信息更改申请</h3>
          <span class="sb">若姓名 / 工号 / 所属区域有误，请提交申请，经后勤管理员审核后生效</span>
        </div>
        <div class="panel-bd">
          <div class="change-form">
            <div class="fg">
              <label class="lbl">更改项目 <span class="req">*</span></label>
              <select v-model="changeForm.fieldKey" class="field">
                <option value="" disabled>请选择要更改的信息</option>
                <option value="real_name">姓名</option>
                <option value="staff_no">工号</option>
                <option value="dept">所属区域</option>
              </select>
            </div>
            <div class="fg">
              <label class="lbl">新内容 <span class="req">*</span></label>
              <input
                v-model="changeForm.newValue"
                class="field"
                :class="{ 'field-err': changeErrors.newValue }"
                type="text"
                maxlength="100"
                :placeholder="changePlaceholder"
              />
              <div v-if="changeErrors.newValue" class="f-err">{{ changeErrors.newValue }}</div>
            </div>
            <div class="fg full">
              <label class="lbl">申请理由 <span class="req">*</span></label>
              <textarea
                v-model="changeForm.reason"
                class="field"
                rows="3"
                maxlength="500"
                placeholder="请说明信息为何有误（如导入时录入错误）"
              />
              <div v-if="changeErrors.reason" class="f-err">{{ changeErrors.reason }}</div>
            </div>
            <div class="form-act">
              <button class="btn btn-primary" type="button" :disabled="submitting" @click="onSubmitChange">
                <span v-if="submitting" class="spin sm" />提交申请
              </button>
            </div>
          </div>

          <!-- 我的申请记录 -->
          <div class="change-list">
            <div class="cl-hd">我的申请记录</div>
            <div v-if="!changes.length" class="empty sm">
              <div class="d">暂无申请记录</div>
            </div>
            <div v-for="c in changes" :key="c.id" class="change-item">
              <div class="ci-main">
                <span class="ci-field">{{ c.fieldLabel }}</span>
                <span class="ci-val">{{ c.oldValue || '—' }} → {{ c.newValue }}</span>
              </div>
              <div class="ci-meta">
                <span class="tag" :class="statusClass(c.status)">{{ c.statusLabel }}</span>
                <span class="ci-time">{{ c.createdAt?.slice(0, 16) }}</span>
                <span v-if="c.rejectReason" class="ci-reject">原因：{{ c.rejectReason }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ④ 对外可见信息说明区 -->
      <div class="panel preview-card">
        <div class="panel-hd">
          <h3>学生端将看到的你的信息</h3>
          <span class="sb">接单后对学生可见</span>
        </div>
        <div class="panel-bd">
          <div class="preview-note">
            当你在学生宿舍进行维修时，学生端工单详情将展示以下信息，请确保准确无误。
          </div>
          <div class="preview-item">
            <div class="pv-avatar">
              <img v-if="previewUrl" :src="previewUrl" alt="头像" />
              <span v-else class="default-avatar">{{ initial }}</span>
            </div>
            <div class="pv-main">
              <div class="pv-name">{{ profile?.realName || '—' }}</div>
              <div class="pv-meta">
                <span>工号 {{ profile?.staffNo || '—' }}</span>
                <span>·</span>
                <span>{{ profile?.deptName || '未分组' }}</span>
              </div>
              <div class="pv-phone">联系电话 {{ form.phone || '—' }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import { toastErr, toastOk } from '../ui/toast'
import {
  getMyProfile,
  myProfileChanges,
  submitProfileChange,
  updateMyProfile,
  uploadAvatar,
  type ProfileChangeVO,
  type ProfileVO
} from '../api/profile'
import { resolveAssetUrl } from '../config/env'

const profile = ref<ProfileVO | null>(null)
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const changes = ref<ProfileChangeVO[]>([])

/** 编辑表单：仅手机号可编辑；头像走独立上传，保存时一并提交 objectKey */
const form = reactive({
  phone: '',
  avatar: '' // 待提交的头像 objectKey（空 = 不更换）
})

/** 信息更改申请表单 */
const changeForm = reactive({
  fieldKey: '',
  newValue: '',
  reason: ''
})

/** 已更换头像（含已保存 + 待保存的新头像） */
const hasAvatar = computed(() => Boolean(pendingAvatarUrl.value || profile.value?.hasAvatar))
const avatarOverdue = computed(() => Boolean(profile.value?.avatarOverdue))

const pendingAvatarUrl = ref('')

/** 默认头像：品牌色底 + 姓氏首字 */
const initial = computed(() => (profile.value?.realName || 'U').charAt(0))

/** 预览：优先展示「尚未保存的新头像」，否则回落到已保存头像；都无则显示默认头像 */
const previewUrl = computed(() => {
  if (pendingAvatarUrl.value) return pendingAvatarUrl.value
  return resolveAssetUrl(profile.value?.avatarUrl)
})

const changePlaceholder = computed(() => {
  switch (changeForm.fieldKey) {
    case 'real_name':
      return '请输入正确的姓名'
    case 'staff_no':
      return '请输入正确的工号'
    case 'dept':
      return '请输入正确的所属区域名称'
    default:
      return '请先选择要更改的信息'
  }
})

const errors = reactive({ phone: '' })
const changeErrors = reactive({ newValue: '', reason: '' })

onMounted(async () => {
  try {
    profile.value = await getMyProfile()
    form.phone = profile.value.phone || ''
    form.avatar = profile.value.avatar || ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
  loadChanges()
})

async function loadChanges() {
  try {
    const page = await myProfileChanges(1, 20)
    changes.value = page?.list || []
  } catch {
    /* 静默 */
  }
}

/* ---------------- 手机号校验 ---------------- */

function validatePhone(): boolean {
  const v = form.phone.trim()
  if (!v) {
    errors.phone = '手机号不能为空'
    return false
  }
  if (!/^1[3-9]\d{9}$/.test(v)) {
    errors.phone = '请输入正确的 11 位手机号'
    return false
  }
  errors.phone = ''
  return true
}

/* ---------------- 头像 ---------------- */

function onPickAvatar() {
  fileInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''

  if (!/\.(jpe?g|png)$/i.test(file.name) && !/^image\/(jpeg|png)$/.test(file.type)) {
    toastErr('仅支持 JPG / PNG 格式的头像')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    toastErr('头像不能超过 2MB')
    return
  }
  const dim = await readImageSize(file)
  if (dim && (dim.w < 200 || dim.h < 200)) {
    toastErr('头像需为 200×200 以上的图片')
    return
  }

  try {
    const res = await uploadAvatar(file)
    form.avatar = res.objectKey
    pendingAvatarUrl.value = resolveAssetUrl(res.url)
    toastOk('头像已更新，点击保存后生效')
  } catch {
    /* 拦截器已提示 */
  }
}

function readImageSize(file: File): Promise<{ w: number; h: number } | null> {
  return new Promise((resolve) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve({ w: img.naturalWidth, h: img.naturalHeight })
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    img.src = url
  })
}

/* ---------------- 保存 / 重置 ---------------- */

async function onSave() {
  if (!validatePhone()) return

  saving.value = true
  try {
    const updated = await updateMyProfile({
      phone: form.phone.trim(),
      ...(form.avatar ? { avatar: form.avatar } : {})
    })
    profile.value = updated
    pendingAvatarUrl.value = ''
    form.avatar = updated.avatar || ''
    toastOk('保存成功')
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function onReset() {
  if (profile.value) {
    form.phone = profile.value.phone || ''
    form.avatar = profile.value.avatar || ''
  }
  pendingAvatarUrl.value = ''
  errors.phone = ''
}

/* ---------------- 信息更改申请 ---------------- */

async function onSubmitChange() {
  if (!changeForm.fieldKey) {
    toastErr('请选择要更改的信息')
    return
  }
  const newValue = changeForm.newValue.trim()
  if (!newValue) {
    changeErrors.newValue = '新内容不能为空'
    return
  }
  changeErrors.newValue = ''
  const reason = changeForm.reason.trim()
  if (!reason) {
    changeErrors.reason = '请填写申请理由'
    return
  }
  changeErrors.reason = ''

  submitting.value = true
  try {
    await submitProfileChange({
      fieldKey: changeForm.fieldKey,
      newValue,
      reason
    })
    toastOk('申请已提交，请等待后勤管理员审核')
    changeForm.newValue = ''
    changeForm.reason = ''
    changeForm.fieldKey = ''
    await loadChanges()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function statusClass(status: string): string {
  if (status === 'APPROVED') return 'tag-ok'
  if (status === 'REJECTED') return 'tag-crit'
  return 'tag-warn'
}
</script>

<style scoped>
.profile {
  max-width: 860px;
}
.hidden-input {
  display: none;
}
.spin.sm {
  width: 14px;
  height: 14px;
  border-width: 2px;
}
.req {
  color: var(--crit);
}

/* 提醒横幅 */
.avatar-notice {
  margin-bottom: 16px;
}
.avatar-notice.overdue {
  background: #fdf2f2;
  border-color: #f4c7ca;
  color: #a11d26;
}
.notice-link {
  color: inherit;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
  margin-left: 4px;
}
.notice-link:hover {
  opacity: 0.8;
}

/* ① 身份头像区 */
.id-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  margin-bottom: 16px;
  background:
    radial-gradient(360px 160px at 100% 0%, rgba(230, 57, 70, 0.08), transparent 70%),
    linear-gradient(135deg, #fff, #fdf6f7);
}
.id-left {
  display: flex;
  align-items: center;
  gap: 18px;
}
.avatar-box {
  width: 96px;
  height: 96px;
  border-radius: 24px;
  overflow: hidden;
  display: grid;
  place-items: center;
  position: relative;
  border: 2px solid #fff;
  box-shadow: var(--sh-2);
  flex: none;
}
.avatar-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-box.empty {
  border-style: dashed;
  border-color: #e6b6bb;
}
/* 默认头像：品牌色底 + 姓氏首字，绝对定位铺满父容器（避免 grid 中 height:100% 塌陷） */
.default-avatar {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #f0505c, var(--brand) 55%, #c42532);
  color: #fff;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.id-name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.3px;
}
.id-meta {
  display: flex;
  gap: 7px;
  margin-top: 9px;
}
.id-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.avatar-hint {
  font-size: 11.5px;
  color: var(--ink-3);
}

/* ② 表单 */
.form-card {
  margin-bottom: 16px;
}
.fg {
  display: flex;
  flex-direction: column;
}
.f-err {
  color: var(--crit);
  font-size: 12px;
  margin-top: 5px;
}
.f-hint {
  color: var(--ink-3);
  font-size: 11.5px;
  margin-top: 5px;
}
.field-err {
  border-color: var(--crit);
}
.field-err:focus {
  box-shadow: 0 0 0 4px rgba(220, 53, 69, 0.1);
}
.readonly {
  background: var(--line-2);
  color: var(--ink-3);
  cursor: not-allowed;
}
.form-act {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--line-2);
}

/* ③ 信息更改申请 */
.change-card {
  margin-bottom: 16px;
}
.change-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.change-form .full {
  grid-column: 1 / -1;
}
.change-form .form-act {
  grid-column: 1 / -1;
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}
.change-list {
  margin-top: 22px;
  border-top: 1px solid var(--line-2);
  padding-top: 16px;
}
.cl-hd {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
  margin-bottom: 10px;
}
.empty.sm {
  padding: 20px 0;
}
.change-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
  border: 1px solid var(--line-2);
  border-radius: 10px;
  margin-bottom: 8px;
  background: #fcfcfd;
}
.ci-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ci-field {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  flex: none;
}
.ci-val {
  font-size: 13px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ci-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.ci-time {
  font-size: 12px;
  color: var(--ink-3);
}
.ci-reject {
  font-size: 12px;
  color: var(--crit);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ④ 预览区 */
.preview-card .panel-bd {
  padding: 20px 22px;
}
.preview-note {
  font-size: 12.5px;
  color: var(--ink-2);
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 16px;
}
.preview-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--line-2);
  border-radius: 14px;
  background: #fcfcfd;
}
.pv-avatar {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  overflow: hidden;
  display: grid;
  place-items: center;
  position: relative;
  flex: none;
}
.pv-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.pv-avatar .default-avatar {
  font-size: 28px;
  border-radius: 0;
}
.pv-main {
  min-width: 0;
}
.pv-name {
  font-size: 16px;
  font-weight: 700;
}
.pv-meta {
  display: flex;
  gap: 7px;
  font-size: 12.5px;
  color: var(--ink-3);
  margin-top: 4px;
}
.pv-phone {
  font-size: 13px;
  color: var(--ink-2);
  margin-top: 6px;
}

@media (max-width: 720px) {
  .id-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .id-right {
    align-items: flex-start;
  }
  .change-form {
    grid-template-columns: 1fr;
  }
  .change-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
