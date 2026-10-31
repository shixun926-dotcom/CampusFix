<template>
  <div class="stats">
    <div class="page-hd">
      <div>
        <h1>数据统计</h1>
        <p class="sub">
          维修工绩效 · 月度趋势 · 投诉洞察
          <span v-if="isManager">（数据范围：本部门及下级）</span>
          <span v-else>（数据范围：全校）</span>
        </p>
      </div>
      <button class="btn btn-sm" @click="load">
        <Icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <!-- 投诉与效率 KPI -->
    <div class="kpis">
      <div class="kpi">
        <div class="l"><Icon name="check" :size="14" />本月完成</div>
        <div class="v">{{ thisMonth.completed }}</div>
        <div class="d"><span class="muted">本月新增 {{ thisMonth.total }} 单</span></div>
      </div>
      <div class="kpi">
        <div class="l"><Icon name="chart" :size="14" />投诉率</div>
        <div class="v">{{ complaint.ratePercent }}<small>%</small></div>
        <div class="d"><span class="muted">口径：投诉数 / (总单数 + 10)</span></div>
      </div>
      <div class="kpi warn">
        <div class="l"><Icon name="alert" :size="14" />待受理投诉</div>
        <div class="v">{{ complaint.pending }}</div>
        <div class="d"><span class="muted">处理中 {{ complaint.processing }} 单</span></div>
      </div>
      <div class="kpi ok">
        <div class="l"><Icon name="shield" :size="14" />维修人员</div>
        <div class="v">{{ rank.length }}</div>
        <div class="d"><span class="muted">全部已评价 {{ evaluatedCount }} 人</span></div>
      </div>
    </div>

    <div class="stats-grid">
      <!-- 维修工绩效排行 -->
      <section class="panel">
        <div class="panel-hd">
          <div>
            <h3>维修工绩效排行</h3>
            <span class="sb">按加权平均分排序 · 好评率 = 总分 ≥4 占比</span>
          </div>
          <span class="spacer" />
          <span class="tag tag-brand">洞察</span>
        </div>
        <div class="panel-bd">
          <table class="tbl">
            <thead>
              <tr>
                <th style="width: 52px">排名</th>
                <th>姓名</th>
                <th style="width: 110px">部门</th>
                <th style="width: 88px; text-align: right">完成单</th>
                <th style="width: 92px; text-align: right">平均分</th>
                <th style="width: 88px; text-align: right">好评率</th>
                <th style="width: 108px; text-align: right">平均处理</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="rank.length === 0">
                <td colspan="7">
                  <div class="empty"><span class="t">暂无维修人员数据</span></div>
                </td>
              </tr>
              <tr v-for="(r, i) in rank" :key="r.repairerId">
                <td>
                  <span class="rk" :class="'rk-' + (i + 1)">{{ i + 1 }}</span>
                </td>
                <td>{{ r.realName }}</td>
                <td style="color: var(--ink-3)">{{ r.deptName || '未分组' }}</td>
                <td class="num">{{ r.completedCount }}</td>
                <td class="num">
                  <b v-if="r.avgScore != null" :style="{ color: scoreColor(r.avgScore) }">{{ r.avgScore.toFixed(2) }}</b>
                  <span v-else class="muted">未评价</span>
                </td>
                <td class="num">{{ r.goodRatePercent != null ? r.goodRatePercent + '%' : '—' }}</td>
                <td class="num" style="color: var(--ink-3)">
                  {{ r.avgHandleMinutes > 0 ? fmtDuration(r.avgHandleMinutes) : '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="stats-col">
        <!-- 月度趋势 -->
        <section class="panel">
          <div class="panel-hd">
            <div>
              <h3>月度工单趋势</h3>
              <span class="sb">近 6 个月新增与完成</span>
            </div>
          </div>
          <div class="panel-bd">
            <div class="mchart">
              <div v-for="m in monthlyTrend" :key="m.month" class="m-col">
                <div class="m-bars">
                  <div class="m-bar total" :style="{ height: barH(m.total) }" :title="'新增 ' + m.total" />
                  <div class="m-bar done" :style="{ height: barH(m.completed) }" :title="'完成 ' + m.completed" />
                </div>
                <span class="m-num">{{ m.total }}</span>
                <span class="m-label">{{ m.month }}</span>
              </div>
            </div>
            <div class="m-legend">
              <span><i class="dot total" />新增工单</span>
              <span><i class="dot done" />完成</span>
            </div>
          </div>
        </section>

        <!-- 投诉结构 -->
        <section class="panel">
          <div class="panel-hd">
            <div>
              <h3>投诉结构</h3>
              <span class="sb">共 {{ complaint.total }} 单 · 投诉率 {{ complaint.ratePercent }}%</span>
            </div>
          </div>
          <div class="panel-bd">
            <div class="cmp-bars">
              <div v-for="s in complaintBars" :key="s.name" class="cmp-row">
                <span class="cmp-name">{{ s.name }}</span>
                <div class="cmp-track">
                  <i :style="{ width: s.pct + '%', background: s.color }" />
                </div>
                <span class="cmp-num">{{ s.count }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import { useAuthStore } from '../stores/auth'
import { getStatsInsight, type StatsInsight } from '../api/stats'

const auth = useAuthStore()
const insight = ref<StatsInsight | null>(null)
const isManager = computed(() => auth.hasRole('MANAGER'))

const rank = computed(() => insight.value?.repairerRank || [])
const monthlyTrend = computed(() => insight.value?.monthlyTrend || [])
const complaint = computed(
  () => insight.value?.complaint || { total: 0, pending: 0, processing: 0, resolved: 0, rejected: 0, ratePercent: 0 }
)
const evaluatedCount = computed(() => rank.value.filter((r) => r.avgScore != null).length)
const thisMonth = computed(() => monthlyTrend.value[monthlyTrend.value.length - 1] || { month: '', total: 0, completed: 0 })

const complaintBars = computed(() => {
  const c = complaint.value
  const max = Math.max(1, c.pending, c.processing, c.resolved, c.rejected)
  return [
    { name: '待受理', count: c.pending, color: '#e63946', pct: Math.round((c.pending / max) * 100) },
    { name: '处理中', count: c.processing, color: '#f59e0b', pct: Math.round((c.processing / max) * 100) },
    { name: '已成立', count: c.resolved, color: '#7c5cfc', pct: Math.round((c.resolved / max) * 100) },
    { name: '不成立', count: c.rejected, color: '#9aa3b2', pct: Math.round((c.rejected / max) * 100) }
  ]
})

function barH(v: number) {
  const max = Math.max(1, ...monthlyTrend.value.map((m) => m.total))
  if (v <= 0) return '3%'
  return `${Math.max(8, Math.round((v / max) * 100))}%`
}

function fmtDuration(m: number) {
  return m >= 60 ? `${Math.floor(m / 60)}h${m % 60}m` : `${m}m`
}

function scoreColor(score: number) {
  if (score >= 4.5) return '#0a7a3c'
  if (score >= 4) return '#12805c'
  if (score >= 3) return '#d97706'
  return '#e63946'
}

async function load() {
  try {
    insight.value = await getStatsInsight()
  } catch {
    insight.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stats-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}
.stats-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.kpi.warn .v { color: #d97706; }
.kpi.ok .v { color: #0a7a3c; }

.rk {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: inline-grid;
  place-items: center;
  font-size: 11px;
  font-weight: 800;
  background: var(--line-2);
  color: var(--ink-3);
}
.rk-1 { background: #fdecee; color: var(--brand); }
.rk-2 { background: #eef2ff; color: var(--accent); }
.rk-3 { background: #fef4e2; color: #d97706; }

/* 月度双柱图 */
.mchart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 10px;
  padding: 8px 4px 0;
  border-bottom: 1px solid var(--line-2);
}
.m-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.m-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 132px;
}
.m-bar {
  width: 16px;
  border-radius: 5px 5px 0 0;
  min-height: 3px;
  transition: height 0.4s ease;
}
.m-bar.total { background: linear-gradient(180deg, #ff7a5c, #e63946); }
.m-bar.done { background: linear-gradient(180deg, #4ade80, #12a150); }
.m-num {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
}
.m-label {
  font-size: 10.5px;
  color: var(--ink-3);
  padding-bottom: 6px;
}
.m-legend {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--ink-2);
}
.m-legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.dot.total { background: #e63946; }
.dot.done { background: #12a150; }

/* 投诉结构条 */
.cmp-bars { display: flex; flex-direction: column; gap: 13px; }
.cmp-row { display: flex; align-items: center; gap: 10px; font-size: 12.5px; }
.cmp-name { width: 54px; color: var(--ink-2); flex: none; }
.cmp-track {
  flex: 1;
  height: 9px;
  border-radius: 5px;
  background: var(--line-2);
  overflow: hidden;
}
.cmp-track i { display: block; height: 100%; border-radius: 5px; transition: width 0.4s ease; }
.cmp-num { width: 36px; text-align: right; font-weight: 700; color: var(--ink); }

@media (max-width: 1100px) {
  .stats-grid { grid-template-columns: 1fr; }
}
</style>
