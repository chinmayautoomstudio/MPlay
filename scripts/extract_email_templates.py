from pathlib import Path
import re

text = Path("supabase/migrations/20261114120000_email_notifications.sql").read_text(encoding="utf-8")
pat = re.compile(
    r"\(\s*'([^']+)',\s*'((?:\\'|[^'])*)',\s*\$html\$(.*?)\$html\$,",
    re.S,
)
out_dir = Path("scripts/email_preview")
out_dir.mkdir(exist_ok=True)
seen: set[str] = set()
for m in pat.finditer(text):
    key, subject, html = m.group(1), m.group(2), m.group(3)
    if key in seen:
        continue
    seen.add(key)
    path = out_dir / f"{key}.html"
    path.write_text(html.strip() + "\n", encoding="utf-8", newline="\n")
    print(f"{key}\t{subject}\t{len(html)}\t{path}")
print("count", len(seen))
