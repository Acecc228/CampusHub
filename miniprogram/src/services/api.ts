import { http } from "./transport";
import { Activity, Category, PageResult, Registration } from "./types";
import { createRegistrationActions } from "../domain/registration";
export const api = {
  categories: () => http.request<Category[]>("/activity-categories"),
  activities: (page = 1, keyword = "", categoryId = 0, size = 10) =>
    http.request<PageResult<Activity>>(
      `/activities?page=${page}&size=${size}&keyword=${encodeURIComponent(keyword)}${categoryId ? "&categoryId=" + categoryId : ""}`,
    ),
  detail: (id: number) => http.request<Activity>(`/activities/${id}`),
  registrations: (page = 1) =>
    http.request<PageResult<Registration>>(
      `/student/registrations?page=${page}&size=10`,
    ),
  status: (id: number) =>
    http.request<Registration>(`/activities/${id}/registration`),
  change: (id: number, version: number, cancel: boolean) =>
    http.request<Registration>(
      `/activities/${id}/registrations${cancel ? "/cancel" : ""}`,
      "POST",
      { version },
    ),
};
export const registrationActions = createRegistrationActions(api);
