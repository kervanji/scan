# Murakib Attendance

نظام حضور وانصراف الموظفين عبر QR Code — تطبيق سطح مكتب مبني بـ JavaFX.

## المتطلبات

- Java 17+ (Java 21 موصى به للنشر)
- Maven 3.8+
- كاميرا ويب
- Windows 10/11 (للنشر) — يعمل أيضًا على macOS/Linux للتطوير

## التشغيل

```bash
cd /Users/mohammed/Projects/scan
mvn javafx:run
```

أو بعد البناء:

```bash
mvn clean package
java -jar target/qr-attendance-1.0.0.jar
```

## PIN الإدارة الافتراضي

```
1234
```

يُفضّل تغييره من **لوحة الإدارة → الإعدادات**.

## إعداد Telegram

1. أنشئ Bot عبر [@BotFather](https://t.me/BotFather) واحصل على `BOT_TOKEN`.
2. احصل على `CHAT_ID` (مجموعة أو محادثة).
3. أدخل القيم في **لوحة الإدارة → الإعدادات → اختبار Telegram**.

## هيكل البيانات

يُحفظ كل شيء تحت:

```
~/AttendanceSystem/
├── database/attendance.db
├── photos/YYYY-MM-DD/
├── backups/YYYY-MM-DD/
├── config/
└── logs/
```

## الميزات (MVP)

| الميزة | الحالة |
|--------|--------|
| إدارة الموظفين | ✅ |
| إنشاء QR لكل موظف | ✅ |
| قراءة QR بالكاميرا | ✅ |
| تسجيل دخول/خروج | ✅ |
| التقاط صورة لكل عملية | ✅ |
| SQLite محلي | ✅ |
| إشعارات Telegram + إعادة إرسال | ✅ |
| حضور اليوم / غائبون / متأخرون | ✅ |
| مراجعة صور العمليات | ✅ |
| تصدير Excel | ✅ |
| نسخة احتياطية تلقائية | ✅ |
| حماية لوحة الإدارة (PIN) | ✅ |
| سجل التدقيق | ✅ |
| الوضع التلقائي + Kiosk | ✅ |

## إنشاء Installer لـ Windows

```bash
mvn clean package
jpackage --input target \
  --name "Murakib Attendance" \
  --main-jar qr-attendance-1.0.0.jar \
  --main-class com.murakib.attendance.Main \
  --type exe \
  --win-menu \
  --win-shortcut
```

## التقنيات

- Java 17 + JavaFX
- ZXing (QR)
- OpenCV / JavaCV (الكاميرا — يدعم Apple Silicon و Windows)
- SQLite
- Apache POI (Excel)
- Telegram Bot API

## التحديث التلقائي عن بُعد

راجع **[UPDATE_GUIDE_AR.md](UPDATE_GUIDE_AR.md)** و **[RELEASE.md](RELEASE.md)**.

المستودع: `kervanji/scan` — التحديث التلقائي مُدمج افتراضياً.

## ملاحظات macOS

عند التشغيل من Terminal/Cursor لأول مرة، قد يطلب macOS صلاحية الكاميرا:

1. **إعدادات النظام → الخصوصية والأمان → الكاميرا**
2. فعّل **Terminal** أو **Cursor**
3. أعد تشغيل: `mvn javafx:run`

## ملاحظات

- الإنترنت مطلوب فقط لـ Telegram؛ الحضور يُحفظ محليًا دائمًا.
- `scan_cooldown_seconds` يمنع تكرار المسح (افتراضي: 30 ثانية).
- في **الوضع التلقائي** يحدد النظام دخول/خروج حسب آخر عملية للموظف.
