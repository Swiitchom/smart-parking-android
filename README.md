# Smart Parking Android V3

نسخة احترافية متكاملة للمواقف الذكية.

## المميزات
- Dashboard مباشر لـ P1 / P2 / P3
- حجز P3 من التطبيق
- تنبيهات مباشرة
- سجل أحداث Firebase
- عربي / English
- Dark Mode
- أيقونة تطبيق Adaptive
- Firebase Realtime Database
- ESP32 + 3× RC522 + IR + LEDs + Buzzer
- GitHub Actions يبني APK تلقائيًا

## Firebase applicationId
`om.switch.smartparking`

## Android namespace
`om.swiitch.smartparking`

## ESP32
الكود الكامل:
`esp32/smart_parking_firebase.ino`

غيّر فقط:
- WIFI_NAME
- WIFI_PASSWORD

بقية GPIOs والتوصيلات محفوظة كما كانت.

## Firebase paths
- `parking/P1`
- `parking/P2`
- `parking/P3`
- `booking/P3`
- `system`
- `events`
