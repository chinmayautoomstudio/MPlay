// Local supabase-js, vendored so Edge Functions boot without fetching jsr:@supabase/supabase-js.
// Regenerated with esbuild from @supabase/supabase-js@2.49.8 (see vendor/supabase-js.js).
import { createClient as createSupabaseClient } from "./vendor/supabase-js.js";

export type User = {
  id: string;
  email?: string | null;
};

export type RpcError = {
  message: string;
  code?: string;
};

export type SupabaseClient = {
  auth: {
    getUser: (jwt: string) => Promise<{
      data: { user: User | null };
      error: { message: string } | null;
    }>;
  };
  rpc: (
    fn: string,
    args?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error: RpcError | null }>;
};

export function createClient(
  url: string,
  key: string,
  options?: Record<string, unknown>,
): SupabaseClient {
  return createSupabaseClient(url, key, options) as SupabaseClient;
}
