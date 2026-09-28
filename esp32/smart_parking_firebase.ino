#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <SPI.h>
#include <MFRC522.h>

// ======================================================
// WIFI - غيّر هذين السطرين فقط
// ======================================================
const char* WIFI_NAME = "اسم_الواي_فاي";
const char* WIFI_PASSWORD = "كلمة_مرور_الواي_فاي";

// ======================================================
// FIREBASE
// ======================================================
const char* FIREBASE_URL =
"https://com-example-aswitch-743a8-default-rtdb.firebaseio.com";

// ======================================================
// التوصيلات الحالية - بدون تغيير
// ======================================================
#define SCK_PIN   18
#define MISO_PIN  19
#define MOSI_PIN  23

#define SS_P1 16
#define SS_P2 17
#define SS_P3 27
#define RST_PIN 5

#define IR_P1 32
#define IR_P2 33
#define IR_P3 34
#define IR_ACTIVE LOW

#define GREEN_P1 25
#define RED_P1   26
#define GREEN_P2 12
#define RED_P2   13
#define GREEN_P3 14
#define RED_P3   4

#define BUZZER_PIN 15

MFRC522 rfid1(SS_P1, RST_PIN);
MFRC522 rfid2(SS_P2, RST_PIN);
MFRC522 rfid3(SS_P3, RST_PIN);

// ======================================================
// UIDs
// ======================================================
String BUS1_UID = "2E A9 4F 06";
String BUS2_UID = "B3 E5 92 56";

// ======================================================
// P3 BOOKING FROM FIREBASE
// ======================================================
bool p3Booked = false;
String P3_UID = "";
String P3_NAME = "";
String P3_PLATE = "";

unsigned long lastBookingRead = 0;
const unsigned long BOOKING_READ_INTERVAL = 2500;

// ======================================================
// STATES
// ======================================================
enum ParkingState {
  FREE,
  AUTHORIZED_WAIT,
  OCCUPIED,
  WRONG_ALERT
};

ParkingState p1State = FREE;
ParkingState p2State = FREE;
ParkingState p3State = FREE;

// ======================================================
// TIMERS
// ======================================================
const unsigned long ENTRY_TIME = 15000;
const unsigned long WRONG_TIMEOUT = 5000;
const unsigned long IR_CONFIRM_TIME = 700;

unsigned long p1EntryTimer = 0;
unsigned long p2EntryTimer = 0;
unsigned long p3EntryTimer = 0;

unsigned long p1WrongStart = 0;
unsigned long p2WrongStart = 0;
unsigned long p3WrongStart = 0;

bool p1WrongSawCar = false;
bool p2WrongSawCar = false;
bool p3WrongSawCar = false;

// ======================================================
// IR FILTER
// ======================================================
bool rawIR1 = false;
bool rawIR2 = false;
bool rawIR3 = false;

bool stableIR1 = false;
bool stableIR2 = false;
bool stableIR3 = false;

unsigned long irTime1 = 0;
unsigned long irTime2 = 0;
unsigned long irTime3 = 0;

// ======================================================
// OUTPUT / SYNC
// ======================================================
unsigned long lastBlink = 0;
bool blinkState = false;

unsigned long lastFirebaseSync = 0;
const unsigned long FIREBASE_INTERVAL = 2000;

unsigned long lastWiFiCheck = 0;
String lastEvent = "System Started";

bool eventPending = false;
String pendingEventMessage = "";
String pendingEventType = "info";
String pendingEventParking = "";

// ======================================================
// HELPERS
// ======================================================
bool readIR(int pin) {
  return digitalRead(pin) == IR_ACTIVE;
}

String normalizeUID(String uid) {
  uid.trim();
  uid.toUpperCase();
  uid.replace(":", " ");
  uid.replace("-", " ");
  while (uid.indexOf("  ") >= 0) uid.replace("  ", " ");
  return uid;
}

String getUID(MFRC522 &reader) {
  String uid = "";

  for (byte i = 0; i < reader.uid.size; i++) {
    if (reader.uid.uidByte[i] < 0x10) uid += "0";
    uid += String(reader.uid.uidByte[i], HEX);
    if (i < reader.uid.size - 1) uid += " ";
  }

  uid.toUpperCase();
  return uid;
}

String jsonEscape(String text) {
  text.replace("\\", "\\\\");
  text.replace("\"", "\\\"");
  text.replace("\n", " ");
  return text;
}

String jsonStringValue(const String &json, const String &key) {
  String token = "\"" + key + "\"";
  int p = json.indexOf(token);
  if (p < 0) return "";

  p = json.indexOf(':', p + token.length());
  if (p < 0) return "";

  p++;
  while (p < json.length() && (json[p] == ' ' || json[p] == '\n' || json[p] == '\r')) p++;
  if (p >= json.length() || json[p] != '"') return "";

  p++;
  String out = "";
  bool escaped = false;

  for (; p < json.length(); p++) {
    char c = json[p];

    if (escaped) {
      if (c == 'n') out += '\n';
      else if (c == 'r') out += '\r';
      else if (c == 't') out += '\t';
      else out += c;
      escaped = false;
    } else if (c == '\\') {
      escaped = true;
    } else if (c == '"') {
      break;
    } else {
      out += c;
    }
  }

  return out;
}

bool jsonBoolValue(const String &json, const String &key, bool defaultValue = false) {
  String token = "\"" + key + "\"";
  int p = json.indexOf(token);
  if (p < 0) return defaultValue;

  p = json.indexOf(':', p + token.length());
  if (p < 0) return defaultValue;

  String tail = json.substring(p + 1);
  tail.trim();

  if (tail.startsWith("true")) return true;
  if (tail.startsWith("false")) return false;
  return defaultValue;
}

void setEvent(String message, String type, String parking) {
  lastEvent = message;
  pendingEventMessage = message;
  pendingEventType = type;
  pendingEventParking = parking;
  eventPending = true;

  Serial.println();
  Serial.print("EVENT: ");
  Serial.println(message);
}

// ======================================================
// WIFI
// ======================================================
void connectWiFi() {
  Serial.println();
  Serial.println("============================");
  Serial.println("CONNECTING WIFI");
  Serial.println("============================");

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_NAME, WIFI_PASSWORD);

  int counter = 0;

  while (WiFi.status() != WL_CONNECTED && counter < 40) {
    delay(500);
    Serial.print(".");
    counter++;
  }

  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("WiFi Connected");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("WiFi Connection Failed");
  }
}

void keepWiFiConnected() {
  if (millis() - lastWiFiCheck < 10000) return;
  lastWiFiCheck = millis();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi Lost - Reconnecting...");
    WiFi.disconnect();
    WiFi.begin(WIFI_NAME, WIFI_PASSWORD);
  }
}

// ======================================================
// FIREBASE HTTP
// ======================================================
int firebaseRequest(
  String method,
  String path,
  String body,
  String &response
) {
  if (WiFi.status() != WL_CONNECTED) return -1;

  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient https;
  String url = String(FIREBASE_URL) + path + ".json";

  if (!https.begin(client, url)) return -2;

  https.addHeader("Content-Type", "application/json");

  int code = -3;

  if (method == "GET") {
    code = https.GET();
  } else if (method == "PATCH") {
    code = https.PATCH(body);
  } else if (method == "POST") {
    code = https.POST(body);
  } else if (method == "PUT") {
    code = https.PUT(body);
  }

  response = https.getString();
  https.end();

  return code;
}

// ======================================================
// READ P3 BOOKING
// ======================================================
void readP3Booking() {
  if (millis() - lastBookingRead < BOOKING_READ_INTERVAL) return;
  lastBookingRead = millis();

  String response;
  int code = firebaseRequest(
    "GET",
    "/booking/P3",
    "",
    response
  );

  if (code < 200 || code >= 300) {
    if (code > 0) {
      Serial.print("Booking read error: ");
      Serial.println(code);
    }
    return;
  }

  if (response == "null" || response.length() == 0) {
    p3Booked = false;
    P3_UID = "";
    P3_NAME = "";
    P3_PLATE = "";
    return;
  }

  bool newBooked = jsonBoolValue(response, "active", false);
  String newUid = normalizeUID(jsonStringValue(response, "uid"));
  String newName = jsonStringValue(response, "name");
  String newPlate = jsonStringValue(response, "plate");

  bool changed =
    newBooked != p3Booked ||
    newUid != P3_UID ||
    newName != P3_NAME ||
    newPlate != P3_PLATE;

  p3Booked = newBooked;
  P3_UID = newUid;
  P3_NAME = newName;
  P3_PLATE = newPlate;

  if (!p3Booked && p3State == AUTHORIZED_WAIT) {
    p3State = FREE;
  }

  if (changed) {
    Serial.println();
    Serial.println("P3 BOOKING UPDATED");
    Serial.print("Active: ");
    Serial.println(p3Booked ? "YES" : "NO");
    Serial.print("Name: ");
    Serial.println(P3_NAME);
    Serial.print("Plate: ");
    Serial.println(P3_PLATE);
    Serial.print("UID: ");
    Serial.println(P3_UID);
  }
}

// ======================================================
// IR FILTER
// ======================================================
void updateIRFilters() {
  unsigned long now = millis();

  bool r1 = readIR(IR_P1);
  bool r2 = readIR(IR_P2);
  bool r3 = readIR(IR_P3);

  if (r1 != rawIR1) {
    rawIR1 = r1;
    irTime1 = now;
  }

  if (rawIR1 != stableIR1 && now - irTime1 >= IR_CONFIRM_TIME) {
    stableIR1 = rawIR1;
    Serial.print("P1 IR -> ");
    Serial.println(stableIR1 ? "OCCUPIED" : "FREE");
  }

  if (r2 != rawIR2) {
    rawIR2 = r2;
    irTime2 = now;
  }

  if (rawIR2 != stableIR2 && now - irTime2 >= IR_CONFIRM_TIME) {
    stableIR2 = rawIR2;
    Serial.print("P2 IR -> ");
    Serial.println(stableIR2 ? "OCCUPIED" : "FREE");
  }

  if (r3 != rawIR3) {
    rawIR3 = r3;
    irTime3 = now;
  }

  if (rawIR3 != stableIR3 && now - irTime3 >= IR_CONFIRM_TIME) {
    stableIR3 = rawIR3;
    Serial.print("P3 IR -> ");
    Serial.println(stableIR3 ? "OCCUPIED" : "FREE");
  }
}

// ======================================================
// RFID P1
// ======================================================
void checkP1() {
  if (!rfid1.PICC_IsNewCardPresent()) return;
  if (!rfid1.PICC_ReadCardSerial()) return;

  String uid = getUID(rfid1);

  Serial.println();
  Serial.print("P1 CARD: ");
  Serial.println(uid);

  if (uid == BUS1_UID) {
    p1State = AUTHORIZED_WAIT;
    p1EntryTimer = millis();
    p1WrongSawCar = false;
    setEvent("P1 Bus 1 Accepted", "entry", "P1");
  } else {
    p1State = WRONG_ALERT;
    p1WrongSawCar = false;
    p1WrongStart = millis();

    if (uid == BUS2_UID) setEvent("P1 Wrong Bus", "alert", "P1");
    else setEvent("P1 Unknown Card", "alert", "P1");
  }

  rfid1.PICC_HaltA();
  rfid1.PCD_StopCrypto1();
}

// ======================================================
// RFID P2
// ======================================================
void checkP2() {
  if (!rfid2.PICC_IsNewCardPresent()) return;
  if (!rfid2.PICC_ReadCardSerial()) return;

  String uid = getUID(rfid2);

  Serial.println();
  Serial.print("P2 CARD: ");
  Serial.println(uid);

  if (uid == BUS2_UID) {
    p2State = AUTHORIZED_WAIT;
    p2EntryTimer = millis();
    p2WrongSawCar = false;
    setEvent("P2 Bus 2 Accepted", "entry", "P2");
  } else {
    p2State = WRONG_ALERT;
    p2WrongSawCar = false;
    p2WrongStart = millis();

    if (uid == BUS1_UID) setEvent("P2 Wrong Bus", "alert", "P2");
    else setEvent("P2 Unknown Card", "alert", "P2");
  }

  rfid2.PICC_HaltA();
  rfid2.PCD_StopCrypto1();
}

// ======================================================
// RFID P3
// ======================================================
void checkP3() {
  if (!rfid3.PICC_IsNewCardPresent()) return;
  if (!rfid3.PICC_ReadCardSerial()) return;

  String uid = getUID(rfid3);

  Serial.println();
  Serial.print("P3 CARD: ");
  Serial.println(uid);

  if (p3Booked && P3_UID.length() > 0 && uid == P3_UID) {
    p3State = AUTHORIZED_WAIT;
    p3EntryTimer = millis();
    p3WrongSawCar = false;

    setEvent("P3 Booking Accepted", "entry", "P3");

    Serial.print("NAME: ");
    Serial.println(P3_NAME);
    Serial.print("PLATE: ");
    Serial.println(P3_PLATE);
  } else {
    p3State = WRONG_ALERT;
    p3WrongSawCar = false;
    p3WrongStart = millis();

    if (!p3Booked) setEvent("P3 No Booking", "alert", "P3");
    else setEvent("P3 Wrong Card", "alert", "P3");
  }

  rfid3.PICC_HaltA();
  rfid3.PCD_StopCrypto1();
}

// ======================================================
// PARKING LOGIC
// ======================================================
void updateParkingLogic() {
  unsigned long now = millis();

  if (p1State == AUTHORIZED_WAIT) {
    if (stableIR1) {
      p1State = OCCUPIED;
      setEvent("P1 Bus 1 Parked", "entry", "P1");
    } else if (now - p1EntryTimer >= ENTRY_TIME) {
      p1State = FREE;
      setEvent("P1 Permission Expired", "info", "P1");
    }
  } else if (p1State == OCCUPIED) {
    if (!stableIR1) {
      p1State = FREE;
      setEvent("P1 Vehicle Left", "exit", "P1");
    }
  } else if (p1State == WRONG_ALERT) {
    if (stableIR1) p1WrongSawCar = true;

    if (p1WrongSawCar && !stableIR1) {
      p1State = FREE;
      p1WrongSawCar = false;
      setEvent("P1 Wrong Vehicle Left", "exit", "P1");
    } else if (!p1WrongSawCar && now - p1WrongStart >= WRONG_TIMEOUT) {
      p1State = FREE;
    }
  }

  if (p2State == AUTHORIZED_WAIT) {
    if (stableIR2) {
      p2State = OCCUPIED;
      setEvent("P2 Bus 2 Parked", "entry", "P2");
    } else if (now - p2EntryTimer >= ENTRY_TIME) {
      p2State = FREE;
      setEvent("P2 Permission Expired", "info", "P2");
    }
  } else if (p2State == OCCUPIED) {
    if (!stableIR2) {
      p2State = FREE;
      setEvent("P2 Vehicle Left", "exit", "P2");
    }
  } else if (p2State == WRONG_ALERT) {
    if (stableIR2) p2WrongSawCar = true;

    if (p2WrongSawCar && !stableIR2) {
      p2State = FREE;
      p2WrongSawCar = false;
      setEvent("P2 Wrong Vehicle Left", "exit", "P2");
    } else if (!p2WrongSawCar && now - p2WrongStart >= WRONG_TIMEOUT) {
      p2State = FREE;
    }
  }

  if (p3State == AUTHORIZED_WAIT) {
    if (stableIR3) {
      p3State = OCCUPIED;
      setEvent("P3 Booked Vehicle Parked", "entry", "P3");
    } else if (now - p3EntryTimer >= ENTRY_TIME) {
      p3State = FREE;
      setEvent("P3 Permission Expired", "info", "P3");
    }
  } else if (p3State == OCCUPIED) {
    if (!stableIR3) {
      p3State = FREE;
      setEvent("P3 Vehicle Left", "exit", "P3");
    }
  } else if (p3State == WRONG_ALERT) {
    if (stableIR3) p3WrongSawCar = true;

    if (p3WrongSawCar && !stableIR3) {
      p3State = FREE;
      p3WrongSawCar = false;
      setEvent("P3 Wrong Vehicle Left", "exit", "P3");
    } else if (!p3WrongSawCar && now - p3WrongStart >= WRONG_TIMEOUT) {
      p3State = FREE;
    }
  }
}

// ======================================================
// OUTPUTS
// ======================================================
void updateOutputs() {
  if (millis() - lastBlink >= 250) {
    lastBlink = millis();
    blinkState = !blinkState;
  }

  bool buzzer = false;

  if (p1State == WRONG_ALERT) {
    digitalWrite(GREEN_P1, LOW);
    digitalWrite(RED_P1, blinkState);
    if (blinkState) buzzer = true;
  } else if (stableIR1) {
    digitalWrite(GREEN_P1, LOW);
    digitalWrite(RED_P1, HIGH);
  } else {
    digitalWrite(GREEN_P1, HIGH);
    digitalWrite(RED_P1, LOW);
  }

  if (p2State == WRONG_ALERT) {
    digitalWrite(GREEN_P2, LOW);
    digitalWrite(RED_P2, blinkState);
    if (blinkState) buzzer = true;
  } else if (stableIR2) {
    digitalWrite(GREEN_P2, LOW);
    digitalWrite(RED_P2, HIGH);
  } else {
    digitalWrite(GREEN_P2, HIGH);
    digitalWrite(RED_P2, LOW);
  }

  if (p3State == WRONG_ALERT) {
    digitalWrite(GREEN_P3, LOW);
    digitalWrite(RED_P3, blinkState);
    if (blinkState) buzzer = true;
  } else if (stableIR3) {
    digitalWrite(GREEN_P3, LOW);
    digitalWrite(RED_P3, HIGH);
  } else {
    digitalWrite(GREEN_P3, HIGH);
    digitalWrite(RED_P3, LOW);
  }

  digitalWrite(BUZZER_PIN, buzzer ? HIGH : LOW);
}

// ======================================================
// STATUS
// ======================================================
String getP1Status() {
  if (p1State == WRONG_ALERT) return "تنبيه - بطاقة خاطئة";
  if (stableIR1) return "مشغول";
  if (p1State == AUTHORIZED_WAIT) return "تم قبول باص الأول";
  return "متاح";
}

String getP2Status() {
  if (p2State == WRONG_ALERT) return "تنبيه - بطاقة خاطئة";
  if (stableIR2) return "مشغول";
  if (p2State == AUTHORIZED_WAIT) return "تم قبول باص الثاني";
  return "متاح";
}

String getP3Status() {
  if (p3State == WRONG_ALERT) return "تنبيه - بطاقة غير مصرح بها";
  if (stableIR3) return "مشغول";
  if (p3State == AUTHORIZED_WAIT) return "تم قبول الحجز";
  if (p3Booked) return "محجوز";
  return "متاح للحجز";
}

// ======================================================
// SEND EVENT TO FIREBASE
// ======================================================
void sendPendingEvent() {
  if (!eventPending) return;
  if (WiFi.status() != WL_CONNECTED) return;

  String body = "{";
  body += "\"message\":\"" + jsonEscape(pendingEventMessage) + "\",";
  body += "\"type\":\"" + jsonEscape(pendingEventType) + "\",";
  body += "\"parking\":\"" + jsonEscape(pendingEventParking) + "\",";
  body += "\"timestamp\":{\".sv\":\"timestamp\"}";
  body += "}";

  String response;
  int code = firebaseRequest(
    "POST",
    "/events",
    body,
    response
  );

  if (code >= 200 && code < 300) {
    eventPending = false;
  }
}

// ======================================================
// SYNC PARKING TO FIREBASE
// ======================================================
void syncFirebase() {
  if (WiFi.status() != WL_CONNECTED) return;
  if (millis() - lastFirebaseSync < FIREBASE_INTERVAL) return;

  lastFirebaseSync = millis();

  String json = "{";

  json += "\"parking\":{";

  json += "\"P1\":{";
  json += "\"status\":\"" + jsonEscape(getP1Status()) + "\",";
  json += "\"occupied\":" + String(stableIR1 ? "true" : "false") + ",";
  json += "\"alert\":" + String(p1State == WRONG_ALERT ? "true" : "false");
  json += "},";

  json += "\"P2\":{";
  json += "\"status\":\"" + jsonEscape(getP2Status()) + "\",";
  json += "\"occupied\":" + String(stableIR2 ? "true" : "false") + ",";
  json += "\"alert\":" + String(p2State == WRONG_ALERT ? "true" : "false");
  json += "},";

  json += "\"P3\":{";
  json += "\"status\":\"" + jsonEscape(getP3Status()) + "\",";
  json += "\"occupied\":" + String(stableIR3 ? "true" : "false") + ",";
  json += "\"alert\":" + String(p3State == WRONG_ALERT ? "true" : "false");
  json += "}";

  json += "},";

  json += "\"system\":{";
  json += "\"online\":true,";
  json += "\"lastEvent\":\"" + jsonEscape(lastEvent) + "\",";
  json += "\"lastSeen\":{\".sv\":\"timestamp\"}";
  json += "}";

  json += "}";

  String response;
  int code = firebaseRequest(
    "PATCH",
    "/",
    json,
    response
  );

  if (code >= 200 && code < 300) {
    Serial.println();
    Serial.println("Firebase Sync SUCCESS");
    Serial.print("P1: ");
    Serial.println(getP1Status());
    Serial.print("P2: ");
    Serial.println(getP2Status());
    Serial.print("P3: ");
    Serial.println(getP3Status());
  } else {
    Serial.print("Firebase Sync ERROR: ");
    Serial.println(code);
  }
}

// ======================================================
// SETUP
// ======================================================
void setup() {
  Serial.begin(115200);
  delay(1200);

  Serial.println();
  Serial.println("============================");
  Serial.println(" SMART PARKING + FIREBASE");
  Serial.println("============================");

  pinMode(SS_P1, OUTPUT);
  pinMode(SS_P2, OUTPUT);
  pinMode(SS_P3, OUTPUT);

  digitalWrite(SS_P1, HIGH);
  digitalWrite(SS_P2, HIGH);
  digitalWrite(SS_P3, HIGH);

  pinMode(IR_P1, INPUT);
  pinMode(IR_P2, INPUT);
  pinMode(IR_P3, INPUT);

  rawIR1 = readIR(IR_P1);
  rawIR2 = readIR(IR_P2);
  rawIR3 = readIR(IR_P3);

  stableIR1 = rawIR1;
  stableIR2 = rawIR2;
  stableIR3 = rawIR3;

  pinMode(GREEN_P1, OUTPUT);
  pinMode(RED_P1, OUTPUT);
  pinMode(GREEN_P2, OUTPUT);
  pinMode(RED_P2, OUTPUT);
  pinMode(GREEN_P3, OUTPUT);
  pinMode(RED_P3, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  digitalWrite(BUZZER_PIN, LOW);

  SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN);

  rfid1.PCD_Init();
  delay(100);

  rfid2.PCD_Init();
  delay(100);

  rfid3.PCD_Init();
  delay(100);

  Serial.println("RFID Readers Initialized");

  connectWiFi();

  setEvent("ESP32 Smart Parking Started", "info", "SYSTEM");

  lastFirebaseSync = 0;
  lastBookingRead = 0;
}

// ======================================================
// LOOP
// ======================================================
void loop() {
  keepWiFiConnected();

  readP3Booking();

  checkP1();
  checkP2();
  checkP3();

  updateIRFilters();
  updateParkingLogic();
  updateOutputs();

  sendPendingEvent();
  syncFirebase();

  delay(5);
}
