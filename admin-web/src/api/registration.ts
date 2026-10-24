import { http } from "./client";
import type { Registration, Page } from "./types";
export const registrationApi = {
  list: (id: string, page: number, size: number, signal?: AbortSignal) =>
    http.request<Page<Registration>>(
      `/manage/activities/${id}/registrations?page=${page}&size=${size}`,
      { signal },
    ),
};
