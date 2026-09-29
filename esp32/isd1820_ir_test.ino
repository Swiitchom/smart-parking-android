// ======================================================
// ISD1820 + IR SENSORS STANDALONE TEST
// Does NOT use Firebase, RFID, LCD, LEDs, or buzzer.
// ======================================================

#define IR_P1 32
#define IR_P2 33
#define IR_P3 34
#define IR_ACTIVE LOW

// Temporary test pin for ISD1820 PLAYE
#define ISD_PLAYE 2

const unsigned long IR_CONFIRM_MS = 400;
const unsigned long PLAY_PULSE_MS = 150;

bool rawAny = false;
bool stableAny = false;
bool armed = true;

unsigned long rawChangedAt = 0;

bool sensorActive(int pin) {
  return digitalRead(pin) == IR_ACTIVE;
}

int activeSensorNumber() {
  if (sensorActive(IR_P1)) return 1;
  if (sensorActive(IR_P2)) return 2;
  if (sensorActive(IR_P3)) return 3;
  return 0;
}

void playRecordedMessage() {
  Serial.println("VOICE: PLAY");
  digitalWrite(ISD_PLAYE, HIGH);
  delay(PLAY_PULSE_MS);
  digitalWrite(ISD_PLAYE, LOW);
}

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(IR_P1, INPUT);
  pinMode(IR_P2, INPUT);
  pinMode(IR_P3, INPUT);

  pinMode(ISD_PLAYE, OUTPUT);
  digitalWrite(ISD_PLAYE, LOW);

  rawAny =
    sensorActive(IR_P1) ||
    sensorActive(IR_P2) ||
    sensorActive(IR_P3);

  stableAny = rawAny;
  rawChangedAt = millis();

  Serial.println();
  Serial.println("==============================");
  Serial.println(" ISD1820 + IR SENSOR TEST");
  Serial.println("==============================");
  Serial.println("PLAYE -> GPIO2");
  Serial.println("IR P1 -> GPIO32");
  Serial.println("IR P2 -> GPIO33");
  Serial.println("IR P3 -> GPIO34");
  Serial.println();
  Serial.println("Ready.");
}

void loop() {
  bool anyNow =
    sensorActive(IR_P1) ||
    sensorActive(IR_P2) ||
    sensorActive(IR_P3);

  if (anyNow != rawAny) {
    rawAny = anyNow;
    rawChangedAt = millis();
  }

  if (rawAny != stableAny &&
      millis() - rawChangedAt >= IR_CONFIRM_MS) {

    stableAny = rawAny;

    if (stableAny) {
      int sensor = activeSensorNumber();

      Serial.print("IR DETECTED: P");
      Serial.println(sensor);

      if (armed) {
        armed = false;
        playRecordedMessage();
      }
    } else {
      Serial.println("IR CLEAR");
      armed = true;
    }
  }

  delay(5);
}
