<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ElButton, ElSelect, ElOption } from "element-plus";
import { activityApi } from "../api/activity";
import { ApiError, errorMessage, isUncertain } from "../api/client";
import type { Activity, Category } from "../api/types";
import {
  emptyForm,
  validateActivity,
  toInstant,
  toLocalTime,
  safeReturnPath,
  type ActivityForm,
} from "../domain/activity";
import { sessionExpired, currentUser } from "../composables/auth";
import FormField from "../components/FormField.vue";
import DateField from "../components/DateField.vue";
import PageState from "../components/PageState.vue";
import { askConfirmation, success } from "../ui/feedback";
const route = useRoute(),
  router = useRouter();
const editing = computed(() => Boolean(route.params.id)),
  returnTo = computed(() =>
    safeReturnPath(route.query.returnTo || "/activities"),
  );
const form = ref<ActivityForm>(emptyForm()),
  categories = ref<Category[]>([]),
  original = ref<Activity>(),
  baseline = ref("");
const loading = ref(true),
  loadError = ref(""),
  submitError = ref(""),
  submitting = ref(false),
  uncertain = ref(false),
  conflict = ref(false);
const errors = ref<ReturnType<typeof validateActivity>>({});
const dirty = computed(
  () => baseline.value !== "" && JSON.stringify(form.value) !== baseline.value,
);
const frozen = computed(() => original.value?.status === "PUBLISHED");
watch(
  form,
  () => {
    if (Object.keys(errors.value).length)
      errors.value = validateActivity(
        form.value,
        original.value?.registeredCount || 0,
      );
  },
  { deep: true },
);
let saved = false,
  active = true;
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [cats, data] = await Promise.all([
      activityApi.categories(),
      editing.value
        ? activityApi.detail(String(route.params.id))
        : Promise.resolve(undefined),
    ]);
    if (!active) return;
    categories.value = cats;
    original.value = data;
    if (data)
      form.value = {
        title: data.title,
        summary: data.summary,
        content: data.content,
        categoryId: data.categoryId,
        location: data.location,
        capacity: data.capacity,
        startTime: toLocalTime(data.startTime),
        endTime: toLocalTime(data.endTime),
        registrationStart: toLocalTime(data.registrationStart),
        registrationEnd: toLocalTime(data.registrationEnd),
      };
    baseline.value = JSON.stringify(form.value);
    errors.value = {};
    submitError.value = "";
    conflict.value = false;
    uncertain.value = false;
  } catch (e) {
    if (active) loadError.value = errorMessage(e);
  } finally {
    if (active) loading.value = false;
  }
}
async function reload() {
  if (
    !dirty.value ||
    (await askConfirmation(
      "重新读取活动",
      "未保存的修改将被替换为服务器上的最新内容。",
      "重新读取",
    ))
  )
    await load();
}
async function save() {
  if (submitting.value || frozen.value || uncertain.value) return;
  errors.value = validateActivity(
    form.value,
    original.value?.registeredCount || 0,
  );
  const first = Object.keys(errors.value)[0];
  if (first) {
    await nextTick();
    document.getElementById(first)?.focus();
    return;
  }
  submitting.value = true;
  submitError.value = "";
  conflict.value = false;
  const input = {
    ...form.value,
    startTime: toInstant(form.value.startTime),
    endTime: toInstant(form.value.endTime),
    registrationStart: toInstant(form.value.registrationStart),
    registrationEnd: toInstant(form.value.registrationEnd),
    ...(editing.value ? { version: original.value!.version } : {}),
  };
  try {
    if (editing.value) await activityApi.edit(String(route.params.id), input);
    else await activityApi.create(input);
    saved = true;
    success(editing.value ? "活动修改已保存" : "活动已创建，可进入详情发布");
    await router.push(returnTo.value);
  } catch (e) {
    conflict.value = e instanceof ApiError && e.status === 409;
    uncertain.value = isUncertain(e);
    submitError.value = uncertain.value
      ? "暂时无法确认保存结果。请先返回活动列表核实，避免重复创建或提交。当前输入仍保留在此页。"
      : errorMessage(e);
  } finally {
    submitting.value = false;
  }
}
onBeforeRouteLeave(async () => {
  if (saved || !dirty.value || !currentUser.value || sessionExpired.value)
    return true;
  if (submitting.value) return false;
  return askConfirmation(
    "离开编辑页面？",
    "有尚未保存的修改。离开后这些修改将丢失。",
    "放弃修改",
    true,
  );
});
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value && !saved) {
    event.preventDefault();
    event.returnValue = "";
  }
}
onMounted(() => {
  void load();
  window.addEventListener("beforeunload", beforeUnload);
});
onUnmounted(() => {
  active = false;
  window.removeEventListener("beforeunload", beforeUnload);
});
const dateFields = [
  { id: "registrationStart", label: "报名开始时间" },
  { id: "registrationEnd", label: "报名截止时间" },
  { id: "startTime", label: "活动开始时间" },
  { id: "endTime", label: "活动结束时间" },
] as const;
</script>
<template>
  <div class="form-layout">
    <div class="page-heading">
      <div>
        <div class="eyebrow">
          {{ editing ? "Refine the details" : "From idea to gathering" }}
        </div>
        <h1>{{ editing ? "编辑活动" : "创建活动" }}</h1>
        <p>
          {{
            editing
              ? "完善细节，让参与者更了解这场活动。"
              : "从基本信息开始。保存后将成为草稿，可稍后发布。"
          }}
        </p>
      </div>
      <RouterLink :to="returnTo">返回活动列表 →</RouterLink>
    </div>
    <PageState :loading="loading" :error="loadError" @retry="load">
      <div v-if="frozen" class="notice warning" role="status">
        已发布的活动不可直接编辑。请先前往活动详情下架，再进行修改。
        <div>
          <RouterLink :to="'/activities/' + route.params.id"
            >查看活动详情 →</RouterLink
          >
        </div>
      </div>
      <form v-else novalidate @submit.prevent="save" :aria-busy="submitting">
        <div v-if="submitError" class="notice error" role="alert">
          {{ submitError }}
          <div v-if="conflict || uncertain" class="actions">
            <ElButton v-if="editing" @click="reload">重新读取活动</ElButton
            ><RouterLink :to="returnTo">返回列表核实</RouterLink>
          </div>
        </div>
        <fieldset class="stack form-fields" :disabled="submitting">
          <section class="panel">
            <div class="panel-head">
              <div class="form-section-title">
                <span class="section-index">01</span>
                <div>
                  <h2>基本信息</h2>
                  <p>告诉同学们，这是一场怎样的活动。</p>
                </div>
              </div>
              <span class="small muted">* 为必填项</span>
            </div>
            <div class="panel-body form-grid">
              <FormField
                class="full"
                id="title"
                label="活动标题"
                :error="errors.title"
                required
                help="简洁、明确，最多 120 个字符。"
                v-slot="f"
                ><input
                  id="title"
                  v-model="form.title"
                  placeholder="例如：秋日校园摄影工作坊"
                  maxlength="120"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
              /></FormField>
              <FormField
                class="full"
                id="summary"
                label="活动简介"
                :error="errors.summary"
                required
                help="一句话介绍活动亮点，最多 500 个字符。"
                v-slot="f"
              >
                <textarea
                  class="resize-none"
                  id="summary"
                  v-model="form.summary"
                  rows="3"
                  maxlength="500"
                  placeholder="这场活动有什么值得期待？"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
                />
              </FormField>
              <FormField
                id="categoryId"
                label="活动分类"
                :error="errors.categoryId"
                required
                v-slot="f"
                ><ElSelect
                  append-to="#main-content"
                  :disabled="submitting"
                  id="categoryId"
                  :model-value="form.categoryId || undefined"
                  placeholder="选择活动分类"
                  aria-label="活动分类"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
                  @update:model-value="form.categoryId = Number($event)"
                  ><ElOption
                    v-for="category in categories"
                    :key="category.id"
                    :value="category.id"
                    :label="category.name" /></ElSelect
              ></FormField>
              <FormField
                id="location"
                label="活动地点"
                :error="errors.location"
                required
                v-slot="f"
                ><input
                  id="location"
                  v-model="form.location"
                  maxlength="200"
                  placeholder="例如：图书馆二楼研讨室"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
              /></FormField>
              <FormField
                class="full"
                id="content"
                label="详细内容"
                :error="errors.content"
                required
                help="说明活动流程、参与要求与注意事项。支持纯文本换行，最多 20,000 字。"
                v-slot="f"
              >
                <textarea
                  class="resize-none"
                  id="content"
                  v-model="form.content"
                  rows="8"
                  maxlength="20000"
                  placeholder="活动安排、参与方式、需要携带的物品…"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
                />
              </FormField>
            </div>
          </section>
          <section class="panel">
            <div class="panel-head">
              <div class="form-section-title">
                <span class="section-index">02</span>
                <div>
                  <h2>时间安排</h2>
                  <p>报名开始 ＜ 报名截止 ≤ 活动开始 ＜ 活动结束。</p>
                </div>
              </div>
            </div>
            <div class="panel-body form-grid">
              <DateField
                v-for="field in dateFields"
                :key="field.id"
                :id="field.id"
                v-model="form[field.id]"
                :label="field.label"
                :error="errors[field.id]"
              />
            </div>
          </section>
          <section class="panel">
            <div class="panel-head">
              <div class="form-section-title">
                <span class="section-index">03</span>
                <div>
                  <h2>报名设置</h2>
                  <p>设置参与人数上限，报名后可在活动详情查看名单。</p>
                </div>
              </div>
            </div>
            <div class="panel-body form-grid">
              <FormField
                id="capacity"
                label="活动容量"
                :error="errors.capacity"
                required
                :help="
                  '至少 ' +
                  Math.max(1, original?.registeredCount || 0) +
                  ' 人；不能少于当前有效报名人数。'
                "
                v-slot="f"
                ><input
                  id="capacity"
                  v-model.number="form.capacity"
                  type="number"
                  :min="Math.max(1, original?.registeredCount || 0)"
                  max="2147483647"
                  step="1"
                  :aria-invalid="f.invalid"
                  :aria-describedby="f.describedby"
              /></FormField>
              <div class="muted small" style="align-self: center">
                活动保存后不会自动发布。<br />发布后信息将冻结，下架后可以继续编辑。
              </div>
            </div>
          </section>
        </fieldset>
        <div class="form-actions">
          <ElButton :disabled="submitting" @click="router.push(returnTo)"
            >取消</ElButton
          ><ElButton
            type="primary"
            native-type="submit"
            :loading="submitting"
            :disabled="uncertain"
            >{{ editing ? "保存修改" : "创建草稿" }}</ElButton
          >
        </div>
      </form>
    </PageState>
  </div>
</template>
