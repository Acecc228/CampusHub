import { Registration } from "../services/types";
export interface MutationResult {
  verified: boolean;
  registration?: Registration;
  error?: unknown;
}
export function createRegistrationActions(api: {
  change(id: number, version: number, cancel: boolean): Promise<Registration>;
  status(id: number): Promise<Registration>;
}) {
  const pending = new Map<number, Promise<MutationResult>>();
  const uncertain = new Set<number>();
  const expected = new Map<number, { version: number; status: string }>();
  let epoch = 0;
  async function verify(
    id: number,
    generation = epoch,
  ): Promise<MutationResult> {
    try {
      const registration = await api.status(id);
      if (generation !== epoch) return { verified: false };
      const operation = expected.get(id);
      // A non-locking read may race a timed-out transaction that has not committed yet.
      if (
        operation &&
        registration.status !== operation.status &&
        registration.version <= operation.version
      ) {
        uncertain.add(id);
        return { verified: false };
      }
      uncertain.delete(id);
      expected.delete(id);
      return { verified: true, registration };
    } catch (error) {
      if (generation === epoch) uncertain.add(id);
      return { verified: false, error };
    }
  }
  return {
    verify,
    clear() {
      epoch++;
      uncertain.clear();
      expected.clear();
      pending.clear();
    },
    isUncertain: (id: number) => uncertain.has(id),
    change(
      id: number,
      version: number,
      cancel: boolean,
    ): Promise<MutationResult> {
      if (pending.has(id)) return pending.get(id)!;
      if (uncertain.has(id)) return Promise.resolve({ verified: false });
      const generation = epoch;
      const task = (async () => {
        let error: unknown;
        let rejected = false;
        try {
          await api.change(id, version, cancel);
        } catch (e) {
          error = e;
          // Only an explicit 4xx is a known rejection. Network failures and 5xx can follow a commit.
          const status =
            typeof e === "object" && e
              ? (e as { status?: number }).status
              : undefined;
          rejected = !!status && status >= 400 && status < 500;
          if (generation === epoch && !rejected)
            expected.set(id, {
              version,
              status: cancel ? "CANCELLED" : "CONFIRMED",
            });
        }
        if (generation !== epoch) return { verified: false };
        const result = await verify(id, generation);
        // A rejected write is known to have failed even when its status is now hidden.
        if (generation === epoch && rejected && !result.verified) {
          uncertain.delete(id);
          return { verified: true, error };
        }
        return { ...result, error: error || result.error };
      })().finally(() => {
        if (generation === epoch) pending.delete(id);
      });
      pending.set(id, task);
      return task;
    },
  };
}
