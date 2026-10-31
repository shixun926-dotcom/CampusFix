<template>
  <div v-if="visible" class="sec">
    <div class="sec-t">
      评价与投诉
      <span v-if="order.evaluation" class="sec-hint">{{ order.evaluation.totalScore }} 分</span>
      <span v-else-if="order.complaintCount" class="sec-hint">{{ order.complaintCount }} 条投诉</span>
    </div>

    <!-- ============ 已评价 ============ -->
    <div v-if="order.evaluation" class="eval-done">
      <div class="ed-hd">
        <span class="ed-label">综合评分</span>
        <span class="ed-score">{{ order.evaluation.totalScore }}</span>
        <span class="ed-unit">/ 5</span>
        <span v-if="order.evaluation.anonymous" class="tag tag-ink" style="margin-left: auto">匿名</span>
      </div>

      <div class="ed-dims">
        <div v-for="d in doneDims" :key="d.label" class="ed-dim">
          <span class="ed-dim-l">{{ d.label }}</span>
          <span class="ed-stars">
            <i v-for="i in 5" :key="i" class="star" :class="{ on: i <= d.value }">★</i>
          </span>
          <span class="ed-dim-v">{{ d.value }}</span>
        </div>
      </div>

      <div v-if="order.evaluation.content" class="ed-content">{{ order.evaluation.content }}</div>
      <div class="ed-meta">
        {{ order.evaluation.studentName || '—' }} · {{ order.evaluation.createdAt }}
      </div>
    </div>

    <!-- ============ 待评价 ============ -->
    <div v-else-if="order.canEvaluate" class="eval-form">
      <div class="ef-tip">
        <Icon name="shield" :size="14" />
        维修已完成，请对本次服务打分。提交后不可修改。
      </div>

      <div v-for="d in formDims" :key="d.key" class="star-row">
        <span class="star-label">{{ d.label }}</span>
        <span class="ed-stars">
          <i
            v-for="i in 5"
            :key="i"
            class="star pick"
            :class="{ on: i <= (form as any)[d.key] }"
            @click="(form as any)[d.key] = i"
          >
            ★
          </i>
        </span>
        <span class="star-val">{{ (form as any)[d.key] ? (form as any)[d.key] + ' 分' : '未评分' }}</span>
      </div>

      <textarea
        v-model="form.content"
        class="field"
        rows="3"
        maxlength="500"
        placeholder="补充说明（选填，最多 500 字）"
      />

      <label class="anon">
        <input v-model="form.anonymous" type="checkbox" />
        <span>匿名评价（维修人员看不到你的姓名）</span>
      </label>

      <div v-if="formError" class="form-err">{{ formError }}</div>

      <div class="ef-act">
        <button class="btn btn-sm btn-primary" :disabled="busy" @click="onSubmit">
          {{ busy ? '提交中…' : '提交评价' }}
        </button>
      </div>
    </div>

    <!-- ============ 投诉记录 ============ -->
    <div v-if="complaints.length" class="cmp-list">
      <div v-for="c in complaints" :key="c.id" class="cmp-item">
        <div class="ci-hd">
          <span class="tag" :class="cmpTagClass(c.status)">{{ c.statusLabel }}</span>
          <span class="ci-type">{{ c.typeLabel }}</span>
          <span class="ci-time">{{ c.createdAt }}</span>
        </div>
        <div class="ci-content">{{ c.content }}</div>
        <div v-if="c.handleResult" class="ci-result">
          <b>{{ c.handlerName || '管理员' }}</b>：{{ c.handleResult }}
        </div>
      </div>
    </div>

    <!-- ============ 提交投诉 ============ -->
    <div v-if="canComplain" class="cmp-act">
      <button v-if="!cmpOpen" class="btn btn-sm btn-ghost" @click="cmpOpen = true">
        <Icon name="alert" :size="14" />对本次服务不满意？发起投诉
      </button>

      <div v-else class="cmp-form">
        <div class="star-row">
          <span class="star-label">投诉类型</span>
          <select v-model="cmp.type" class="field" style="width: 160px">
            <option v-for="t in COMPLAINT_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>

        <textarea
          v-model="cmp.content"
          class="field"
          rows="3"
          maxlength="1000"
          placeholder="请描述具体情况，如：预约 9 点却 10 点半才到，且未提前告知"
        />

        <div v-if="cmpError" class="form-err">{{ cmpError }}</div>

        <div class="ef-act">
          <button class="btn btn-sm" @click="cmpOpen = false">取消</button>
          <button class="btn btn-sm btn-danger" :disabled="cmpBusy" @click="onComplain">
            {{ cmpBusy ? '提交中…' : '提交投诉' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Icon from '../ui/Icon.vue'
import { toastErr, toastOk } from '../ui/toast'
import { evaluateOrder, listOrderComplaints, submitComplaint, type ComplaintVO } from '../api/order'

const props = defineProps<{ order: any }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

/** 与后端 ComplaintType 保持一致 */
const COMPLAINT_TYPES = [
  { value: 'ATTITUDE', label: '服务态度' },
  { value: 'SLOW', label: '响应拖沓' },
  { value: 'QUALITY', label: '维修质量' },
  { value: 'OTHER', label: '其他问题' }
]

const form = reactive({
  speedScore: 5,
  attitudeScore: 5,
  effectScore: 5,
  content: '',
  anonymous: false
})

const formError = ref('')
const busy = ref(false)

const cmpOpen = ref(false)
const cmpBusy = ref(false)
const cmpError = ref('')
const cmp = reactive({ type: 'ATTITUDE', content: '' })

const complaints = ref<ComplaintVO[]>([])

const formDims = [
  { key: 'speedScore', label: '维修速度' },
  { key: 'attitudeScore', label: '服务态度' },
  { key: 'effectScore', label: '维修效果' }
] as const

const doneDims = computed(() => {
  const e = props.order.evaluation
  if (!e) return []
  return [
    { label: '维修速度', value: e.speedScore },
    { label: '服务态度', value: e.attitudeScore },
    { label: '维修效果', value: e.effectScore }
  ]
})

/** 投诉入口：待确认 / 已完成，且没有未终结的投诉 */
const canComplain = computed(
  () =>
    props.order &&
    ['PENDING_CONFIRM', 'COMPLETED'].includes(props.order.status) &&
    !props.order.hasOpenComplaint
)

/**
 * 只在「已完成」「待确认」或有历史投诉时出现。
 * 处理中 / 待审核这类阶段既不评价也不投诉，显示空面板没有意义。
 */
const visible = computed(
  () =>
    !!props.order &&
    (!!props.order.evaluation ||
      !!props.order.canEvaluate ||
      canComplain.value ||
      complaints.value.length > 0)
)

async function loadComplaints() {
  if (!props.order?.id) return
  // 只有相关方才看得到投诉记录，失败时静默（比如维修工无关的单）
  try {
    complaints.value = (await listOrderComplaints(props.order.id)) || []
  } catch {
    complaints.value = []
  }
}

async function onSubmit() {
  formError.value = ''
  if (!form.speedScore || !form.attitudeScore || !form.effectScore) {
    formError.value = '请完成三项评分'
    return
  }
  busy.value = true
  try {
    const res: any = await evaluateOrder(props.order.id, {
      speedScore: form.speedScore,
      attitudeScore: form.attitudeScore,
      effectScore: form.effectScore,
      content: form.content.trim() || undefined,
      anonymous: form.anonymous
    })
    toastOk(`评价已提交，综合 ${res.totalScore} 分`)
    emit('changed')
  } catch (e: any) {
    formError.value = e?.message || '提交失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

async function onComplain() {
  cmpError.value = ''
  if (!cmp.content.trim()) {
    cmpError.value = '请填写投诉内容'
    return
  }
  cmpBusy.value = true
  try {
    await submitComplaint(props.order.id, { type: cmp.type, content: cmp.content.trim() })
    toastOk('投诉已提交，等待后勤处理')
    cmpOpen.value = false
    cmp.content = ''
    await loadComplaints()
    emit('changed')
  } catch (e: any) {
    cmpError.value = e?.message || '提交失败，请稍后重试'
  } finally {
    cmpBusy.value = false
  }
}

function cmpTagClass(status: string) {
  if (status === 'RESOLVED') return 'tag-crit'
  if (status === 'REJECTED') return 'tag-ink'
  if (status === 'PROCESSING') return 'tag-brand'
  return 'tag-warn'
}

onMounted(loadComplaints)
watch(() => props.order?.id, loadComplaints)
</script>
