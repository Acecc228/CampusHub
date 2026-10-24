import { http } from "./client";
import type { Activity, ActivityInput, Category, Page } from "./types";
export const activityApi = {
  categories: () => http.request<Category[]>("/activity-categories"),
  list: (query: string, signal?: AbortSignal) =>
    http.request<Page<Activity>>("/manage/activities?" + query, { signal }),
  detail: (id: string, signal?: AbortSignal) =>
    http.request<Activity>("/manage/activities/" + id, { signal }),
  create: (input: ActivityInput) =>
    http.request<Activity>("/manage/activities", {
      method: "POST",
      body: JSON.stringify(input),
    }),
  edit: (id: string, input: ActivityInput) =>
    http.request<Activity>("/manage/activities/" + id, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  transition: (activity: Activity, publish: boolean) =>
    http.request<Activity>(
      `/manage/activities/${activity.id}/${publish ? "publish" : "unpublish"}`,
      { method: "POST", body: JSON.stringify({ version: activity.version }) },
    ),
};
