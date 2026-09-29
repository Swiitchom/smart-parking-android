#include <Wire.h>

#define LCD_SDA 21
#define LCD_SCL 22

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("==============================");
  Serial.println(" LCD I2C SCANNER TEST");
  Serial.println("==============================");
  Serial.println("SDA = GPIO21");
  Serial.println("SCL = GPIO22");
  Serial.println();

  Wire.begin(LCD_SDA, LCD_SCL);

  byte count = 0;

  for (byte address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    byte error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("I2C DEVICE FOUND: 0x");
      if (address < 16) Serial.print("0");
      Serial.println(address, HEX);
      count++;
    }

    delay(3);
  }

  Serial.println();

  if (count == 0) {
    Serial.println("NO I2C DEVICE FOUND");
    Serial.println("Check VCC, GND, SDA and SCL.");
  } else {
    Serial.print("TOTAL DEVICES: ");
    Serial.println(count);
  }

  Serial.println("==============================");
}

void loop() {
  // Scanner runs once only.
  delay(1000);
}
