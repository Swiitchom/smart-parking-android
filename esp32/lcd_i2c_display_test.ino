#include <Wire.h>
#include <LiquidCrystal_I2C.h>

#define LCD_SDA 21
#define LCD_SCL 22

LiquidCrystal_I2C lcd(0x27, 20, 4);

void setup() {
  Serial.begin(115200);
  delay(500);

  Wire.begin(LCD_SDA, LCD_SCL);

  lcd.init();
  lcd.backlight();
  lcd.clear();

  lcd.setCursor(0, 0);
  lcd.print("SCHOOL OMAN PARKING");

  lcd.setCursor(0, 1);
  lcd.print("LCD TEST OK");

  lcd.setCursor(0, 2);
  lcd.print("I2C ADDRESS: 0x27");

  lcd.setCursor(0, 3);
  lcd.print("READY");
}

void loop() {
}
