/** The app has to send this exact word, so a stray or replayed call with another body can't delete an account. */
export const DELETE_CONFIRMATION = "DELETE";

/** Refusals `delete_account()` returns as `{"error": code}`; the function answers 409 with the code. */
export const DELETE_REFUSALS = ["last_admin", "active_subscription"];

export function isDeleteConfirmed(body: unknown): boolean {
  return typeof body === "object" && body !== null &&
    (body as Record<string, unknown>).confirm === DELETE_CONFIRMATION;
}
