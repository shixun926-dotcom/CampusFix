<template>
  <div class="sec">
    <div class="sec-t">
      维修过程
      <span v-if="record?.durationMinutes != null" class="sec-hint">
        用时 {{ record.durationMinutes }} 分钟
      </span>
    </div>

    <div class="stamps">
      <div class="stamp" :class="{ on: record?.arriveAt }">
        <span class="stamp-dot" />
        <div>
          <div class="stamp-l">到达现场</div>
          <div class="stamp-v">{{ short(record?.arriveAt) }}</div>
        </div>
      </div>
      <div class="stamp" :class="{ on: record?.startAt }">
        <span class="stamp-dot" />
        <div>
          <div class="stamp-l">开始维修</div>
          <div class="stamp-v">{{ short(record?.startAt) }}</div>
        </div>
      </div>
      <div class="stamp" :class="{ on: record?.endAt }">
        <span class="stamp-dot" />
        <div>
          <div class="stamp-l">维修结束</div>
          <div class="stamp-v">{{ short(record?.endAt) }}</div>
        </div>
      </div>
    </div>

    <!-- 只读内容（非操作人 或 已提交后） -->
    <div v-if="record?.content || record?.result" class="record-view">
      <div v-if="record?.content" class="rv-row"><b>维修内容</b><span>{{ record.content }}</span></div>
      <div v-if="record?.material" class="rv-row"><b>使用材料</b><span>{{ record.material }}</span></div>
      <div v-if="record?.result" class="rv-row"><b>维修结果</b><span>{{ record.result }}</span></div>
      <div v-if="record?.remark" class="rv-row"><b>备注</b><span>{{ record.remark }}</span></div>
    </div>

    <!-- 维修工操作区 -->
    <template v-if="canOperate">
      <div class="punch-row">
        <button v-if="!record?.arriveAt" class="btn btn-sm" :disabled="busy" @click="onArrive">
          <Icon name="pin" :size="14" />记录到达
        </button>
        <button
          v-if="record?.arriveAt && !record?.startAt"
          class="btn btn-sm"
          :disabled="busy"
          @click="onStart"
        >
          <Icon name="wrench" :size="14" />开始维修
        </button>
        <span class="spacer" />
        <button class="btn btn-sm btn-ghost" :disabled="busy" @click="onSuspend">挂起</button>
        <button class="btn btn-sm btn-ghost" :disabled="busy" @click="onUnrepairable">无法维修</button>
        <span v-if="!record?.arriveAt" class="punch-hint">到场后先打点，用时统计以此为准</span>
      </div>

      <div v-if="!formOpen" class="slot-ops">
        <button class="btn btn-sm" @click="openForm">
          <Icon name="file" :size="14" />填写维修记录
        </button>
        <button class="btn btn-sm btn-primary" @click="openForm(true)">
          <Icon name="check" :size="14" />提交维修结果
        </button>
      </div>

      <div v-if="formOpen" class="rec-form">
        <label class="lbl">维修内容 <em>*</em></label>
        <textarea v-model="form.content" class="field" rows="3" placeholder="排查过程、故障原因、处理方式" />

        <div class="two">
          <div>
            <label class="lbl">使用材料</label>
            <input v-model="form.material" class="field" placeholder="如：滤网清洗剂 1 瓶" />
          </div>
          <div>
            <label class="lbl">维修结果 {{ submitting ? '*' : '' }}</label>
            <input v-model="form.result" class="field" placeholder="如：制冷恢复正常" />
          </div>
        </div>

        <label class="lbl">
          {{ submitting ? '维修后照片' : '维修前照片（选填）' }}
        </label>
        <div class="pics">
          <div v-for="(p, i) in pics" :key="p.objectKey" class="pic">
            <img :src="asset(p.url)" alt="" />
            <button type="button" class="del" @click="pics.splice(i, 1)">✕</button>
          </div>
          <label v-if="pics.length < 9" class="pic-add">
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onPick" />
            <span v-if="!uploading">＋</span>
            <span v-else class="spin" style="width: 14px; height: 14px" />
          </label>
        </div>
        <div class="hint-line">整单（含报修照片）最多 9 张</div>

        <div v-if="formError" class="form-err">{{ formError }}</div>

        <div class="rec-act">
          <button class="btn btn-sm" @click="formOpen = false">取消</button>
          <span class="spacer" />
          <button v-if="!submitting" class="btn btn-sm" :disabled="busy" @click="onSave">保存记录</button>
          <button class="btn btn-sm btn-primary" :disabled="busy" @click="onComplete">
            {{ submitting ? '确认提交' : '提交结果' }}
          </button>
        </div>
      </div>
    </template>

    <!-- 挂起中：维修人可恢复 / 判定无法维修 -->
    <template v-else-if="canResume">
      <div class="suspend-tip">工单已挂起（挂起时长不计入维修用时），配件到位或可继续维修时恢复。</div>
      <div class="punch-row">
        <span class="spacer" />
        <button class="btn btn-sm btn-ghost" :disabled="busy" @click="onUnrepairable">无法维修</button>
        <button class="btn btn-sm btn-primary" :disabled="busy" @click="onResume">
          <Icon name="play" :size="14" />恢复维修
        </button>
      </div>
    </template>

    <div v-else-if="!record" class="empty-line">维修人员到场后会在这里记录过程与结果。</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Icon from '../ui/Icon.vue'
import { resolveAssetUrl } from '../config/env'
import { toastErr, toastOk } from '../ui/toast'
import { useAuthStore } from '../stores/auth'
import { confirmDialog } from '../ui/dialog'
import {
  arriveOrder,
  completeOrder,
  getRecord,
  resumeOrder,
  saveRecord,
  startOrder,
  suspendOrder,
  unrepairableOrder,
  uploadImage,
  type OrderVO,
  type RecordVO
} from '../api/order'

const asset = (u?: string | null) => resolveAssetUrl(u)

const props = defineProps<{ order: OrderVO; orderId: number }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const record = ref<RecordVO | null>(null)
const busy = ref(false)
const formOpen = ref(false)
const submitting = ref(false)
const formError = ref('')
const uploading = ref(false)
const pics = ref<{ objectKey: string; url: string }[]>([])

const form = reactive({ content: '', material: '', result: '', remark: '' })

const meId = computed(() => Number(auth.user?.id || 0))
const canOperate = computed(
  () => meId.value > 0 && props.order.assigneeId === meId.value && props.order.status === 'PROCESSING'
)
/** 挂起中：当前维修人可恢复 / 判定无法维修 */
const canResume = computed(
  () => meId.value > 0 && props.order.assigneeId === meId.value && props.order.status === 'SUSPENDED'
)

async function load() {
  try {
    const data: any = await getRecord(props.orderId)
    record.value = data || null
  } catch {
    record.value = null
  }
}

function short(v?: string) {
  if (!v) return '—'
  // "2026-10-30 23:41:43" → "23:41"
  return v.length >= 16 ? v.slice(11, 16) : v
}

function openForm(asSubmit = false) {
  formOpen.value = true
  submitting.value = asSubmit
  formError.value = ''
  form.content = record.value?.content || ''
  form.material = record.value?.material || ''
  form.result = record.value?.result || ''
  form.remark = record.value?.remark || ''
  pics.value = []
}

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (files.length === 0) return
  uploading.value = true
  try {
    for (const f of files) {
      const data: any = await uploadImage(f, submitting.value ? 'AFTER' : 'BEFORE')
      pics.value.push({ objectKey: data.objectKey, url: data.url })
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    uploading.value = false
  }
}

async function onArrive() {
  busy.value = true
  try {
    await arriveOrder(props.orderId)
    toastOk('已记录到达时间')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '打点失败')
  } finally {
    busy.value = false
  }
}

async function onStart() {
  busy.value = true
  try {
    await startOrder(props.orderId)
    toastOk('已开始计时')
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '打点失败')
  } finally {
    busy.value = false
  }
}

async function onSuspend() {
  const reason = window.prompt('请填写挂起原因（如：缺配件、报修人不在，会记录在流转日志并通知报修人）')
  if (reason === null) return
  if (!reason.trim()) {
    toastErr('挂起原因不能为空')
    return
  }
  busy.value = true
  try {
    await suspendOrder(props.orderId, reason.trim())
    toastOk('工单已挂起')
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '挂起失败')
  } finally {
    busy.value = false
  }
}

async function onResume() {
  busy.value = true
  try {
    await resumeOrder(props.orderId)
    toastOk('工单已恢复维修')
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '恢复失败')
  } finally {
    busy.value = false
  }
}

async function onUnrepairable() {
  const reason = window.prompt('请填写无法维修的原因（工单将转「已驳回」，会通知报修人）')
  if (reason === null) return
  if (!reason.trim()) {
    toastErr('原因不能为空')
    return
  }
  const ok = await confirmDialog({
    title: '判定无法维修',
    message: '工单将转为「已驳回」并通知报修人，报修人可发起投诉。确定继续？',
    okText: '确认判定',
    danger: true
  })
  if (!ok) return
  busy.value = true
  try {
    await unrepairableOrder(props.orderId, reason.trim())
    toastOk('已判定无法维修，工单转已驳回')
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

function payload(stage: string) {
  return {
    content: form.content,
    material: form.material,
    result: form.result,
    remark: form.remark,
    images: pics.value.map((p, i) => ({ objectKey: p.objectKey, stage, sort: i }))
  }
}

async function onSave() {
  if (!form.content.trim()) {
    formError.value = '请先填写维修内容'
    return
  }
  busy.value = true
  try {
    await saveRecord(props.orderId, payload('BEFORE'))
    toastOk('维修记录已保存')
    pics.value = []
    await load()
  } catch (e: any) {
    formError.value = e?.message || '保存失败'
  } finally {
    busy.value = false
  }
}

async function onComplete() {
  formError.value = ''
  if (!form.content.trim()) {
    formError.value = '请填写维修内容'
    return
  }
  if (!form.result.trim()) {
    formError.value = '请填写维修结果'
    return
  }
  if (!submitting.value) {
    // 第一次点击只是进入「确认提交」模式，避免误操作直接推进状态
    submitting.value = true
    pics.value = []
    return
  }
  busy.value = true
  try {
    await completeOrder(props.orderId, payload('AFTER'))
    toastOk('已提交，等待报修人确认')
    formOpen.value = false
    submitting.value = false
    await load()
    emit('changed')
  } catch (e: any) {
    formError.value = e?.message || '提交失败'
  } finally {
    busy.value = false
  }
}

onMounted(load)
watch(() => props.orderId, load)
</script>

<style scoped>
.sec-hint {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--brand);
}
.stamps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.stamp {
  display: flex;
  gap: 8px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 9px 11px;
  background: #fcfcfd;
}
.stamp.on {
  border-color: #bfe6cd;
  background: #f6fdf9;
}
.stamp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d9dce2;
  flex: none;
}
.stamp.on .stamp-dot {
  background: #12a150;
  box-shadow: 0 0 0 3px rgba(18, 161, 80, 0.14);
}
.stamp-l {
  font-size: 11.5px;
  color: var(--ink-3);
}
.stamp-v {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.record-view {
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rv-row {
  display: flex;
  gap: 10px;
  font-size: 12.5px;
  line-height: 1.6;
}
.rv-row b {
  flex: none;
  width: 64px;
  color: var(--ink-3);
  font-weight: 400;
}
.punch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
.punch-row .spacer {
  flex: 1;
}
.suspend-tip {
  margin-top: 12px;
  font-size: 12.5px;
  line-height: 1.6;
  color: #9a3412;
  background: #fef7ec;
  border: 1px solid #fde3c8;
  border-radius: 10px;
  padding: 10px 13px;
}
.punch-hint {
  font-size: 12px;
  color: var(--ink-3);
}
.slot-ops {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.rec-form {
  margin-top: 12px;
  border: 1px dashed #cfd3da;
  border-radius: 10px;
  padding: 12px;
  background: #fafbfc;
}
.rec-form .lbl {
  margin-top: 8px;
}
.rec-form .lbl:first-child {
  margin-top: 0;
}
.lbl em {
  color: var(--brand);
  font-style: normal;
}
textarea.field {
  resize: vertical;
  height: auto;
  padding: 9px 11px;
  font-family: inherit;
  line-height: 1.6;
}
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.pics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pic {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--line);
}
.pic img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.pic .del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 9px;
  display: grid;
  place-items: center;
  cursor: pointer;
}
.pic-add {
  width: 72px;
  height: 72px;
  border: 1px dashed #cfd3da;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 20px;
  color: var(--ink-3);
  cursor: pointer;
}
.pic-add:hover {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-l2);
}
.hint-line {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 6px;
}
.rec-act {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}
.spacer {
  flex: 1;
}
.empty-line {
  font-size: 12.5px;
  color: var(--ink-3);
  background: var(--bg);
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: 12px;
}
</style>
