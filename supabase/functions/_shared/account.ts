/** The app has to send this exact word, so a stray or replayed call with another body can't delete an account. */
export const DELETE_CONFIRMATION = "DELETE";

/**
 * Refusals the function answers with 409 and the code: `last_admin` from `delete_account()`, and
 * `mandate_cancel_failed` when PayU didn't confirm the autopay mandate was cancelled (payments CN6).
 */
export const DELETE_REFUSALS = ["last_admin", "mandate_cancel_failed"];

export function isDeleteConfirmed(body: unknown): boolean {
  return typeof body === "object" && body !== null &&
    (body as Record<string, unknown>).confirm === DELETE_CONFIRMATION;
}
