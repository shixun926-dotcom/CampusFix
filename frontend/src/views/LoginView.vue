<template>
  <div class="login">
    <!-- 全局封面：项目内静态资源（构建时由 Vite 处理为带哈希的产物 URL），铺满整页 -->
    <div class="login-cover" :style="{ backgroundImage: `url(${loginCover})` }" aria-hidden="true" />
    <div class="login-veil veil-left" aria-hidden="true" />
    <div class="login-veil veil-right" aria-hidden="true" />

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
          <span>每一次流转都有记录，进度随时可查</span>
        </li>
        <li>
          <span class="ic"><Icon name="bolt" :size="15" /></span>
          <span>审核 → 指派 / 抢单 → 预约 → 维修 → 验收</span>
        </li>
        <li>
          <span class="ic"><Icon name="shield" :size="15" /></span>
          <span>四类角色各司其职，只看到与自己相关的内容</span>
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
          <div class="input-wrap has-eye">
            <Icon name="lock" :size="16" />
            <input
              v-model="password"
              :type="showPwd ? 'text' : 'password'"
              class="field"
              placeholder="请输入密码"
              @keyup.enter="onSubmit"
            />
            <button
              class="eye"
              type="button"
              :title="showPwd ? '隐藏密码' : '显示密码'"
              :aria-label="showPwd ? '隐藏密码' : '显示密码'"
              @click="showPwd = !showPwd"
            >
              <!-- 图标表达「当前状态」：密文时划线眼（不可见），明文时睁眼（可见） -->
              <Icon :name="showPwd ? 'eye' : 'eyeOff'" :size="16" />
            </button>
          </div>
        </div>

        <p v-if="error" class="err-text">{{ error }}</p>

        <button class="btn btn-primary btn-lg btn-block" :disabled="loading" @click="onSubmit">
          <span v-if="!loading">登 录</span>
          <span v-else class="spin" style="width: 16px; height: 16px" />
        </button>

        <p class="acc-help">账号由学校统一开通，忘记密码请联系后勤管理员重置</p>
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
// 封面图作为项目内静态资源引入：由 Vite 打包为带哈希的产物，随应用一起分发
import loginCover from '../assets/login-cover.jpg'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const showPwd = ref(false)

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
