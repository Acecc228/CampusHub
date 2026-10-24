<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { ref, computed } from "vue";
import { ElButton } from "element-plus";
import { Grid, Calendar, School } from "@element-plus/icons-vue";
import { currentUser, signOut } from "../composables/auth";
import { roleLabel, canManage } from "../domain/activity";
import { errorMessage } from "../api/client";
const route = useRoute(),
  router = useRouter();
const logoutBusy = ref(false),
  error = ref("");
const rolesText = computed(() =>
  currentUser.value?.roles.map((role) => roleLabel[role]).join(" · "),
);
async function logout() {
  if (logoutBusy.value) return;
  logoutBusy.value = true;
  error.value = "";
  try {
    await signOut();
    await router.replace("/login");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    logoutBusy.value = false;
  }
}
</script>
<template>
  <a class="skip-link" href="#main-content">跳转到主要内容</a>
  <div class="app-shell">
    <aside class="sidebar" aria-label="管理中心侧栏">
      <RouterLink class="brand" to="/"
        ><span class="brand-mark"><School aria-hidden="true" /></span
        >CampusHub</RouterLink
      >
      <div class="brand-sub">校园活动 · 管理中心</div>
      <div v-if="currentUser && canManage(currentUser.roles)" class="nav-label">
        工作空间
      </div>
      <nav
        v-if="currentUser && canManage(currentUser.roles)"
        class="side-nav"
        aria-label="主导航"
      >
        <RouterLink
          to="/"
          :class="{ active: route.path === '/' }"
          :aria-current="route.path === '/' ? 'page' : undefined"
          ><Grid aria-hidden="true" />工作台</RouterLink
        >
        <RouterLink
          to="/activities"
          :class="{ active: route.path.startsWith('/activities') }"
          :aria-current="
            route.path.startsWith('/activities') ? 'page' : undefined
          "
          ><Calendar aria-hidden="true" />活动管理</RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <strong>让每一次相聚，更有意义。</strong
        >从一个想法，到一场好活动。<br />在这里，连接校园里的可能。
      </div>
    </aside>
    <header class="header">
      <nav class="breadcrumbs" aria-label="面包屑">
        <RouterLink to="/">管理中心</RouterLink><span>/</span
        ><RouterLink
          v-if="route.params.id || route.path.endsWith('/new')"
          to="/activities"
          >活动管理</RouterLink
        ><span v-if="route.params.id || route.path.endsWith('/new')">/</span
        ><strong aria-current="page">{{ route.meta.title }}</strong>
      </nav>
      <div class="user-area">
        <span class="avatar" aria-hidden="true">{{
          currentUser?.displayName.slice(0, 1) || "C"
        }}</span>
        <div>
          <div class="user-name">{{ currentUser?.displayName }}</div>
          <div class="role">{{ rolesText }}</div>
        </div>
        <ElButton text :loading="logoutBusy" @click="logout">退出登录</ElButton>
      </div>
    </header>
    <main id="main-content" class="content" tabindex="-1">
      <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      <slot />
    </main>
  </div>
</template>
