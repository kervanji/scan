# دليل التحديث عن بُعد (للبائع / المطوّر)

هذا النظام **لا يستخدم Git مباشرة** على جهاز العميل (لأن Git غير مثبت عند المستخدمين).  
بدلاً من ذلك: **أنت ترفع التحديث إلى GitHub عبر Git**، والتطبيق عند العميل **يحمّل التحديث تلقائيًا**.

## آلية العمل

```
أنت (المطوّر)                    GitHub                         جهاز العميل
     │                              │                                │
     │  git push + Release          │                                │
     ├─────────────────────────────►│                                │
     │                              │  version.json + JAR            │
     │                              │◄───────────────────────────────┤
     │                              │         فحص كل 6 ساعات         │
     │                              ├───────────────────────────────►│
     │                              │         تحميل + تثبيت          │
```

## إعداد المستودع (مرة واحدة)

### 1. أنشئ مستودع GitHub خاص

```bash
git init
git remote add origin https://github.com/YOUR_USER/qr-attendance.git
git add .
git commit -m "Initial release"
git push -u origin main
```

### 2. عدّل `updates/version.json`

```json
{
  "version": "1.0.0",
  "downloadUrl": "https://github.com/YOUR_USER/qr-attendance/releases/download/v1.0.0/qr-attendance-1.0.0.jar",
  "sha256": "",
  "releaseNotes": "النسخة الأولى",
  "mandatory": false
}
```

### 3. اضبط رابط التحديث في نسخة العميل

من **لوحة الإدارة → التحديثات**:

| الحقل | القيمة |
|-------|--------|
| رابط version.json | `https://raw.githubusercontent.com/YOUR_USER/qr-attendance/main/updates/version.json` |
| أو GitHub repo | `YOUR_USER/qr-attendance` |

> للمستودعات **الخاصة**: أضف GitHub Token في حقل Token.

---

## نشر تحديث جديد (كل مرة)

### الخطوة 1: زِد رقم النسخة

في `pom.xml`:

```xml
<version>1.0.1</version>
```

### الخطوة 2: ابنِ ملف JAR

```bash
mvn clean package -DskipTests
```

الملف: `target/qr-attendance-1.0.1.jar`

### الخطوة 3: احسب SHA256 (اختياري لكن موصى به)

```bash
shasum -a 256 target/qr-attendance-1.0.1.jar
```

### الخطوة 4: أنشئ GitHub Release

```bash
git add .
git commit -m "Release v1.0.1"
git tag v1.0.1
git push origin main --tags
```

ثم من GitHub → **Releases → New Release**:
- Tag: `v1.0.1`
- ارفع `qr-attendance-1.0.1.jar`

### الخطوة 5: حدّث `updates/version.json`

```json
{
  "version": "1.0.1",
  "downloadUrl": "https://github.com/YOUR_USER/qr-attendance/releases/download/v1.0.1/qr-attendance-1.0.1.jar",
  "sha256": "abc123...",
  "releaseNotes": "إصلاح الكاميرا وتحسينات",
  "mandatory": false
}
```

```bash
git add updates/version.json
git commit -m "Bump version.json to 1.0.1"
git push
```

**خلال ساعات** (أو فورًا عند إعادة تشغيل التطبيق) سيتحدّث جهاز العميل تلقائيًا.

---

## التحديث التلقائي الكامل (بدون سؤال العميل)

من **لوحة الإدارة → التحديثات**:
- ✅ فحص التحديثات تلقائيًا
- ✅ تثبيت التحديثات تلقائيًا

---

## تحديث إجباري

في `version.json`:

```json
"mandatory": true
```

سيُجبر المستخدم على التحديث قبل الاستمرار.

---

## GitHub Actions (اختياري)

عند دفع tag `v*` يبني GitHub Actions ملف JAR وينشر Release تلقائيًا.  
راجع `.github/workflows/release.yml`.

---

## ملاحظات للبيع

1. **لا تعطِ العميل Git** — فقط ملف التثبيت `.exe` أو `.jar`
2. **اضبط رابط التحديث** قبل تسليم الجهاز (أو ادمجه في البناء)
3. **استخدم مستودع خاص** + Token للحماية
4. **البيانات محلية** — التحديث لا يمس `~/AttendanceSystem/database/` ولا الصور
5. **اختبر التحديث** على جهاز تجريبي قبل إرساله للعملاء

## دمج الرابط في البناء (للتوزيع الجماعي)

عند البناء للعملاء:

```bash
mvn clean package -Dupdate.default.manifest.url=https://raw.githubusercontent.com/YOUR_USER/qr-attendance/main/updates/version.json
```

أو عدّل `src/main/resources/version.properties` قبل البناء.
