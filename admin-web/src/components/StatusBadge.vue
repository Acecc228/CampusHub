<script setup lang="ts">
import { statusLabel } from "../domain/activity";
defineProps<{ status: string }>();
const labels: Record<string, string> = {
  ...statusLabel,
  CONFIRMED: "已报名",
  CANCELLED: "已取消",
};
</script>
<template>
  <span class="badge" :class="status">{{ labels[status] || status }}</span>
</template>
