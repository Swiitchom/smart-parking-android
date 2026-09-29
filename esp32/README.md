# School Oman Smart Parking — ESP32

الملف النهائي المعتمد:

- `smart_parking_firebase.ino`

## الوظائف

- 3 قارئات RC522 للمواقف P1 / P2 / P3
- 3 حساسات IR لاكتشاف وجود المركبة
- LED أخضر / أحمر لكل موقف
- Buzzer للتنبيه
- LCD 16x2 I2C على العنوان `0x27`
- ISD1820 لتشغيل الرسالة الصوتية المسجلة
- Firebase Realtime Database
- تطبيق Android مرتبط بنفس بيانات المواقف والحجز

## التوصيلات الأساسية

### RC522 / SPI
- SCK: GPIO18
- MISO: GPIO19
- MOSI: GPIO23
- P1 SS: GPIO16
- P2 SS: GPIO17
- P3 SS: GPIO27
- RST: GPIO5

### IR
- P1: GPIO32
- P2: GPIO33
- P3: GPIO34

### LEDs
- P1 Green: GPIO25
- P1 Red: GPIO26
- P2 Green: GPIO12
- P2 Red: GPIO13
- P3 Green: GPIO14
- P3 Red: GPIO4

### Buzzer
- GPIO15

### LCD 16x2 I2C
- SDA: GPIO21
- SCL: GPIO22
- Address: 0x27

### ISD1820
- PLAYE: GPIO2
- VCC/GND: من التغذية المناسبة مع أرضي مشترك مع ESP32
- السماعة موصولة مباشرة بمخارج SP+/SP-

## سلوك التنبيه

عند دخول موقف في حالة غير مصرح بها:
1. LED الأحمر يومض.
2. Buzzer يعمل.
3. LCD تعرض رقم الموقف والتنبيه وتومض الإضاءة الخلفية.
4. ISD1820 يشغل الرسالة الصوتية المسجلة مرة واحدة عند بداية التنبيه.
5. الحدث يرسل إلى Firebase ويظهر في التطبيق.

عند زوال التنبيه يرجع LCD إلى شاشة النظام الطبيعية.

> بيانات Wi-Fi تظل محلية في نسخة الجهاز ولا تُحفظ علنًا في المستودع.
