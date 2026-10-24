import { createRouter, createWebHistory } from "vue-router";
import {
  ensureIdentity,
  currentUser,
  sessionExpired,
} from "./composables/auth";
import { canManage } from "./domain/activity";
import LoginPage from "./pages/LoginPage.vue";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginPage, meta: { title: "登录" } },
    {
      path: "/",
      component: () => import("./pages/DashboardPage.vue"),
      meta: { title: "工作台" },
    },
    {
      path: "/activities",
      component: () => import("./pages/ActivitiesPage.vue"),
      meta: { title: "活动管理" },
    },
    {
      path: "/activities/new",
      component: () => import("./pages/ActivityFormPage.vue"),
      meta: { title: "创建活动" },
    },
    {
      path: "/activities/:id/edit",
      component: () => import("./pages/ActivityFormPage.vue"),
      meta: { title: "编辑活动" },
    },
    {
      path: "/activities/:id",
      component: () => import("./pages/ActivityDetailPage.vue"),
      meta: { title: "活动详情" },
    },
    {
      path: "/403",
      component: () => import("./pages/ErrorPage.vue"),
      meta: { title: "无权访问", status: 403 },
    },
    {
      path: "/unavailable",
      component: () => import("./pages/ErrorPage.vue"),
      meta: { title: "服务连接失败", status: 503 },
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("./pages/ErrorPage.vue"),
      meta: { title: "页面不存在", status: 404 },
    },
  ],
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
});
router.beforeEach(async (to) => {
  if (to.path === "/unavailable") return;
  try {
    await ensureIdentity();
  } catch {
    return "/unavailable";
  }
  if (sessionExpired.value) return false;
  if (to.path === "/login") return currentUser.value ? "/" : undefined;
  if (!currentUser.value)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (!canManage(currentUser.value.roles) && to.path !== "/403") return "/403";
});
router.afterEach((to, from) => {
  document.title = `${String(to.meta.title)} · CampusHub`;
  if (to.path !== from.path)
    requestAnimationFrame(() =>
      document.querySelector<HTMLElement>("main")?.focus(),
    );
});
