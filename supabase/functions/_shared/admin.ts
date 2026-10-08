const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FILTERS = ["all", "pro", "trial", "free", "disabled", "admin"];
export const PAGE_MAX = 100;

/** The SQL function an admin request maps to and its arguments, without `p_actor`. */
export type AdminCall = { fn: string; args: Record<string, unknown> };

/** Error codes the SQL functions return as `{"error": ...}` for refusals the app explains. */
export const REFUSALS = ["last_admin", "self", "invalid_email", "invalid_date"];

function userId(value: unknown): string | null {
  return typeof value === "string" && UUID.test(value) ? value.toLowerCase() : null;
}

function email(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim().toLowerCase();
  return trimmed.length > 0 && trimmed.length <= 254 && trimmed.includes("@") ? trimmed : null;
}

function count(value: unknown, fallback: number, max: number): number | null {
  if (value === undefined || value === null) return fallback;
  return Number.isInteger(value) && (value as number) >= 0 && (value as number) <= max ? value as number : null;
}

/** `{ action, ... }` from the app; null when the action is unknown or its fields are malformed. */
export function parseAdminRequest(body: unknown): AdminCall | null {
  const b = (body ?? {}) as Record<string, unknown>;
  switch (b.action) {
    case "overview":
      return { fn: "admin_overview", args: {} };
    case "usage":
      return { fn: "admin_usage", args: {} };
    case "admins":
      return { fn: "admin_list_admins", args: {} };
    case "users": {
      const query = b.query ?? "";
      const filter = b.filter ?? "all";
      const limit = count(b.limit, 50, PAGE_MAX);
      const offset = count(b.offset, 0, 100_000);
      if (typeof query !== "string" || query.length > 100) return null;
      if (typeof filter !== "string" || !FILTERS.includes(filter)) return null;
      if (limit === null || limit === 0 || offset === null) return null;
      return {
        fn: "admin_list_users",
        args: { p_query: query.trim(), p_filter: filter, p_limit: limit, p_offset: offset },
      };
    }
    case "user": {
      const id = userId(b.userId);
      return id ? { fn: "admin_user_detail", args: { p_user: id } } : null;
    }
    case "setRole": {
      const id = userId(b.userId);
      if (!id || (b.role !== "user" && b.role !== "admin")) return null;
      return { fn: "admin_set_role", args: { p_user: id, p_role: b.role } };
    }
    case "setDisabled": {
      const id = userId(b.userId);
      if (!id || typeof b.disabled !== "boolean") return null;
      return { fn: "admin_set_disabled", args: { p_user: id, p_disabled: b.disabled } };
    }
    case "addAdmin": {
      const e = email(b.email);
      return e ? { fn: "admin_add_admin", args: { p_email: e } } : null;
    }
    case "revokeInvite": {
      const e = email(b.email);
      return e ? { fn: "admin_revoke_invite", args: { p_email: e } } : null;
    }
    case "grantPro": {
      const id = userId(b.userId);
      const until = typeof b.until === "string" ? new Date(b.until) : null;
      if (!id || !until || isNaN(until.getTime())) return null;
      return { fn: "admin_grant_pro", args: { p_user: id, p_until: until.toISOString() } };
    }
    case "revokePro": {
      const id = userId(b.userId);
      return id ? { fn: "admin_revoke_pro", args: { p_user: id } } : null;
    }
    case "audit": {
      const limit = count(b.limit, 50, PAGE_MAX);
      const before = count(b.before, 0, Number.MAX_SAFE_INTEGER);
      if (limit === null || limit === 0 || before === null) return null;
      return { fn: "admin_audit", args: { p_limit: limit, p_before: b.before == null ? null : before } };
    }
    default:
      return null;
  }
}
