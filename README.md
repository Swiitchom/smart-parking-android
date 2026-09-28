# Smart Parking Android

Package: `om.switch.smartparking`

## جاهز الآن
- Android + Jetpack Compose
- Firebase config مضاف
- Realtime Database dependency
- GitHub Action لبناء APK
- GitHub Action لتوزيع APK عبر Firebase App Distribution

## Firebase Realtime Database
`https://com-example-aswitch-743a8-default-rtdb.firebaseio.com/`

## GitHub Actions
- `.github/workflows/build-apk.yml` يبني APK تلقائيًا على main.
- `.github/workflows/firebase-distribution.yml` جاهز للتوزيع بعد إضافة Secrets.

## App Distribution Secrets
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_TESTERS`

## Security
قاعدة البيانات النهائية موجودة في `firebase/database.rules.json` ومقفلة افتراضيًا.
يوجد ملف تطوير منفصل `firebase/database.rules.DEVELOPMENT.json` للاختبار فقط.

## ESP32
المرحلة التالية: ربط نفس منطق RC522 + IR + LEDs + Buzzer مع Firebase بدل WebServer المحلي.
