#!/usr/bin/env python3
"""Smoke: create throwaway user → claim-trial → expect welcome outbox sent.

Email/password sign-in is disabled on GoTrue, so the access token is minted with
SERVICE_PASSWORD_JWT (same secret GoTrue uses) after admin createUser.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def load_env(path: str) -> dict[str, str]:
    env: dict[str, str] = {}
    for raw in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        v = v.strip().strip('"').strip("'")
        env[k.strip()] = v
    return env


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def mint_user_jwt(secret: str, user_id: str, email: str) -> str:
    now = int(time.time())
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = b64url(
        json.dumps(
            {
                "aud": "authenticated",
                "role": "authenticated",
                "sub": user_id,
                "email": email,
                "iat": now,
                "exp": now + 3600,
            },
            separators=(",", ":"),
        ).encode()
    )
    sig = hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    return f"{header}.{payload}.{b64url(sig)}"


def req(method: str, url: str, headers: dict[str, str], body: dict | None = None) -> tuple[int, dict | str]:
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60) as res:
            raw = res.read().decode()
            try:
                return res.status, json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                return res.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return e.code, raw


def main() -> int:
    env = load_env("/opt/mp3studio-postgres/.env")
    base = env.get("API_EXTERNAL_URL", "https://mp3studio-api.3.232.162.36.sslip.io").rstrip("/")
    service = env["SERVICE_SUPABASESERVICE_KEY"]
    anon = env["SERVICE_SUPABASEANON_KEY"]
    jwt_secret = env["SERVICE_PASSWORD_JWT"]
    stamp = time.strftime("%Y%m%d%H%M%S")
    # Unique address so trial_claims is new (non-Gmail +aliases still normalize).
    # Delivered via SendGrid; check outbox + SendGrid activity for the smoke.
    email = f"welcome.smoke.{stamp}@autoomstudio.com"
    password = secrets.token_urlsafe(24)
    device_id = hashlib.sha256(f"smoke-{stamp}".encode()).hexdigest()

    print("email", email)
    print("device", device_id[:12] + "...")

    # 1) Create confirmed user (admin) — triggers handle_new_user → profile
    code, created = req(
        "POST",
        f"{base}/auth/v1/admin/users",
        {
            "apikey": service,
            "Authorization": f"Bearer {service}",
            "Content-Type": "application/json",
        },
        {
            "email": email,
            "password": password,
            "email_confirm": True,
            "user_metadata": {"full_name": "Chinmay Nayak", "name": "Chinmay Nayak"},
        },
    )
    print("create_user", code, created if isinstance(created, dict) else str(created)[:300])
    if code not in (200, 201) or not isinstance(created, dict) or "id" not in created:
        return 1
    user_id = created["id"]
    access = mint_user_jwt(jwt_secret, user_id, email)
    print("minted_jwt_for", user_id)

    time.sleep(1.5)

    # 2) claim-trial (same Edge path first sign-in / devices register uses)
    code, claim = req(
        "POST",
        f"{base}/functions/v1/claim-trial",
        {
            "apikey": anon,
            "Authorization": f"Bearer {access}",
            "Content-Type": "application/json",
        },
        {"deviceId": device_id},
    )
    print("claim_trial", code, claim)
    if code != 200 or not isinstance(claim, dict) or claim.get("result") != "granted":
        print("FAIL: expected granted")
    else:
        print("OK: trial granted")

    time.sleep(3)

    # 3) Outbox row
    code, outbox = req(
        "GET",
        f"{base}/rest/v1/email_outbox?template_key=eq.welcome&user_id=eq.{user_id}&select=id,status,to_email,error,provider_id,dedupe_key,sent_at&order=created_at.desc",
        {
            "apikey": service,
            "Authorization": f"Bearer {service}",
        },
    )
    print("outbox", code, outbox)

    ok = (
        code == 200
        and isinstance(outbox, list)
        and len(outbox) >= 1
        and outbox[0].get("status") == "sent"
        and outbox[0].get("to_email") == email
    )
    if ok:
        print("PASS: welcome email sent")
    else:
        print("FAIL: welcome email not sent/sent failed")
        os.system("docker logs mp3studio-functions --tail 50 2>&1 | tail -50")

    # Keep the auth user so you can see the inbox delivery; delete only on PASS if CLEANUP=1
    if os.environ.get("CLEANUP") == "1":
        del_code, deleted = req(
            "DELETE",
            f"{base}/auth/v1/admin/users/{user_id}",
            {
                "apikey": service,
                "Authorization": f"Bearer {service}",
            },
        )
        print("cleanup_user", del_code, deleted if deleted else "ok")
    else:
        print("kept_user", user_id, "(set CLEANUP=1 to delete)")

    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
