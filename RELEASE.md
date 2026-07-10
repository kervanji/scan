# نشر Release على GitHub

المستودع: **https://github.com/kervanji/scan**

## الطريقة 1: GitHub Actions (موصى بها)

```bash
# 1. ارفع الكود
git add .
git commit -m "Prepare release v1.0.0"
git push origin main

# 2. أنشئ tag — يبني GitHub تلقائياً ويرفع Release
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions سيبني:
- `qr-attendance-1.0.0-win.zip` — للعملاء Windows
- `qr-attendance-1.0.0-mac-aarch64.zip` — لـ Mac
- يحدّث `updates/version.json` للتحديث التلقائي

---

## الطريقة 2: Docker (على جهازك)

```bash
cd /Users/mohammed/Projects/scan

# بناء نسخة Windows
docker compose build release-win
docker compose run --rm release-win

# أو مباشرة بدون Docker:
chmod +x scripts/build-release.sh
PLATFORM=win ./scripts/build-release.sh 1.0.0
```

الملف جاهز في:
```
release-output/qr-attendance-1.0.0-win.zip
```

ارفعه يدوياً: GitHub → Releases → New Release → Upload zip

---

## الطريقة 3: رفع يدوي

1. GitHub → **kervanji/scan** → **Releases** → **Draft a new release**
2. Tag: `v1.0.0`
3. ارفع: `release-output/qr-attendance-1.0.0-win.zip`
4. حدّث `updates/version.json`:
   - `version`: `1.0.0`
   - `downloadUrl`: رابط الـ zip من Release
   - `sha256`: من output البناء

---

## التحديث التلقائي (مُدمج في البرنامج)

| الإعداد | القيمة |
|---------|--------|
| manifest URL | `https://raw.githubusercontent.com/kervanji/scan/main/updates/version.json` |
| GitHub repo | `kervanji/scan` |

العملاء يفحصون تلقائياً عند التشغيل + كل 6 ساعات.

---

## للعميل (Windows)

1. حمّل `qr-attendance-1.0.0-win.zip`
2. فك الضغط
3. ثبّت Java 17 من https://adoptium.net
4. شغّل **Start Attendance.bat**

---

## إصدار جديد لاحقاً

```bash
# غيّر النسخة في pom.xml → 1.0.1
git commit -am "Bump version 1.0.1"
git tag v1.0.1
git push origin main --tags
```
