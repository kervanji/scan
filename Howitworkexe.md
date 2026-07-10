# توزيع البرنامج للمستهلكين عبر GitHub

## رابط التحميل للعملاء

```
https://github.com/kervanji/scan/releases/latest
```

العميل يحمّل `qr-attendance-1.0.0-win.zip` (أو أحدث نسخة)، يفك الضغط، ويشغّل **Murakib Attendance.exe**.

---

## ماذا يحدث عند العميل؟

1. يفتح الرابط أعلاه من المتصفح
2. يحمّل ملف `.zip` لنظام Windows
3. يفك الضغط في أي مجلد
4. ينقر مرتين على `Murakib Attendance.exe`
5. البرنامج يعمل — البيانات تُحفظ في `C:\Users\<اسم المستخدم>\AttendanceSystem\`

التحديث التلقائي مُفعّل افتراضياً: عند صدور نسخة جديدة على GitHub، يُخطر العميل ويستطيع التحديث من داخل البرنامج.

---

## كيف تنشر نسخة جديدة (أنت كمطوّر)

### 1. ابنِ النسخة محلياً (اختياري للتجربة)

```powershell
mvn clean package -Prelease -DskipTests
```

الملفات في `target\`:
- `Murakib Attendance.exe`
- `qr-attendance-1.0.0.jar`
- `lib\` (المكتبات)

### 2. ارفع التغييرات إلى GitHub

```powershell
git add .
git commit -m "Prepare release v1.0.1"
git push origin main
```

### 3. أنشئ Tag — GitHub يبني ويرفع Release تلقائياً

```powershell
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions سيبني تلقائياً:
- `qr-attendance-1.0.1-win.zip` (يحتوي EXE + JAR + lib + bat)
- `qr-attendance-1.0.1-mac-aarch64.zip`
- يحدّث `updates/version.json` للتحديث التلقائي

### 4. تحقق من النشر

```powershell
gh release view v1.0.1 --repo kervanji/scan
```

أو افتح: https://github.com/kervanji/scan/releases

---

## محتويات ملف zip للعميل (Windows)

```
qr-attendance-1.0.0-win/
├── Murakib Attendance.exe    ← التشغيل المباشر
├── Start Attendance.bat      ← بديل (يتطلب Java مثبت)
├── qr-attendance-1.0.0.jar
├── lib/                      ← مكتبات JavaFX وغيرها
└── README.txt
```

---

## إصدار جديد لاحقاً

1. غيّر `<version>` في `pom.xml` (مثلاً `1.0.2`)
2. حدّث رقم النسخة في `qr-attendance(exe).xml` إن استخدمت Launch4j GUI
3. `git commit` → `git tag v1.0.2` → `git push origin v1.0.2`

---

## روابط مهمة

| الغرض | الرابط |
|-------|--------|
| تحميل أحدث نسخة | https://github.com/kervanji/scan/releases/latest |
| ملف التحديث التلقائي | https://raw.githubusercontent.com/kervanji/scan/main/updates/version.json |
| المستودع | https://github.com/kervanji/scan |
