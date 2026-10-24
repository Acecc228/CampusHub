<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElButton } from "element-plus";
import { activityApi } from "../api/activity";
import { registrationApi } from "../api/registration";
import { errorMessage, isUncertain } from "../api/client";
import type { Activity, Registration, Page } from "../api/types";
import { formatDate, safeReturnPath } from "../domain/activity";
import PageState from "../components/PageState.vue";
import StatusBadge from "../components/StatusBadge.vue";
import PaginationBar from "../components/PaginationBar.vue";
import { askConfirmation, success } from "../ui/feedback";
const route = useRoute(),
  router = useRouter();
const activity = ref<Activity>(),
  loading = ref(true),
  error = ref(""),
  actionError = ref(""),
  busy = ref(false);
const registrations = ref<Page<Registration>>({
    items: [],
    total: 0,
    page: 1,
    size: 10,
  }),
  listLoading = ref(false),
  listError = ref("");
const tab = computed(() =>
  route.query.tab === "registrations" ? "registrations" : "details",
);
const page = computed(() =>
  Math.min(
    10000,
    Math.max(1, Math.floor(Number(route.query.registrationPage) || 1)),
  ),
);
const size = computed(() =>
  [10, 20, 50].includes(Number(route.query.registrationSize))
    ? Number(route.query.registrationSize)
    : 10,
);
const returnTo = computed(() =>
  safeReturnPath(route.query.returnTo || "/activities"),
);
let controller: AbortController | undefined,
  listController: AbortController | undefined;
async function load() {
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  loading.value = true;
  error.value = "";
  actionError.value = "";
  try {
    const data = await activityApi.detail(String(route.params.id), signal);
    if (!signal.aborted) activity.value = data;
  } catch (e) {
    if (!signal.aborted) {
      activity.value = undefined;
      error.value = errorMessage(e);
    }
  } finally {
    if (!signal.aborted) loading.value = false;
  }
}
async function loadRegistrations() {
  listController?.abort();
  listController = new AbortController();
  const signal = listController.signal;
  listLoading.value = true;
  listError.value = "";
  registrations.value = {
    items: [],
    total: 0,
    page: page.value,
    size: size.value,
  };
  try {
    const [data, fresh] = await Promise.all([
      registrationApi.list(
        String(route.params.id),
        page.value,
        size.value,
        signal,
      ),
      activityApi.detail(String(route.params.id), signal),
    ]);
    if (signal.aborted) return;
    if (!activity.value || fresh.version >= activity.value.version)
      activity.value = fresh;
    const last = Math.max(1, Math.ceil(data.total / size.value));
    if (page.value > last) {
      updateList({ registrationPage: last });
      return;
    }
    registrations.value = data;
  } catch (e) {
    if (!signal.aborted) listError.value = errorMessage(e);
  } finally {
    if (!signal.aborted) listLoading.value = false;
  }
}
function updateList(query: Record<string, number | undefined>) {
  void router.replace({ query: { ...route.query, ...query } });
}
async function transition() {
  if (!activity.value || busy.value) return;
  busy.value = true;
  const publish = activity.value.status !== "PUBLISHED";
  const label = publish
    ? activity.value.status === "DRAFT"
      ? "发布活动"
      : "重新发布"
    : "下架活动";
  const accepted = await askConfirmation(
    `${label}：${activity.value.title}`,
    publish
      ? "发布后，学生可以查看活动并在报名时段内报名。活动信息将冻结，修改前须先下架。"
      : "下架后学生将无法浏览和报名。已有报名会保留，学生在活动开始前仍可取消。",
    label,
    !publish,
  );
  if (!accepted) {
    busy.value = false;
    return;
  }
  actionError.value = "";
  try {
    activity.value = await activityApi.transition(activity.value, publish);
    success(publish ? "活动已发布" : "活动已下架");
  } catch (e) {
    actionError.value = isUncertain(e)
      ? "暂时无法确认操作结果，请刷新活动核实后再操作。"
      : errorMessage(e);
  } finally {
    busy.value = false;
  }
}
watch(() => route.params.id, load, { immediate: true });
watch(
  [tab, page, size],
  () => {
    if (tab.value === "registrations") void loadRegistrations();
    else listController?.abort();
  },
  { immediate: true },
);
onUnmounted(() => {
  controller?.abort();
  listController?.abort();
});
</script>
<template>
  <PageState :loading="loading" :error="error" @retry="load">
    <template v-if="activity">
      <div class="page-heading">
        <div>
          <div class="eyebrow">Activity / {{ activity.id }}</div>
          <h1 style="overflow-wrap: anywhere">{{ activity.title }}</h1>
          <p>
            <StatusBadge :status="activity.status" />
            <span style="margin-left: 10px"
              >{{ activity.categoryName }} · 最近更新
              {{ formatDate(activity.updatedAt) }}</span
            >
          </p>
        </div>
        <div class="actions">
          <ElButton :disabled="busy" @click="load">刷新</ElButton
          ><RouterLink
            v-if="activity.status !== 'PUBLISHED' && !busy"
            class="button-link"
            :to="{
              path: '/activities/' + activity.id + '/edit',
              query: { returnTo },
            }"
            >编辑活动</RouterLink
          ><ElButton
            :type="activity.status === 'PUBLISHED' ? 'default' : 'primary'"
            :loading="busy"
            :disabled="Boolean(actionError)"
            @click="transition"
            >{{
              activity.status === "PUBLISHED"
                ? "下架活动"
                : activity.status === "DRAFT"
                  ? "发布活动"
                  : "重新发布"
            }}</ElButton
          >
        </div>
      </div>
      <div v-if="actionError" class="notice error" role="alert">
        {{ actionError }}
        <div class="actions"><ElButton @click="load">刷新活动</ElButton></div>
      </div>
      <nav class="tabs" aria-label="活动详情导航">
        <RouterLink
          :to="{ query: { ...route.query, tab: undefined } }"
          :class="{ active: tab === 'details' }"
          :aria-current="tab === 'details' ? 'page' : undefined"
          >活动详情</RouterLink
        ><RouterLink
          :to="{ query: { ...route.query, tab: 'registrations' } }"
          :class="{ active: tab === 'registrations' }"
          :aria-current="tab === 'registrations' ? 'page' : undefined"
          >报名名单 · {{ activity.registeredCount }}</RouterLink
        >
      </nav>
      <div v-if="tab === 'details'" class="detail-grid">
        <section class="panel panel-body">
          <h2>关于这场活动</h2>
          <p class="detail-summary">{{ activity.summary }}</p>
          <div class="prose">{{ activity.content }}</div>
        </section>
        <aside class="stack">
          <section class="panel panel-body">
            <div class="muted small">报名情况</div>
            <div class="stat-number">
              {{ activity.registeredCount }}
              <span class="muted" style="font-size: 18px"
                >/ {{ activity.capacity }}</span
              >
            </div>
            <div class="capacity-track" aria-hidden="true">
              <span
                :style="{
                  width:
                    (activity.registeredCount / activity.capacity) * 100 + '%',
                }"
              />
            </div>
            <p class="muted small">
              剩余 {{ activity.remainingCapacity }} 个名额
            </p>
            <RouterLink
              :to="{ query: { ...route.query, tab: 'registrations' } }"
              class="quick-link"
              >查看报名名单 <span>→</span></RouterLink
            >
          </section>
          <section class="panel panel-body">
            <dl class="facts">
              <div>
                <dt>活动地点</dt>
                <dd>{{ activity.location }}</dd>
              </div>
              <div>
                <dt>活动时间 · 北京时间</dt>
                <dd>
                  {{ formatDate(activity.startTime) }}<br />至
                  {{ formatDate(activity.endTime) }}
                </dd>
              </div>
              <div>
                <dt>报名时间 · 北京时间</dt>
                <dd>
                  {{ formatDate(activity.registrationStart) }}<br />至
                  {{ formatDate(activity.registrationEnd) }}
                </dd>
              </div>
              <div>
                <dt>创建者编号</dt>
                <dd>#{{ activity.creatorId }}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
      <section v-else class="panel">
        <div class="panel-head">
          <div>
            <h2>报名名单</h2>
            <p>
              有效报名 {{ activity.registeredCount }} 人，剩余
              {{ activity.remainingCapacity }}
              个名额。列表包含取消记录，仅展示授权学生编号。
            </p>
          </div>
          <ElButton :disabled="listLoading" @click="loadRegistrations"
            >刷新名单</ElButton
          >
        </div>
        <PageState
          :loading="listLoading"
          :error="listError"
          :empty="!registrations.items.length"
          title="还没有报名记录"
          description="活动发布后，同学们在报名时段内报名，记录会出现在这里。"
          @retry="loadRegistrations"
          ><div
            class="table-scroll"
            tabindex="0"
            aria-label="报名表格，可横向滚动"
          >
            <table class="data-table">
              <thead>
                <tr>
                  <th scope="col">学生编号</th>
                  <th scope="col">报名状态</th>
                  <th scope="col">首次报名 · 北京时间</th>
                  <th scope="col">最近更新 · 北京时间</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="registration in registrations.items"
                  :key="registration.id"
                >
                  <td class="number">#{{ registration.userId }}</td>
                  <td><StatusBadge :status="registration.status" /></td>
                  <td class="nowrap number">
                    {{ formatDate(registration.createdAt) }}
                  </td>
                  <td class="nowrap number">
                    {{ formatDate(registration.updatedAt) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div></PageState
        ><PaginationBar
          :page="page"
          :size="size"
          :total="registrations.total"
          :busy="listLoading"
          @page="updateList({ registrationPage: $event })"
          @size="
            updateList({
              registrationSize: $event,
              registrationPage: undefined,
            })
          "
        />
      </section>
      <div style="margin-top: 24px">
        <RouterLink :to="returnTo">← 返回活动列表</RouterLink>
        <p
          v-if="activity.status === 'PUBLISHED'"
          class="muted small"
          style="margin-top: 12px"
        >
          已发布活动不可编辑；如需修改，请先下架。报名数据将保留。
        </p>
      </div>
    </template>
  </PageState>
</template>
