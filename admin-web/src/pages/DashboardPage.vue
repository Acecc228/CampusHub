<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { Plus, ArrowRight } from "@element-plus/icons-vue";
import { activityApi } from "../api/activity";
import type { Activity } from "../api/types";
import { errorMessage } from "../api/client";
import { currentUser } from "../composables/auth";
import { formatDate, toLocalTime } from "../domain/activity";
import PageState from "../components/PageState.vue";
import StatusBadge from "../components/StatusBadge.vue";
const loading = ref(true),
  error = ref(""),
  recent = ref<Activity[]>([]),
  counts = ref<number[]>([]);
let controller: AbortController | undefined;
const isAdmin = computed(() => currentUser.value?.roles.includes("ADMIN"));
const cards = [
  { label: "全部活动", status: "", hint: "当前可管理的活动" },
  { label: "已发布", status: "PUBLISHED", hint: "学生可以浏览与报名" },
  { label: "筹备中的草稿", status: "DRAFT", hint: "打磨想法，准备发布" },
  { label: "已下架", status: "UNPUBLISHED", hint: "调整后可以重新发布" },
];
async function load() {
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  loading.value = true;
  error.value = "";
  try {
    const data = await Promise.all(
      cards.map((card, i) =>
        activityApi.list(
          `page=1&size=${i === 0 ? 5 : 1}${card.status ? "&status=" + card.status : ""}`,
          signal,
        ),
      ),
    );
    if (signal.aborted) return;
    counts.value = data.map((page) => page.total);
    recent.value = data[0]?.items || [];
  } catch (e) {
    if (!signal.aborted) error.value = errorMessage(e);
  } finally {
    if (!signal.aborted) loading.value = false;
  }
}
onMounted(load);
onUnmounted(() => controller?.abort());
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">Campus, connected.</div>
      <h1>每一场精彩，从这里开始。</h1>
      <p>
        {{ currentUser?.displayName }}，欢迎回来。这是{{
          isAdmin ? "全校" : "你负责的"
        }}活动概况。
      </p>
    </div>
    <RouterLink class="button-link primary" to="/activities/new"
      ><Plus aria-hidden="true" />创建活动</RouterLink
    >
  </div>
  <PageState :loading="loading" :error="error" @retry="load">
    <div class="stats">
      <RouterLink
        v-for="(card, i) in cards"
        :key="card.label"
        class="panel stat"
        :to="{
          path: '/activities',
          query: card.status ? { status: card.status } : {},
        }"
        ><span class="stat-label">{{ card.label }}</span
        ><strong class="stat-number" style="color: var(--ch-text)">{{
          counts[i]
        }}</strong
        ><span class="stat-foot">{{ card.hint }} ↗</span></RouterLink
      >
    </div>
    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>最近创建的活动</h2>
            <p>查看安排，跟进每场活动的进度。</p>
          </div>
          <RouterLink to="/activities" class="small">查看全部 →</RouterLink>
        </div>
        <PageState :empty="recent.length === 0"
          ><template #action
            ><RouterLink to="/activities/new">创建活动 →</RouterLink></template
          >
          <div v-for="activity in recent" :key="activity.id" class="date-card">
            <div class="date-stamp" aria-hidden="true">
              <span>{{ toLocalTime(activity.startTime).slice(5, 7) }} 月</span
              ><b>{{ toLocalTime(activity.startTime).slice(8, 10) }}</b>
            </div>
            <div class="date-info">
              <RouterLink :to="'/activities/' + activity.id">{{
                activity.title
              }}</RouterLink>
              <p>
                {{ formatDate(activity.startTime) }} · {{ activity.location }}
              </p>
              <p>
                {{ activity.categoryName }} · {{ activity.registeredCount }} /
                {{ activity.capacity }} 人报名
              </p>
            </div>
            <StatusBadge :status="activity.status" />
          </div>
        </PageState>
      </section>
      <aside class="panel panel-body">
        <div class="eyebrow">Make it happen</div>
        <h2>下一步，安排起来。</h2>
        <RouterLink class="quick-link" to="/activities/new"
          ><span
            ><strong>筹备一场新活动</strong
            ><small>完善信息，保存为草稿</small></span
          ><ArrowRight aria-hidden="true" /></RouterLink
        ><RouterLink class="quick-link" to="/activities?status=DRAFT"
          ><span
            ><strong>继续编辑草稿</strong
            ><small>准备就绪后，即可发布</small></span
          ><ArrowRight aria-hidden="true" /></RouterLink
        ><RouterLink class="quick-link" to="/activities?status=PUBLISHED"
          ><span
            ><strong>查看报名情况</strong
            ><small>进入活动，查看报名名单</small></span
          ><ArrowRight aria-hidden="true"
        /></RouterLink>
        <p class="scope-note">
          数据范围：{{
            isAdmin ? "管理员可管理的全部活动" : "本人创建的活动"
          }}。<br />统计来源于实时活动列表；报名人数在各活动中查看。
        </p>
      </aside>
    </div>
  </PageState>
</template>
