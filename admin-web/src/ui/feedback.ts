import { ElMessage, ElMessageBox } from "element-plus";
export const success = (message: string) =>
  ElMessage({ message, type: "success", grouping: true, duration: 3000 });
let cancelPending: (() => void) | undefined;
window.addEventListener("session-expired", () => {
  cancelPending?.();
  ElMessageBox.close();
  ElMessage.closeAll();
});
export async function askConfirmation(
  title: string,
  message: string,
  action: string,
  danger = false,
) {
  return new Promise<boolean>((resolve) => {
    const settle = (accepted: boolean) => {
      cancelPending = undefined;
      resolve(accepted);
    };
    cancelPending = () => settle(false);
    void ElMessageBox.confirm(message, title, {
      confirmButtonText: action,
      cancelButtonText: "取消",
      type: danger ? "warning" : "info",
      autofocus: false,
      closeOnClickModal: false,
      distinguishCancelAndClose: true,
    }).then(
      () => settle(true),
      () => settle(false),
    );
  });
}
