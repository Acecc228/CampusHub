import { ref } from "vue";
import { authApi } from "../api/auth";
import { ApiError } from "../api/client";
import type { Identity } from "../api/types";
export const currentUser = ref<Identity | null>(null);
export const sessionExpired = ref(false);
export const identityRevision = ref(0);
let previousId: number | null = null;
let initialized = false;
let pending: Promise<void> | undefined;
window.addEventListener("session-expired", () => {
  if (!currentUser.value) return;
  previousId = currentUser.value.id;
  currentUser.value = null;
  sessionExpired.value = true;
});
export async function ensureIdentity() {
  if (initialized) return;
  if (!pending)
    pending = authApi
      .me()
      .then((user) => {
        currentUser.value = user;
        initialized = true;
      })
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401)
          initialized = true;
        else throw error;
      })
      .finally(() => {
        pending = undefined;
      });
  await pending;
}
export async function signIn(username: string, password: string) {
  const user = await authApi.login(username, password);
  const sameIdentity = previousId === null || previousId === user.id;
  if (!sameIdentity) identityRevision.value++;
  currentUser.value = user;
  previousId = null;
  initialized = true;
  sessionExpired.value = false;
  return sameIdentity;
}
export async function signOut() {
  try {
    await authApi.logout();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) throw error;
  }
  currentUser.value = null;
  previousId = null;
  sessionExpired.value = false;
  identityRevision.value++;
  initialized = true;
}
