<script setup lang="ts">
import { computed } from "vue";
import { ElButton, ElSelect, ElOption } from "element-plus";
const props = defineProps<{
  page: number;
  size: number;
  total: number;
  busy?: boolean;
}>();
defineEmits<{ page: [value: number]; size: [value: number] }>();
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));
</script>
<template>
  <div class="pagination">
    <span class="muted small" aria-live="polite"
      >共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span
    >
    <div class="pagination-controls">
      <ElSelect
        append-to="#main-content"
        :model-value="size"
        aria-label="每页条数"
        :disabled="busy"
        @update:model-value="$emit('size', Number($event))"
        ><ElOption
          v-for="n in [10, 20, 50]"
          :key="n"
          :value="n"
          :label="n + ' 条 / 页'"
      /></ElSelect>
      <ElButton
        :disabled="busy || page <= 1"
        aria-label="上一页"
        @click="$emit('page', page - 1)"
        >上一页</ElButton
      >
      <ElButton
        :disabled="busy || page >= pages"
        aria-label="下一页"
        @click="$emit('page', page + 1)"
        >下一页</ElButton
      >
    </div>
  </div>
</template>
