<template>
  <div class="login">
    <section class="login-brand">
      <div class="logo">
        <span class="mark">CF</span>
        <span>CampusFix</span>
      </div>

      <h1>让每一次报修<br />都有<em>清晰的下文</em></h1>
      <p class="lead">
        从发现问题到维修验收，全过程留痕。学生、维修人员与后勤管理员在同一条工单上协作，
        状态流转有据可查，历史数据沉淀为运维决策依据。
      </p>

      <ul class="feat">
        <li>
          <span class="ic"><Icon name="receipt" :size="15" /></span>
          <span>工单状态机驱动，越权流转会被后端拦截</span>
        </li>
        <li>
          <span class="ic"><Icon name="bolt" :size="15" /></span>
          <span>审核 → 指派 / 抢单 → 预约 → 维修 → 验收</span>
        </li>
        <li>
          <span class="ic"><Icon name="shield" :size="15" /></span>
          <span>四类角色分权，数据按部门与归属隔离</span>
        </li>
      </ul>

      <div class="quote">
        <span class="line" />
        CampusFix · 校园设施报修与工单管理平台
      </div>
    </section>

    <section class="login-panel">
      <div class="card login-card">
        <span class="eyebrow">Welcome back</span>
        <h2>账号登录</h2>
        <p class="sub">使用学号或工号登录，无需注册</p>

        <div class="form-row">
          <label class="lbl">账号</label>
          <div class="input-wrap">
            <Icon name="user" :size="16" />
            <input v-model="username" class="field" placeholder="学号 / 工号" @keyup.enter="onSubmit" />
          </div>
        </div>
        <div class="form-row">
          <label class="lbl">密码</label>
          <div class="input-wrap">
            <Icon name="lock" :size="16" />
            <input
              v-model="password"
              :type="showPwd ? 'text' : 'password'"
              class="field"
              placeholder="请输入密码"
              @keyup.enter="onSubmit"
            />
            <button class="eye" type="button" :title="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
              <Icon :name="showPwd ? 'eyeOff' : 'eye'" :size="16" />
            </button>
          </div>
        </div>

        <p v-if="error" class="err-text">{{ error }}</p>

        <button class="btn btn-primary btn-lg btn-block" :disabled="loading" @click="onSubmit">
          <span v-if="!loading">登 录</span>
          <span v-else class="spin" style="width: 16px; height: 16px" />
        </button>

        <div class="acc-hint">
          <div class="t">测试账号（密码均为 Cf@123456）</div>
          <div class="list">
            <span v-for="a in accounts" :key="a.username" class="acc" @click="fill(a.username)">
              <span class="role-dot" :style="{ background: a.color }" />
              {{ a.label }} {{ a.username }}
            </span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../ui/Icon.vue'
import { toastOk } from '../ui/toast'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('1000')
const password = ref('Cf@123456')
const loading = ref(false)
const error = ref('')
const showPwd = ref(false)

const accounts = [
  { username: '1000', label: '系统管理员', color: '#e63946' },
  { username: '20001', label: '后勤管理员', color: '#f59e0b' },
  { username: '30001', label: '维修人员', color: '#4f6ef7' },
  { username: '2021001', label: '学生', color: '#0ea5a4' }
]

function fill(name: string) {
  username.value = name
  password.value = 'Cf@123456'
  error.value = ''
}

async function onSubmit() {
  if (loading.value) return
  error.value = ''
  if (!username.value.trim()) {
    error.value = '请输入学号或工号'
    return
  }
  if (!password.value) {
    error.value = '请输入密码'
    return
  }

  loading.value = true
  try {
    await auth.login({ username: username.value.trim(), password: password.value })
    toastOk('登录成功')
    router.push((route.query.redirect as string) || '/dashboard')
  } catch (e: any) {
    error.value = e?.message || '登录失败，请检查账号与密码'
  } finally {
    loading.value = false
  }
}
</script>
