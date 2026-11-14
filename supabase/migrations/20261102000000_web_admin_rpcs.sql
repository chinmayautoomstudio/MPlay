-- Web Admin RPCs for Studio Ops (`admin_web_action` + callees / extended overloads).
-- Restored from the pre-cutover securedbmp3 schema onto the Coolify Postgres 18 stack.
-- Mobile Edge Function admin_* signatures stay; these add the web overloads and support/billing/CMS helpers.

SET check_function_bodies = false;
SELECT pg_catalog.set_config('search_path', '', false);

--
-- Name: admin_audit(uuid, integer, bigint, text, text, text, timestamp with time zone, timestamp with time zone); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_audit(p_actor uuid, p_limit integer, p_before bigint, p_action text DEFAULT NULL::text, p_actor_email text DEFAULT NULL::text, p_target_email text DEFAULT NULL::text, p_from timestamp with time zone DEFAULT NULL::timestamp with time zone, p_to timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
    PERFORM public.assert_admin(p_actor);
    RETURN jsonb_build_object('entries', coalesce((
        SELECT jsonb_agg(jsonb_build_object(
            'id', l.id,
            'action', l.action,
            'actorEmail', a.email,
            'targetId', l.target_user_id,
            'targetEmail', t.email,
            'details', l.details,
            'createdAt', l.created_at
        ) ORDER BY l.id DESC)
        FROM (
            SELECT log.*
            FROM public.admin_audit_log log
            LEFT JOIN public.profiles actor ON actor.id = log.actor_id
            LEFT JOIN public.profiles target ON target.id = log.target_user_id
            WHERE (p_before IS NULL OR log.id < p_before)
              AND (p_action IS NULL OR log.action = p_action)
              AND (p_actor_email IS NULL OR actor.email ILIKE '%' || p_actor_email || '%')
              AND (p_target_email IS NULL OR target.email ILIKE '%' || p_target_email || '%')
              AND (p_from IS NULL OR log.created_at >= p_from)
              AND (p_to IS NULL OR log.created_at <= p_to)
            ORDER BY log.id DESC
            LIMIT least(greatest(p_limit, 1), 100)
        ) l
        LEFT JOIN public.profiles a ON a.id = l.actor_id
        LEFT JOIN public.profiles t ON t.id = l.target_user_id
    ), '[]'::jsonb));
END;
$$;


--
-- Name: admin_billing_action(uuid, text, uuid, text, integer, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_billing_action(p_actor uuid, p_action text, p_user uuid, p_txn text, p_amount_paise integer DEFAULT NULL::integer, p_reason text DEFAULT NULL::text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
    v_payment public.payments;
    v_sub public.subscriptions;
    v_remaining integer;
    v_request integer;
BEGIN
    PERFORM public.assert_admin(p_actor);
    IF p_action NOT IN ('reverify', 'refund', 'cancel_subscription', 'revoke_subscription') THEN
        RAISE EXCEPTION 'invalid action %', p_action USING ERRCODE = '22023';
    END IF;

    IF p_action = 'cancel_subscription' THEN
        SELECT * INTO v_sub FROM public.subscriptions
        WHERE user_id = p_user AND provider = 'payu' AND status IN ('active', 'past_due');
        IF NOT FOUND THEN
            RETURN jsonb_build_object('error', 'not_subscribed');
        END IF;
        PERFORM public.admin_log(p_actor, 'cancel_subscription', p_user, jsonb_build_object('subscription', v_sub.id));
        RETURN jsonb_build_object('userId', p_user);
    END IF;

    IF p_action = 'revoke_subscription' THEN
        SELECT * INTO v_sub FROM public.subscriptions
        WHERE user_id = p_user AND provider = 'payu' AND status IN ('active', 'past_due', 'cancelled')
          AND expires_at > now()
        ORDER BY expires_at DESC
        LIMIT 1;
        IF NOT FOUND THEN
            RETURN jsonb_build_object('error', 'not_subscribed');
        END IF;
        PERFORM public.admin_log(p_actor, 'revoke_subscription', p_user, jsonb_build_object('subscription', v_sub.id));
        RETURN jsonb_build_object('userId', p_user, 'subscriptionId', v_sub.id);
    END IF;

    SELECT * INTO v_payment FROM public.payments WHERE txn_id = p_txn;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('error', 'not_found');
    END IF;
    IF p_action = 'refund' AND (v_payment.status NOT IN ('success', 'partially_refunded') OR v_payment.payu_ref IS NULL
                                OR v_payment.refund_request_id IS NOT NULL) THEN
        RETURN jsonb_build_object('error', 'not_refundable');
    END IF;
    v_remaining := v_payment.amount_paise - v_payment.refunded_paise;
    IF p_action = 'refund' THEN
        IF p_amount_paise IS NULL THEN
            v_request := v_remaining;
        ELSIF p_amount_paise < 1 OR p_amount_paise > v_remaining THEN
            RETURN jsonb_build_object('error', 'invalid_amount');
        ELSE
            v_request := p_amount_paise;
        END IF;
    END IF;
    PERFORM public.admin_log(
        p_actor,
        CASE WHEN p_action = 'refund' THEN 'refund_requested' ELSE 'reverify_payment' END,
        v_payment.user_id,
        jsonb_build_object(
            'txnId', p_txn,
            'requestedRefundPaise', v_request,
            'refundReason', p_reason
        )
    );
    RETURN jsonb_build_object(
        'txnId', v_payment.txn_id,
        'si', v_payment.si,
        'status', v_payment.status,
        'payuRef', v_payment.payu_ref,
        'refundablePaise', v_remaining,
        'requestedRefundPaise', v_request,
        'refundReason', p_reason,
        'refundRequestId', v_payment.refund_request_id
    );
END;
$$;


--
-- Name: admin_delete_user_account(uuid, uuid, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_delete_user_account(p_actor uuid, p_user uuid, p_ticket_id bigint DEFAULT NULL::bigint) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_result jsonb;
BEGIN
  PERFORM public.assert_admin(p_actor);
  IF EXISTS (
    SELECT 1 FROM public.subscriptions
    WHERE user_id = p_user AND provider = 'payu' AND autopay_status = 'on'
      AND mandate_cancel_requested_at IS NULL
  ) THEN
    RETURN jsonb_build_object('error', 'mandate_active');
  END IF;
  v_result := public.delete_account(p_user);
  IF v_result ? 'error' THEN
    RETURN v_result;
  END IF;
  PERFORM public.admin_log(p_actor, 'admin_delete_account', p_user, jsonb_build_object('ticketId', p_ticket_id));
  IF p_ticket_id IS NOT NULL THEN
    UPDATE public.support_tickets SET status = 'resolved' WHERE id = p_ticket_id;
    INSERT INTO public.support_messages (ticket_id, direction, body, actor_id)
    VALUES (p_ticket_id, 'note', 'Admin deleted the matched account.', p_actor);
  END IF;
  RETURN v_result;
END;
$$;


--
-- Name: admin_job_runs(uuid, text, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_job_runs(p_actor uuid, p_job text DEFAULT NULL::text, p_limit integer DEFAULT 50) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  RETURN jsonb_build_object(
    'runs', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', r.id,
        'job', r.job,
        'startedAt', r.started_at,
        'finishedAt', r.finished_at,
        'processed', r.processed,
        'failed', r.failed,
        'skipped', r.skipped,
        'error', r.error
      ) ORDER BY r.finished_at DESC)
      FROM (
        SELECT * FROM public.job_runs
        WHERE p_job IS NULL OR job = p_job
        ORDER BY finished_at DESC
        LIMIT least(greatest(coalesce(p_limit, 50), 1), 100)
      ) r
    ), '[]'::jsonb)
  );
END;
$$;


--
-- Name: admin_list_payments(uuid, text, text, integer, integer, text, text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_list_payments(p_actor uuid, p_query text, p_filter text, p_limit integer, p_offset integer, p_currency text DEFAULT NULL::text, p_interval text DEFAULT NULL::text, p_kind text DEFAULT NULL::text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
    v_pattern text := '%' || replace(replace(replace(coalesce(btrim(p_query), ''), '\\', '\\\\'), '%', '\\%'), '_', '\\_') || '%';
    v_result jsonb;
BEGIN
    PERFORM public.assert_admin(p_actor);
    IF p_filter NOT IN ('all', 'success', 'failed', 'pending', 'refunded', 'partially_refunded', 'disputed', 'past_due', 'cancelled') THEN
        RAISE EXCEPTION 'invalid filter %', p_filter USING ERRCODE = '22023';
    END IF;
    IF p_currency IS NOT NULL AND p_currency NOT IN ('INR', 'USD') THEN
        RAISE EXCEPTION 'invalid currency %', p_currency USING ERRCODE = '22023';
    END IF;
    IF p_interval IS NOT NULL AND p_interval NOT IN ('month', 'year') THEN
        RAISE EXCEPTION 'invalid interval %', p_interval USING ERRCODE = '22023';
    END IF;
    IF p_kind IS NOT NULL AND p_kind NOT IN ('first', 'renewal', 'replace') THEN
        RAISE EXCEPTION 'invalid kind %', p_kind USING ERRCODE = '22023';
    END IF;

    WITH matched AS (
        SELECT p.*, u.email, s.status AS sub_status
        FROM public.payments p
        LEFT JOIN public.profiles u ON u.id = p.user_id
        LEFT JOIN public.subscriptions s ON s.id = p.subscription_id
        WHERE coalesce(btrim(p_query), '') = ''
           OR u.email ILIKE v_pattern
           OR p.txn_id ILIKE v_pattern
           OR p.payu_ref ILIKE v_pattern
           OR p.mandate_ref ILIKE v_pattern
    ), filtered AS (
        SELECT * FROM matched
        WHERE CASE p_filter
            WHEN 'all' THEN true
            WHEN 'success' THEN status = 'success'
            WHEN 'failed' THEN status IN ('failed', 'cancelled')
            WHEN 'pending' THEN status IN ('created', 'pending')
            WHEN 'refunded' THEN status IN ('refunded', 'partially_refunded')
            WHEN 'partially_refunded' THEN status = 'partially_refunded'
            WHEN 'disputed' THEN status = 'disputed'
            WHEN 'past_due' THEN sub_status = 'past_due'
            WHEN 'cancelled' THEN sub_status = 'cancelled'
        END
        AND (p_currency IS NULL OR currency = p_currency)
        AND (p_interval IS NULL OR billing_interval = p_interval)
        AND (p_kind IS NULL OR kind = p_kind)
    ), page AS (
        SELECT * FROM filtered
        ORDER BY created_at DESC, id
        LIMIT least(greatest(p_limit, 1), 100) OFFSET greatest(p_offset, 0)
    )
    SELECT jsonb_build_object(
        'total', (SELECT count(*) FROM filtered),
        'payments', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'txnId', x.txn_id,
                'userId', x.user_id,
                'email', x.email,
                'kind', x.kind,
                'status', x.status,
                'amountPaise', x.amount_paise,
                'refundedPaise', x.refunded_paise,
                'currency', x.currency,
                'interval', x.billing_interval,
                'payuRef', x.payu_ref,
                'mandateRef', x.mandate_ref,
                'method', x.method,
                'failureReason', x.failure_reason,
                'linkExpiresAt', x.link_expires_at,
                'resolveUntil', x.resolve_until,
                'subscriptionStatus', x.sub_status,
                'refundPending', x.refund_request_id IS NOT NULL AND x.status = 'success',
                'createdAt', x.created_at,
                'completedAt', x.completed_at
            ) ORDER BY x.created_at DESC, x.id)
            FROM page x
        ), '[]'::jsonb)
    ) INTO v_result;
    RETURN v_result;
END;
$$;


--
-- Name: admin_mark_activity_seen(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_mark_activity_seen(p_actor uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  INSERT INTO public.admin_activity_seen (admin_id, seen_at)
  VALUES (p_actor, now())
  ON CONFLICT (admin_id) DO UPDATE SET seen_at = now();
  RETURN jsonb_build_object('result', 'ok', 'seenAt', now());
END;
$$;


--
-- Name: admin_mark_reviewed(uuid, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_mark_reviewed(p_actor uuid, p_event_id bigint) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  UPDATE public.payment_events
  SET reviewed_at = now(), reviewed_by = p_actor
  WHERE id = p_event_id AND reviewed_at IS NULL;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;
  PERFORM public.admin_log(p_actor, 'mark_reviewed', NULL, jsonb_build_object('eventId', p_event_id));
  RETURN jsonb_build_object('result', 'ok');
END;
$$;


--
-- Name: admin_payment_events(uuid, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_payment_events(p_actor uuid, p_txn text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  RETURN jsonb_build_object(
    'txnId', p_txn,
    'events', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', e.id,
        'provider', e.provider,
        'type', e.event_type,
        'txnId', e.txn_id,
        'reference', e.provider_ref,
        'amountPaise', e.amount_paise,
        'currency', e.currency,
        'createdAt', e.created_at,
        'reviewedAt', e.reviewed_at
      ) ORDER BY e.created_at DESC, e.id DESC)
      FROM public.payment_events e
      WHERE e.txn_id = p_txn
    ), '[]'::jsonb)
  );
END;
$$;


--
-- Name: admin_review_queue(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_review_queue(p_actor uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  RETURN jsonb_build_object(
    'events', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', e.id,
        'userId', e.user_id,
        'type', e.event_type,
        'txnId', e.txn_id,
        'reference', e.provider_ref,
        'amountPaise', e.amount_paise,
        'currency', e.currency,
        'createdAt', e.created_at
      ) ORDER BY e.created_at DESC)
      FROM public.payment_events e
      WHERE e.event_type IN ('verification_mismatch', 'payment_needs_review', 'dispute_opened', 'mandate_ending')
        AND e.created_at > now() - interval '30 days'
        AND e.reviewed_at IS NULL
    ), '[]'::jsonb)
  );
END;
$$;


--
-- Name: admin_stuck_mandates(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_stuck_mandates(p_actor uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  RETURN jsonb_build_object(
    'mandates', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', s.id,
        'userId', s.user_id,
        'email', p.email,
        'status', s.status,
        'mandateRef', s.mandate_ref,
        'requestedAt', s.mandate_cancel_requested_at,
        'autopayStatus', s.autopay_status
      ) ORDER BY s.mandate_cancel_requested_at DESC)
      FROM public.subscriptions s
      LEFT JOIN public.profiles p ON p.id = s.user_id
      WHERE s.mandate_cancel_requested_at IS NOT NULL
        AND s.mandate_cancelled_at IS NULL
    ), '[]'::jsonb)
  );
END;
$$;


--
-- Name: admin_ticket(uuid, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_ticket(p_actor uuid, p_id bigint) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_ticket public.support_tickets;
BEGIN
  PERFORM public.assert_admin(p_actor);
  SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;
  RETURN jsonb_build_object(
    'ticket', jsonb_build_object(
      'id', v_ticket.id,
      'referenceNumber', v_ticket.reference_number,
      'source', v_ticket.source,
      'topic', v_ticket.topic,
      'status', v_ticket.status,
      'requesterName', v_ticket.requester_name,
      'requesterEmail', v_ticket.requester_email,
      'userId', v_ticket.user_id,
      'subject', v_ticket.subject,
      'txnId', v_ticket.txn_id,
      'lastMessageAt', v_ticket.last_message_at,
      'createdAt', v_ticket.created_at,
      'aged', v_ticket.status = 'open' AND v_ticket.created_at < now() - interval '2 days'
    ),
    'messages', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', m.id,
        'ticketId', m.ticket_id,
        'direction', m.direction,
        'body', m.body,
        'fromEmail', m.from_email,
        'toEmail', m.to_email,
        'createdAt', m.created_at,
        'actorId', m.actor_id
      ) ORDER BY m.created_at)
      FROM public.support_messages m
      WHERE m.ticket_id = p_id
    ), '[]'::jsonb),
    'user', CASE WHEN v_ticket.user_id IS NULL THEN NULL ELSE public.admin_user_detail(p_actor, v_ticket.user_id) END
  );
END;
$$;


--
-- Name: admin_ticket_action(uuid, bigint, text, text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_ticket_action(p_actor uuid, p_id bigint, p_action text, p_body text DEFAULT NULL::text, p_status text DEFAULT NULL::text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_ticket public.support_tickets;
BEGIN
  PERFORM public.assert_admin(p_actor);
  SELECT * INTO v_ticket FROM public.support_tickets WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;

  IF p_action = 'setStatus' THEN
    IF p_status NOT IN ('open', 'pending_user', 'resolved', 'spam') THEN
      RETURN jsonb_build_object('error', 'invalid_status');
    END IF;
    UPDATE public.support_tickets SET status = p_status WHERE id = p_id;
    RETURN jsonb_build_object('result', 'ok');
  END IF;

  IF p_action = 'note' THEN
    INSERT INTO public.support_messages (ticket_id, direction, body, actor_id)
    VALUES (p_id, 'note', coalesce(p_body, ''), p_actor);
    UPDATE public.support_tickets SET last_message_at = now() WHERE id = p_id;
    RETURN jsonb_build_object('result', 'ok');
  END IF;

  IF p_action = 'reply' THEN
    INSERT INTO public.support_messages (ticket_id, direction, body, from_email, to_email, actor_id)
    VALUES (p_id, 'out', coalesce(p_body, ''), 'mp3studio.in@gmail.com', v_ticket.requester_email, p_actor);
    UPDATE public.support_tickets SET status = 'pending_user', last_message_at = now() WHERE id = p_id;
    INSERT INTO public.email_outbox (template_key, to_email, user_id, status)
    VALUES ('support_reply', v_ticket.requester_email, v_ticket.user_id, 'queued');
    PERFORM public.admin_log(p_actor, 'support_reply', v_ticket.user_id, jsonb_build_object('ticketId', p_id));
    RETURN jsonb_build_object('result', 'ok', 'subjectPrefix', '[MP3-#' || p_id || ']');
  END IF;

  RETURN jsonb_build_object('error', 'unknown_action');
END;
$$;


--
-- Name: admin_tickets(uuid, text, text, boolean, integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_tickets(p_actor uuid, p_status text DEFAULT 'open'::text, p_topic text DEFAULT NULL::text, p_unmatched boolean DEFAULT NULL::boolean, p_limit integer DEFAULT 50, p_offset integer DEFAULT 0) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  PERFORM public.assert_admin(p_actor);
  RETURN jsonb_build_object(
    'tickets', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', t.id,
        'referenceNumber', t.reference_number,
        'source', t.source,
        'topic', t.topic,
        'status', t.status,
        'requesterName', t.requester_name,
        'requesterEmail', t.requester_email,
        'userId', t.user_id,
        'subject', t.subject,
        'txnId', t.txn_id,
        'lastMessageAt', t.last_message_at,
        'createdAt', t.created_at,
        'aged', t.status = 'open' AND t.created_at < now() - interval '2 days'
      ) ORDER BY t.last_message_at DESC)
      FROM (
        SELECT * FROM public.support_tickets
        WHERE (p_status = 'all' OR status = p_status)
          AND (p_topic IS NULL OR topic = p_topic)
          AND (p_unmatched IS NULL OR (p_unmatched AND user_id IS NULL) OR (NOT p_unmatched AND user_id IS NOT NULL))
        ORDER BY last_message_at DESC
        LIMIT least(greatest(p_limit, 1), 100) OFFSET greatest(p_offset, 0)
      ) t
    ), '[]'::jsonb),
    'templates', coalesce((
      SELECT jsonb_agg(jsonb_build_object('slug', s.slug, 'subject', s.subject, 'body', s.body) ORDER BY s.slug)
      FROM public.support_templates s
    ), '[]'::jsonb)
  );
END;
$$;


--
-- Name: admin_trial_action(uuid, uuid, text, timestamp with time zone); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_trial_action(p_actor uuid, p_user uuid, p_action text, p_until timestamp with time zone DEFAULT NULL::timestamp with time zone) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_trial public.trials;
BEGIN
  PERFORM public.assert_admin(p_actor);
  IF p_action NOT IN ('grant_trial', 'extend_trial', 'reset_trial') THEN
    RAISE EXCEPTION 'invalid action %', p_action USING ERRCODE = '22023';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_user) THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;

  SELECT * INTO v_trial FROM public.trials WHERE user_id = p_user;

  IF p_action = 'grant_trial' THEN
    IF v_trial.user_id IS NOT NULL AND v_trial.ends_at > now() THEN
      RETURN jsonb_build_object('error', 'already_trial');
    END IF;
    INSERT INTO public.trials (user_id, started_at, ends_at, used)
    VALUES (p_user, now(), now() + interval '30 days', false)
    ON CONFLICT (user_id) DO UPDATE
      SET started_at = now(), ends_at = now() + interval '30 days', used = false;
    PERFORM public.admin_log(p_actor, 'grant_trial', p_user, jsonb_build_object('until', now() + interval '30 days'));
    RETURN jsonb_build_object('result', 'ok');
  END IF;

  IF p_action = 'extend_trial' THEN
    IF p_until IS NULL OR p_until <= now() OR p_until > now() + interval '1 year' THEN
      RETURN jsonb_build_object('error', 'invalid_date');
    END IF;
    IF v_trial.user_id IS NULL THEN
      INSERT INTO public.trials (user_id, started_at, ends_at, used)
      VALUES (p_user, now(), p_until, false);
    ELSE
      UPDATE public.trials SET ends_at = p_until WHERE user_id = p_user;
    END IF;
    PERFORM public.admin_log(p_actor, 'extend_trial', p_user, jsonb_build_object('until', p_until));
    RETURN jsonb_build_object('result', 'ok');
  END IF;

  DELETE FROM public.trial_claims WHERE user_id = p_user;
  DELETE FROM public.trials WHERE user_id = p_user;
  PERFORM public.admin_log(p_actor, 'reset_trial', p_user, NULL);
  RETURN jsonb_build_object('result', 'ok');
END;
$$;


--
-- Name: admin_usage(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_usage(p_actor uuid, p_weeks integer DEFAULT 8) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
    v_week date := public.usage_week_start(now());
    v_count integer := least(greatest(coalesce(p_weeks, 8), 1), 52);
BEGIN
    PERFORM public.assert_admin(p_actor);
    RETURN jsonb_build_object(
        'weeks', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'weekStart', w.week,
                'completed', (SELECT count(*) FROM public.ai_usage a WHERE a.week_start = w.week AND a.status = 'completed'),
                'reserved', (SELECT count(*) FROM public.ai_usage a WHERE a.week_start = w.week AND a.status = 'reserved'),
                'released', (SELECT count(*) FROM public.ai_usage a WHERE a.week_start = w.week AND a.status = 'released'),
                'denied', (SELECT count(*) FROM public.ai_usage a WHERE a.week_start = w.week AND a.status = 'denied'),
                'users', (SELECT count(DISTINCT a.user_id) FROM public.ai_usage a
                          WHERE a.week_start = w.week AND a.status = 'completed')
            ) ORDER BY w.week DESC)
            FROM (SELECT (v_week - 7 * g)::date AS week FROM generate_series(0, v_count - 1) g) w
        ), '[]'::jsonb),
        'top', coalesce((
            SELECT jsonb_agg(jsonb_build_object('id', t.user_id, 'email', p.email, 'name', p.display_name,
                                                'completed', t.completed) ORDER BY t.completed DESC, p.email)
            FROM (
                SELECT user_id, count(*) AS completed FROM public.ai_usage
                WHERE week_start = v_week AND status = 'completed'
                GROUP BY user_id ORDER BY count(*) DESC LIMIT 20
            ) t
            JOIN public.profiles p ON p.id = t.user_id
        ), '[]'::jsonb),
        'top30d', coalesce((
            SELECT jsonb_agg(jsonb_build_object('id', t.user_id, 'email', p.email, 'name', p.display_name,
                                                'completed', t.completed) ORDER BY t.completed DESC, p.email)
            FROM (
                SELECT user_id, count(*) AS completed FROM public.ai_usage
                WHERE completed_at > now() - interval '30 days' AND status = 'completed'
                GROUP BY user_id ORDER BY count(*) DESC LIMIT 20
            ) t
            JOIN public.profiles p ON p.id = t.user_id
        ), '[]'::jsonb),
        'topAll', coalesce((
            SELECT jsonb_agg(jsonb_build_object('id', t.user_id, 'email', p.email, 'name', p.display_name,
                                                'completed', t.completed) ORDER BY t.completed DESC, p.email)
            FROM (
                SELECT user_id, count(*) AS completed FROM public.ai_usage
                WHERE status = 'completed'
                GROUP BY user_id ORDER BY count(*) DESC LIMIT 20
            ) t
            JOIN public.profiles p ON p.id = t.user_id
        ), '[]'::jsonb),
        'denied', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
              'id', p.id, 'email', p.email, 'name', p.display_name, 'denied', t.denied
            ) ORDER BY t.denied DESC, p.email)
            FROM (
              SELECT user_id, count(*) AS denied
              FROM public.ai_usage
              WHERE week_start = v_week AND status = 'denied'
              GROUP BY user_id
              ORDER BY count(*) DESC
              LIMIT 50
            ) t
            JOIN public.profiles p ON p.id = t.user_id
        ), '[]'::jsonb),
        'stuckReserved', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
              'jobRef', a.job_ref,
              'userId', a.user_id,
              'email', p.email,
              'songRef', a.song_ref,
              'reservedAt', a.reserved_at
            ) ORDER BY a.reserved_at)
            FROM public.ai_usage a
            LEFT JOIN public.profiles p ON p.id = a.user_id
            WHERE a.status = 'reserved'
              AND a.reserved_at < now() - interval '1 hour'
              AND a.reserved_at > now() - interval '48 hours'
            LIMIT 50
        ), '[]'::jsonb)
    );
END;
$$;


--
-- Name: admin_webhook_action(uuid, bigint, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_webhook_action(p_actor uuid, p_id bigint, p_action text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_row public.webhook_log;
BEGIN
  PERFORM public.assert_admin(p_actor);
  IF p_action NOT IN ('retry', 'ack') THEN
    RAISE EXCEPTION 'invalid action %', p_action USING ERRCODE = '22023';
  END IF;
  SELECT * INTO v_row FROM public.webhook_log WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;
  IF p_action = 'ack' THEN
    UPDATE public.webhook_log SET flagged = false WHERE id = p_id;
    PERFORM public.admin_log(p_actor, 'ack_webhook', NULL, jsonb_build_object('id', p_id));
    RETURN jsonb_build_object('result', 'ok');
  END IF;
  UPDATE public.webhook_log
  SET status = 'received', next_attempt_at = now(), last_error = NULL
  WHERE id = p_id;
  PERFORM public.admin_log(p_actor, 'retry_webhook', NULL, jsonb_build_object('id', p_id));
  RETURN jsonb_build_object('result', 'queued', 'id', p_id);
END;
$$;


--
-- Name: admin_webhook_detail(uuid, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_webhook_detail(p_actor uuid, p_id bigint) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_row public.webhook_log;
BEGIN
  PERFORM public.assert_admin(p_actor);
  SELECT * INTO v_row FROM public.webhook_log WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'not_found');
  END IF;
  RETURN jsonb_build_object(
    'id', v_row.id,
    'receivedAt', v_row.received_at,
    'eventKey', v_row.event_key,
    'status', v_row.status,
    'attempts', v_row.attempts,
    'nextAttemptAt', v_row.next_attempt_at,
    'lastError', v_row.last_error,
    'processedAt', v_row.processed_at,
    'flagged', v_row.flagged,
    'body', public.admin_redact_json(v_row.body)
  );
END;
$$;


--
-- Name: admin_webhooks(uuid, text, integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_webhooks(p_actor uuid, p_status text DEFAULT 'all'::text, p_limit integer DEFAULT 50, p_offset integer DEFAULT 0) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_result jsonb;
BEGIN
  PERFORM public.assert_admin(p_actor);
  IF p_status NOT IN ('all', 'flagged', 'failed', 'received', 'processed', 'ignored', 'hash_mismatch') THEN
    RAISE EXCEPTION 'invalid status %', p_status USING ERRCODE = '22023';
  END IF;

  WITH filtered AS (
    SELECT * FROM public.webhook_log w
    WHERE CASE p_status
      WHEN 'all' THEN true
      WHEN 'flagged' THEN w.flagged
      WHEN 'hash_mismatch' THEN w.status = 'hash_mismatch' OR w.last_error ILIKE '%hash%'
      ELSE w.status = p_status
    END
  ), page AS (
    SELECT * FROM filtered
    ORDER BY received_at DESC, id DESC
    LIMIT least(greatest(p_limit, 1), 100) OFFSET greatest(p_offset, 0)
  )
  SELECT jsonb_build_object(
    'total', (SELECT count(*) FROM filtered),
    'webhooks', coalesce((
      SELECT jsonb_agg(jsonb_build_object(
        'id', x.id,
        'receivedAt', x.received_at,
        'eventKey', x.event_key,
        'status', x.status,
        'attempts', x.attempts,
        'nextAttemptAt', x.next_attempt_at,
        'lastError', x.last_error,
        'processedAt', x.processed_at,
        'flagged', x.flagged
      ) ORDER BY x.received_at DESC, x.id DESC)
      FROM page x
    ), '[]'::jsonb)
  ) INTO v_result;
  RETURN v_result;
END;
$$;


--
-- Name: admin_web_action(text, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE OR REPLACE FUNCTION public.admin_web_action(p_action text, p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
  v_actor uuid := (SELECT auth.uid());
  v_payload jsonb := coalesce(p_payload, '{}'::jsonb);
BEGIN
  PERFORM public.assert_admin(v_actor);

  RETURN CASE p_action
    WHEN 'overview' THEN public.admin_overview(v_actor) || jsonb_build_object(
      'openTickets', (SELECT count(*) FROM public.support_tickets WHERE status = 'open')
    )
    WHEN 'users' THEN public.admin_list_users(
      v_actor, v_payload ->> 'query', coalesce(v_payload ->> 'filter', 'all'),
      coalesce((v_payload ->> 'limit')::int, 50), coalesce((v_payload ->> 'offset')::int, 0), v_payload ->> 'currency'
    )
    WHEN 'user' THEN public.admin_user_detail(v_actor, (v_payload ->> 'userId')::uuid)
    WHEN 'usage' THEN public.admin_usage(v_actor, coalesce((v_payload ->> 'weeks')::int, 8))
    WHEN 'admins' THEN public.admin_list_admins(v_actor)
    WHEN 'addAdmin' THEN public.admin_add_admin(v_actor, v_payload ->> 'email')
    WHEN 'revokeInvite' THEN public.admin_revoke_invite(v_actor, v_payload ->> 'email')
    WHEN 'setRole' THEN public.admin_set_role(v_actor, (v_payload ->> 'userId')::uuid, v_payload ->> 'role')
    WHEN 'setDisabled' THEN public.admin_set_disabled(v_actor, (v_payload ->> 'userId')::uuid, (v_payload ->> 'disabled')::boolean)
    WHEN 'grantPro' THEN public.admin_grant_pro(v_actor, (v_payload ->> 'userId')::uuid, (v_payload ->> 'until')::timestamptz)
    WHEN 'revokePro' THEN public.admin_revoke_pro(v_actor, (v_payload ->> 'userId')::uuid)
    WHEN 'grantTrial' THEN public.admin_trial_action(v_actor, (v_payload ->> 'userId')::uuid, 'grant_trial', NULL)
    WHEN 'extendTrial' THEN public.admin_trial_action(v_actor, (v_payload ->> 'userId')::uuid, 'extend_trial', (v_payload ->> 'until')::timestamptz)
    WHEN 'resetTrial' THEN public.admin_trial_action(v_actor, (v_payload ->> 'userId')::uuid, 'reset_trial', NULL)
    WHEN 'audit' THEN public.admin_audit(
      v_actor, coalesce((v_payload ->> 'limit')::int, 50), (v_payload ->> 'before')::bigint,
      v_payload ->> 'action', v_payload ->> 'actorEmail', v_payload ->> 'targetEmail',
      (v_payload ->> 'from')::timestamptz, (v_payload ->> 'to')::timestamptz
    )
    WHEN 'activity' THEN public.admin_activity(v_actor, coalesce((v_payload ->> 'limit')::int, 50))
    WHEN 'markActivitySeen' THEN public.admin_mark_activity_seen(v_actor)
    WHEN 'payments' THEN public.admin_list_payments(
      v_actor, v_payload ->> 'query', coalesce(v_payload ->> 'filter', 'all'),
      coalesce((v_payload ->> 'limit')::int, 50), coalesce((v_payload ->> 'offset')::int, 0),
      v_payload ->> 'currency', v_payload ->> 'interval', v_payload ->> 'kind'
    )
    WHEN 'paymentEvents' THEN public.admin_payment_events(v_actor, v_payload ->> 'txnId')
    WHEN 'billingHealth' THEN public.admin_billing_health(v_actor)
    WHEN 'billingAction' THEN public.admin_billing_action(
      v_actor, v_payload ->> 'billingAction', (v_payload ->> 'userId')::uuid, v_payload ->> 'txnId',
      (v_payload ->> 'amountPaise')::int, v_payload ->> 'reason'
    )
    WHEN 'webhooks' THEN public.admin_webhooks(v_actor, coalesce(v_payload ->> 'status', 'all'), coalesce((v_payload ->> 'limit')::int, 50), coalesce((v_payload ->> 'offset')::int, 0))
    WHEN 'webhook' THEN public.admin_webhook_detail(v_actor, (v_payload ->> 'id')::bigint)
    WHEN 'retryWebhook' THEN public.admin_webhook_action(v_actor, (v_payload ->> 'id')::bigint, 'retry')
    WHEN 'ackWebhook' THEN public.admin_webhook_action(v_actor, (v_payload ->> 'id')::bigint, 'ack')
    WHEN 'jobRuns' THEN public.admin_job_runs(v_actor, v_payload ->> 'job', coalesce((v_payload ->> 'limit')::int, 50))
    WHEN 'stuckMandates' THEN public.admin_stuck_mandates(v_actor)
    WHEN 'reviewQueue' THEN public.admin_review_queue(v_actor)
    WHEN 'markReviewed' THEN public.admin_mark_reviewed(v_actor, (v_payload ->> 'eventId')::bigint)
    WHEN 'exportUsers' THEN public.admin_export(v_actor, 'users', v_payload)
    WHEN 'exportPayments' THEN public.admin_export(v_actor, 'payments', v_payload)
    WHEN 'exportAudit' THEN public.admin_export(v_actor, 'audit', v_payload)
    WHEN 'tickets' THEN public.admin_tickets(v_actor, coalesce(v_payload ->> 'status', 'open'), v_payload ->> 'topic', (v_payload ->> 'unmatched')::boolean, coalesce((v_payload ->> 'limit')::int, 50), coalesce((v_payload ->> 'offset')::int, 0))
    WHEN 'ticket' THEN public.admin_ticket(v_actor, (v_payload ->> 'id')::bigint)
    WHEN 'replyTicket' THEN public.admin_ticket_action(v_actor, (v_payload ->> 'id')::bigint, 'reply', v_payload ->> 'body', NULL)
    WHEN 'setTicketStatus' THEN public.admin_ticket_action(v_actor, (v_payload ->> 'id')::bigint, 'setStatus', NULL, v_payload ->> 'status')
    WHEN 'addTicketNote' THEN public.admin_ticket_action(v_actor, (v_payload ->> 'id')::bigint, 'note', v_payload ->> 'body', NULL)
    WHEN 'deleteAccount' THEN public.admin_delete_user_account(v_actor, (v_payload ->> 'userId')::uuid, (v_payload ->> 'ticketId')::bigint)
    WHEN 'emails' THEN public.admin_emails(v_actor, v_payload ->> 'status', coalesce((v_payload ->> 'limit')::int, 50))
    WHEN 'resendEmail' THEN public.admin_email_action(v_actor, 'resend', v_payload)
    WHEN 'updateEmailTemplate' THEN public.admin_email_action(v_actor, 'updateTemplate', v_payload)
    WHEN 'updateGst' THEN public.admin_email_action(v_actor, 'updateGst', v_payload)
    WHEN 'receipt' THEN public.admin_receipt(v_actor, v_payload ->> 'txnId')
    WHEN 'siteContent' THEN public.admin_site_content(v_actor)
    WHEN 'publishSiteContent' THEN public.admin_publish_site_content(v_actor, v_payload ->> 'key', v_payload -> 'draft', coalesce((v_payload ->> 'publish')::boolean, false))
    WHEN 'runJob' THEN jsonb_build_object('error', 'edge_required', 'action', 'runJob')
    WHEN 'retryMandateCancel' THEN jsonb_build_object('error', 'edge_required', 'action', 'retryMandateCancel')
    WHEN 'readiness' THEN jsonb_build_object('error', 'edge_required', 'action', 'readiness')
    ELSE jsonb_build_object('error', 'unknown_action', 'action', p_action)
  END;
END;
$$;


--
-- PostgreSQL database dump complete
--



-- Grants for web Admin RPCs
REVOKE ALL ON FUNCTION public.admin_audit(uuid, integer, bigint, text, text, text, timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_audit(uuid, integer, bigint, text, text, text, timestamptz, timestamptz) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_billing_action(uuid, text, uuid, text, integer, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_billing_action(uuid, text, uuid, text, integer, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_delete_user_account(uuid, uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_delete_user_account(uuid, uuid, bigint) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_job_runs(uuid, text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_job_runs(uuid, text, integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_list_payments(uuid, text, text, integer, integer, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_list_payments(uuid, text, text, integer, integer, text, text, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_mark_activity_seen(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_mark_activity_seen(uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_mark_reviewed(uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_mark_reviewed(uuid, bigint) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_payment_events(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_payment_events(uuid, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_review_queue(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_review_queue(uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_stuck_mandates(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_stuck_mandates(uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_ticket(uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_ticket(uuid, bigint) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_ticket_action(uuid, bigint, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_ticket_action(uuid, bigint, text, text, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_tickets(uuid, text, text, boolean, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_tickets(uuid, text, text, boolean, integer, integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_trial_action(uuid, uuid, text, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_trial_action(uuid, uuid, text, timestamptz) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_usage(uuid, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_usage(uuid, integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_webhook_action(uuid, bigint, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_webhook_action(uuid, bigint, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_webhook_detail(uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_webhook_detail(uuid, bigint) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_webhooks(uuid, text, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_webhooks(uuid, text, integer, integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.admin_web_action(text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_web_action(text, jsonb) TO authenticated, service_role;
