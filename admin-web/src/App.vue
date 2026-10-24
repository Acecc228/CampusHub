<script setup lang="ts">
import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { useRoute } from "vue-router";
import AppShell from "./components/AppShell.vue";
import LoginPage from "./pages/LoginPage.vue";
import { sessionExpired, identityRevision } from "./composables/auth";
const route = useRoute();
</script>
<template>
  <ElConfigProvider :locale="zhCn">
    <div
      v-if="sessionExpired"
      class="session-layer"
      role="dialog"
      aria-modal="true"
      aria-label="登录状态已失效"
    >
      <LoginPage recovery />
    </div>
    <div
      v-show="!sessionExpired"
      :inert="sessionExpired"
      :aria-hidden="sessionExpired || undefined"
      :key="identityRevision"
    >
      <RouterView v-if="route.path === '/login'" />
      <AppShell v-else><RouterView :key="route.path" /></AppShell>
    </div>
  </ElConfigProvider>
</template>
