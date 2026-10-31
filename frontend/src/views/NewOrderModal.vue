<template>
  <Modal :open="open" title="提交报修" width="620px" @close="onClose">
    <div class="form-err" v-if="error" style="margin: 0 0 12px">{{ error }}</div>

    <div class="form-grid">
      <div class="full">
        <label class="lbl">报修标题 <em>*</em></label>
        <input v-model="form.title" class="field" maxlength="100" placeholder="一句话说明问题，如「202 宿舍空调不制冷」" />
      </div>

      <div>
        <label class="lbl">故障类型 <em>*</em></label>
        <select v-model.number="form.categoryId" class="field">
          <option :value="0">请选择</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">
            {{ c.name }}{{ c.deptName ? '（' + c.deptName + '）' : '' }}
          </option>
        </select>
      </div>

      <div>
        <label class="lbl">紧急程度</label>
        <div class="urg">
          <button
            v-for="u in urgOptions"
            :key="u.value"
            type="button"
            class="urg-btn"
            :class="{ on: form.urgency === u.value }"
            @click="form.urgency = u.value"
          >
            <Icon :name="u.icon" :size="14" />{{ u.label }}
          </button>
        </div>
      </div>

      <div class="full">
        <label class="lbl">报修位置 <em>*</em>（校区 → 区域 → 楼栋 → 楼层 → 房间）</label>
        <div class="loc-row">
          <select
            v-for="(lv, i) in levelOptions"
            :key="i"
            class="field"
            :value="pathIds[i] ?? ''"
            @change="onLevelChange(i, $event)"
          >
            <option value="">请选择</option>
            <option v-for="n in lv" :key="n.id" :value="n.id">{{ n.name }}</option>
          </select>
        </div>
        <div v-if="locationPath" class="hint-line">已选：{{ locationPath }}</div>
      </div>

      <div class="full" v-if="facilities.length">
        <label class="lbl">关联设施（可选）</label>
        <select v-model.number="form.facilityId" class="field">
          <option :value="0">不关联（如墙面、路灯等无档案设施）</option>
          <option v-for="f in facilities" :key="f.id" :value="f.id">
            {{ f.name }}（{{ f.facilityNo }} · {{ f.typeName || '未分类' }})
          </option>
        </select>
        <div class="hint-line">关联后该设施会标记为「维修中」，验收完成自动恢复</div>
      </div>

      <div class="full">
        <label class="lbl">故障描述 <em>*</em>（不少于 10 字）</label>
        <textarea
          v-model="form.description"
          class="field"
          rows="4"
          maxlength="1000"
          placeholder="描述故障现象、发生时间、已尝试的处理方式，便于维修人员提前判断"
        />
        <div class="hint-line">{{ form.description.length }} / 1000</div>
      </div>

      <div class="full">
        <label class="lbl">现场照片 <em>*</em>（1–9 张）</label>
        <div class="pics">
          <div v-for="(p, i) in pics" :key="i" class="pic">
            <img :src="asset(p.url)" alt="" />
            <button type="button" class="del" @click="removePic(i)">✕</button>
          </div>
          <label v-if="pics.length < 9" class="pic-add" :class="{ busy: uploading }">
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onPick" />
            <span v-if="!uploading">＋</span>
            <span v-else class="spin" style="width: 16px; height: 16px" />
          </label>
        </div>
        <div class="hint-line">支持 jpg / png / webp，单张不超过 10MB</div>
      </div>

      <div class="full">
        <label class="privacy">
          <input v-model="form.privacyConfirmed" type="checkbox" />
          <span>
            该照片会被其他同学与维修人员看到，请注意保护隐私（已确认）
          </span>
        </label>
      </div>
    </div>

    <template #footer>
      <button class="btn" type="button" @click="onClose">取消</button>
      <button class="btn btn-primary" type="button" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交报修' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Modal from '../ui/Modal.vue'
import { resolveAssetUrl } from '../config/env'
import Icon from '../ui/Icon.vue'
import { toastOk } from '../ui/toast'
import {
  createOrder,
  getLocationTree,
  listCategories,
  uploadImage,
  type CategoryVO,
  type LocationNode
} from '../api/order'
import { listFacilities } from '../api/stats'

const asset = (u?: string | null) => resolveAssetUrl(u)

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'created', id: number): void }>()

const form = reactive({
  title: '',
  description: '',
  categoryId: 0,
  urgency: 1,
  facilityId: 0,
  privacyConfirmed: false
})

const categories = ref<CategoryVO[]>([])
const tree = ref<LocationNode[]>([])
const pathIds = ref<number[]>([])
const pics = ref<{ objectKey: string; url: string; fileName: string; fileSize: number }[]>([])
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')

const urgOptions = [
  { value: 1, label: '普通', icon: 'dot' },
  { value: 2, label: '紧急', icon: 'clock' },
  { value: 3, label: '非常紧急', icon: 'bolt' }
]

/** 逐级下拉：根据已选链路推导每一层的候选节点 */
const levelOptions = computed<LocationNode[][]>(() => {
  const levels: LocationNode[][] = []
  let nodes = tree.value
  for (let i = 0; i < 8; i++) {
    if (!nodes || nodes.length === 0) break
    levels.push(nodes)
    const picked = nodes.find((n) => n.id === pathIds.value[i])
    if (!picked || !picked.children || picked.children.length === 0) break
    nodes = picked.children
  }
  return levels
})

const locationPath = computed(() => {
  const names: string[] = []
  let nodes = tree.value
  for (const id of pathIds.value) {
    const picked = nodes.find((n) => n.id === id)
    if (!picked) break
    names.push(picked.name)
    nodes = picked.children || []
  }
  return names.join(' / ')
})

const selectedLocationId = computed(() => {
  const id = pathIds.value[pathIds.value.length - 1]
  return id || 0
})

/* 关联设施：位置选定后加载该位置下的设施档案（可选关联） */
const facilities = ref<any[]>([])

watch(selectedLocationId, async (locId) => {
  form.facilityId = 0
  facilities.value = []
  if (!locId) return
  try {
    facilities.value = ((await listFacilities(locId)) as any) || []
  } catch {
    facilities.value = []
  }
})

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    reset()
    if (categories.value.length === 0) {
      try {
        const [cats, locs]: any = await Promise.all([listCategories(), getLocationTree()])
        categories.value = cats || []
        tree.value = locs || []
      } catch {
        // 拦截器已提示
      }
    }
  }
)

function onLevelChange(index: number, e: Event) {
  const value = Number((e.target as HTMLSelectElement).value) || 0
  const next = pathIds.value.slice(0, index)
  if (value) next.push(value)
  pathIds.value = next
}

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (files.length === 0) return

  const room = 9 - pics.value.length
  if (files.length > room) {
    error.value = `最多上传 9 张，当前还能上传 ${room} 张`
    files.splice(room)
  }
  uploading.value = true
  try {
    for (const f of files) {
      const data: any = await uploadImage(f, 'REPORT')
      pics.value.push({
        objectKey: data.objectKey,
        url: data.url,
        fileName: data.fileName,
        fileSize: data.fileSize
      })
    }
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function removePic(index: number) {
  pics.value.splice(index, 1)
}

function reset() {
  form.title = ''
  form.description = ''
  form.categoryId = 0
  form.urgency = 1
  form.facilityId = 0
  form.privacyConfirmed = false
  pathIds.value = []
  facilities.value = []
  pics.value = []
  error.value = ''
}

function onClose() {
  if (submitting.value) return
  emit('close')
}

async function onSubmit() {
  error.value = ''
  if (form.title.trim().length < 4) {
    error.value = '标题至少 4 个字'
    return
  }
  if (!form.categoryId) {
    error.value = '请选择故障类型'
    return
  }
  if (!selectedLocationId.value) {
    error.value = '请选择到具体的房间或区域节点'
    return
  }
  if (form.description.trim().length < 10) {
    error.value = '故障描述至少 10 个字'
    return
  }
  if (pics.value.length === 0) {
    error.value = '请至少上传 1 张现场照片'
    return
  }
  if (!form.privacyConfirmed) {
    error.value = '请先确认隐私提示'
    return
  }

  submitting.value = true
  try {
    const data: any = await createOrder({
      title: form.title.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId,
      locationId: selectedLocationId.value,
      facilityId: form.facilityId > 0 ? form.facilityId : undefined,
      urgency: form.urgency,
      images: pics.value.map((p, i) => ({
        objectKey: p.objectKey,
        stage: 'REPORT',
        sort: i,
        fileName: p.fileName,
        fileSize: p.fileSize
      })),
      privacyConfirmed: true
    })
    toastOk(data?.message || '已提交报修')
    emit('created', data.id)
    emit('close')
  } catch (e: any) {
    error.value = e?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.lbl em {
  color: var(--brand);
  font-style: normal;
}
.urg {
  display: flex;
  gap: 6px;
}
.urg-btn {
  flex: 1;
  height: 40px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
  transition: 0.15s;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.urg-btn:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.urg-btn.on {
  background: var(--brand-l2);
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 600;
}
.loc-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.loc-row .field {
  flex: 1;
  min-width: 110px;
}
.hint-line {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 6px;
}
textarea.field {
  resize: vertical;
  height: auto;
  padding: 9px 11px;
  font-family: inherit;
  line-height: 1.6;
}
.pics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pic {
  position: relative;
  width: 84px;
  height: 84px;
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
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  line-height: 1;
  display: grid;
  place-items: center;
  cursor: pointer;
}
.pic-add {
  width: 84px;
  height: 84px;
  border: 1px dashed #cfd3da;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 22px;
  color: var(--ink-3);
  cursor: pointer;
  transition: 0.15s;
}
.pic-add:hover {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-l2);
}
.privacy {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 12.5px;
  color: var(--ink-2);
  background: #fff9e8;
  border: 1px solid #ffe2b0;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  line-height: 1.6;
}
.privacy input {
  margin-top: 2px;
}
</style>
