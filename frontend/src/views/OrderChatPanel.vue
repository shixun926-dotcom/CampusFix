<template>
  <div class="sec chat-sec">
    <div class="sec-t">在线沟通</div>

    <p v-if="noCounterpart" class="chat-hint">
      <Icon name="chat" :size="14" />
      维修人员接单后可在此与对方在线沟通
    </p>

    <template v-else>
      <div ref="listEl" class="chat-list">
        <div v-if="messages.length === 0 && !loading" class="chat-empty">
          {{ iAmReporter ? '向维修人员说明故障细节吧' : '与报修人沟通上门细节吧' }}
        </div>

        <div
          v-for="m in messages"
          :key="m.id"
          class="chat-row"
          :class="{ mine: m.senderId === myId }"
        >
          <div class="bubble">
            <div class="b-meta">
              <span class="b-name">{{ m.senderName }}</span>
              <span class="b-time">{{ shortTime(m.createdAt) }}</span>
            </div>
            <div class="b-text">{{ m.content }}</div>
          </div>
        </div>

        <div v-if="loading && messages.length === 0" class="chat-empty">加载中…</div>
      </div>

      <div class="chat-input">
        <input
          v-model="text"
          type="text"
          maxlength="500"
          placeholder="输入消息（1-500 字，回车发送）"
          :disabled="sending"
          @keyup.enter="doSend"
        />
        <button class="btn btn-sm btn-primary" :disabled="sending || !text.trim()" @click="doSend">
          <Icon name="send" :size="14" />
          发送
        </button>
      </div>
      <div class="chat-count" :class="{ over: text.length > 480 }">{{ text.length }}/500</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '../ui/Icon.vue'
import { useAuthStore } from '../stores/auth'
import { toastErr } from '../ui/toast'
import { listMessages, sendMessage, onPush, type ChatMessageVO } from '../api/message'
import type { OrderVO } from '../api/order'

const props = defineProps<{ order: OrderVO; orderId: number | string }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const myId = computed(() => Number(auth.user?.id))
const iAmReporter = computed(() => props.order?.reporterId === myId.value)

/** 报修人视角：维修人未接单时没有会话对象（后端也会拦截） */
const noCounterpart = computed(
  () => iAmReporter.value && !props.order?.assigneeId
)

const messages = ref<ChatMessageVO[]>([])
const text = ref('')
const sending = ref(false)
const loading = ref(false)
const listEl = ref<HTMLElement | null>(null)

let pollTimer: number | undefined
let unsubscribe: (() => void) | null = null

const shortTime = (t?: string) => (t ? t.slice(5, 16) : '')

async function load() {
  if (noCounterpart.value) return
  try {
    const list = await listMessages(props.orderId)
    const prevLen = messages.value.length
    messages.value = list || []
    if (messages.value.length !== prevLen) {
      await nextTick()
      scrollBottom()
    }
  } catch {
    /* 权限不足等情况静默，不打断抽屉 */
  } finally {
    loading.value = false
  }
}

async function doSend() {
  const content = text.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    await sendMessage(props.orderId, content)
    text.value = ''
    await load()
    emit('changed')
  } catch (e: any) {
    toastErr(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

function scrollBottom() {
  if (listEl.value) {
    listEl.value.scrollTop = listEl.value.scrollHeight
  }
}

watch(
  () => props.orderId,
  () => {
    messages.value = []
    loading.value = true
    load()
  },
  { immediate: true }
)

onMounted(() => {
  // WS 推送到达即刷新；轮询兜底（连接失败/断线期间仍可收到消息）
  unsubscribe = onPush((frame) => {
    if (frame.type === 'CHAT' && frame.payload?.orderId === Number(props.orderId)) {
      load()
    }
  })
  pollTimer = window.setInterval(load, 8_000)
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
  if (unsubscribe) unsubscribe()
})
</script>

<style scoped>
.chat-sec .chat-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 11px 13px;
  font-size: 12.5px;
  color: var(--ink-3);
  background: var(--bg);
  border-radius: 10px;
}
.chat-list {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 13px;
  background: var(--bg);
  border-radius: 12px;
  border: 1px solid var(--line-2);
}
.chat-empty {
  text-align: center;
  font-size: 12.5px;
  color: var(--ink-3);
  padding: 18px 0;
}
.chat-row {
  display: flex;
  justify-content: flex-start;
}
.chat-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px 12px 12px 4px;
  padding: 8px 12px 9px;
}
.chat-row.mine .bubble {
  background: var(--brand-l);
  border-color: #f6cfd3;
  border-radius: 12px 12px 4px 12px;
}
.b-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 3px;
}
.b-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-2);
}
.b-time {
  font-size: 10.5px;
  color: var(--ink-3);
}
.b-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-input {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.chat-input input {
  flex: 1;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--ink);
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.chat-input input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-l);
}
.chat-count {
  margin-top: 5px;
  text-align: right;
  font-size: 11px;
  color: var(--ink-3);
}
.chat-count.over {
  color: var(--brand);
}
</style>
