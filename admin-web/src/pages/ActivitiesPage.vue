<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElButton, ElSelect, ElOption } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { activityApi } from "../api/activity";
import type { Activity, Category, Page } from "../api/types";
import { errorMessage } from "../api/client";
import { formatDate, statusLabel } from "../domain/activity";
import PageState from "../components/PageState.vue";
import StatusBadge from "../components/StatusBadge.vue";
import PaginationBar from "../components/PaginationBar.vue";
const route = useRoute(),
  router = useRouter();
const result = ref<Page<Activity>>({ items: [], total: 0, page: 1, size: 10 }),
  categories = ref<Category[]>([]);
const loading = ref(true),
  error = ref(""),
  categoryError = ref(""),
  search = ref(String(route.query.keyword || ""));
const searchInput = ref<HTMLInputElement>(),
  composing = ref(false);
const page = computed(() =>
  Math.min(10000, Math.max(1, Math.floor(Number(route.query.page) || 1))),
);
const size = computed(() =>
  [10, 20, 50].includes(Number(route.query.size))
    ? Number(route.query.size)
    : 10,
);
const status = computed(() =>
  Object.keys(statusLabel).includes(String(route.query.status))
    ? String(route.query.status)
    : "",
);
const categoryId = computed(() =>
  Number(route.query.categoryId) > 0
    ? Number(route.query.categoryId)
    : undefined,
);
const hasFilters = computed(() =>
  Boolean(route.query.keyword || status.value || categoryId.value),
);
let timer: ReturnType<typeof setTimeout>,
  controller: AbortController | undefined;
function update(values: Record<string, string | number | undefined>) {
  clearTimeout(timer);
  void router.replace({
    path: "/activities",
    query: { ...route.query, ...values },
  });
}
function submitSearch() {
  if (!composing.value)
    update({ keyword: search.value.trim() || undefined, page: undefined });
}
function inputSearch() {
  clearTimeout(timer);
  if (!composing.value) timer = setTimeout(submitSearch, 300);
}
function clearSearch() {
  search.value = "";
  update({ keyword: undefined, page: undefined });
  searchInput.value?.focus();
}
async function loadCategories() {
  categoryError.value = "";
  try {
    categories.value = await activityApi.categories();
  } catch {
    categoryError.value = "分类暂时无法加载。";
  }
}
async function load() {
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  loading.value = true;
  error.value = "";
  const query = new URLSearchParams({
    page: String(page.value),
    size: String(size.value),
  });
  if (route.query.keyword) query.set("keyword", String(route.query.keyword));
  if (status.value) query.set("status", status.value);
  if (categoryId.value) query.set("categoryId", String(categoryId.value));
  try {
    const data = await activityApi.list(query.toString(), signal);
    if (signal.aborted) return;
    const last = Math.max(1, Math.ceil(data.total / size.value));
    if (page.value > last) {
      update({ page: last });
      return;
    }
    result.value = data;
  } catch (e) {
    if (!signal.aborted) error.value = errorMessage(e);
  } finally {
    if (!signal.aborted) loading.value = false;
  }
}
watch(
  () => route.query,
  () => {
    search.value = String(route.query.keyword || "");
    void load();
  },
  { immediate: true },
);
onMounted(loadCategories);
onUnmounted(() => {
  clearTimeout(timer);
  controller?.abort();
});
function detailLink(id: number) {
  return { path: "/activities/" + id, query: { returnTo: route.fullPath } };
}
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">Activity studio</div>
      <h1>活动管理</h1>
      <p>把想法变成相聚，管理活动的每一个阶段。</p>
    </div>
    <RouterLink
      class="button-link primary"
      :to="{ path: '/activities/new', query: { returnTo: route.fullPath } }"
      ><Plus aria-hidden="true" />创建活动</RouterLink
    >
  </div>
  <section class="panel" aria-label="活动列表">
    <div class="filter-bar">
      <form
        class="search-field"
        novalidate
        role="search"
        @submit.prevent="submitSearch"
      >
        <label class="sr-only" for="activity-search">搜索活动</label
        ><input
          id="activity-search"
          ref="searchInput"
          v-model="search"
          placeholder="搜索活动标题或简介…"
          maxlength="100"
          @input="inputSearch"
          @compositionstart="composing = true"
          @compositionend="
            composing = false;
            inputSearch();
          "
        /><button
          v-if="search"
          class="search-clear"
          type="button"
          aria-label="清除搜索"
          @click="clearSearch"
        >
          清除
        </button>
      </form>
      <ElSelect
        append-to="#main-content"
        class="filter-select"
        placeholder="全部分类"
        :model-value="categoryId || ''"
        aria-label="活动分类筛选"
        @update:model-value="
          update({ categoryId: $event || undefined, page: undefined })
        "
        ><ElOption label="全部分类" value="" /><ElOption
          v-for="category in categories"
          :key="category.id"
          :label="category.name"
          :value="category.id"
      /></ElSelect>
      <ElSelect
        append-to="#main-content"
        class="filter-select"
        placeholder="全部状态"
        :model-value="status"
        aria-label="活动状态筛选"
        @update:model-value="
          update({ status: $event || undefined, page: undefined })
        "
        ><ElOption label="全部状态" value="" /><ElOption
          v-for="(label, key) in statusLabel"
          :key="key"
          :value="key"
          :label="label"
      /></ElSelect>
      <ElButton :disabled="loading" @click="load">刷新</ElButton>
    </div>
    <div v-if="categoryError" class="notice error" role="alert">
      {{ categoryError }}
      <ElButton text @click="loadCategories">重新加载分类</ElButton>
    </div>
    <div v-if="hasFilters" class="filter-chips">
      <ElButton v-if="route.query.keyword" size="small" @click="clearSearch"
        >关键词：{{ route.query.keyword }} ×</ElButton
      ><ElButton
        v-if="categoryId"
        size="small"
        @click="update({ categoryId: undefined, page: undefined })"
        >分类：{{
          categories.find((c) => c.id === categoryId)?.name || categoryId
        }}
        ×</ElButton
      ><ElButton
        v-if="status"
        size="small"
        @click="update({ status: undefined, page: undefined })"
        >{{ statusLabel[status as keyof typeof statusLabel] }} ×</ElButton
      ><ElButton text @click="router.replace('/activities')">清除筛选</ElButton>
    </div>
    <PageState
      :loading="loading"
      :error="error"
      :empty="!result.items.length"
      :title="hasFilters ? '没有找到匹配活动' : '还没有活动'"
      :description="hasFilters ? '试试其他关键词，或清除筛选条件。' : undefined"
      @retry="load"
      ><template #action
        ><RouterLink v-if="!hasFilters" to="/activities/new"
          >创建第一场活动 →</RouterLink
        ><ElButton v-else @click="router.replace('/activities')"
          >清除筛选</ElButton
        ></template
      >
      <div class="table-scroll" tabindex="0" aria-label="活动表格，可横向滚动">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">活动名称</th>
              <th scope="col">状态</th>
              <th scope="col">活动开始 · 北京时间</th>
              <th scope="col">报名 / 容量</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="activity in result.items" :key="activity.id">
              <td class="table-title">
                <RouterLink :to="detailLink(activity.id)">{{
                  activity.title
                }}</RouterLink
                ><small
                  >{{ activity.categoryName }} · {{ activity.location }}</small
                >
              </td>
              <td><StatusBadge :status="activity.status" /></td>
              <td class="nowrap number">
                {{ formatDate(activity.startTime) }}
              </td>
              <td class="nowrap number">
                {{ activity.registeredCount }}
                <span class="muted">/ {{ activity.capacity }}</span
                ><small class="muted" style="display: block"
                  >剩余 {{ activity.remainingCapacity }} 人</small
                >
              </td>
              <td class="nowrap">
                <RouterLink :to="detailLink(activity.id)"
                  >查看详情 →</RouterLink
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </PageState>
    <PaginationBar
      :page="page"
      :size="size"
      :total="result.total"
      :busy="loading"
      @page="update({ page: $event })"
      @size="update({ size: $event, page: undefined })"
    />
  </section>
</template>
