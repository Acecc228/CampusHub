<script setup lang="ts">
import { ElButton } from "element-plus";
import { Loading, FolderOpened, Warning } from "@element-plus/icons-vue";
defineProps<{
  loading?: boolean;
  error?: string;
  empty?: boolean;
  title?: string;
  description?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div
    v-if="loading || error || empty"
    class="state"
    :role="error ? 'alert' : 'status'"
    :aria-busy="loading"
  >
    <Loading v-if="loading" class="state-symbol spinner" aria-hidden="true" />
    <Warning v-else-if="error" class="state-symbol" aria-hidden="true" />
    <FolderOpened v-else class="state-symbol" aria-hidden="true" />
    <h3>
      {{ loading ? "正在加载…" : error ? "暂时无法加载" : title || "暂无活动" }}
    </h3>
    <p v-if="!loading">
      {{ error || description || "创建第一场活动，让校园里的好想法发生。" }}
    </p>
    <ElButton v-if="error" @click="$emit('retry')">重新加载</ElButton>
    <slot v-if="empty && !loading && !error" name="action" />
  </div>
  <slot v-else />
</template>
