const int RED_PIN = 8;
const int GREEN_PIN = 9;

void setup() {
  Serial.begin(9600);
  pinMode(RED_PIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  // 시작 시 초록불
  digitalWrite(GREEN_PIN, HIGH);
}

void loop() {
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'R') {
      digitalWrite(RED_PIN, HIGH);
      digitalWrite(GREEN_PIN, LOW);
    } else if (c == 'G') {
      digitalWrite(RED_PIN, LOW);
      digitalWrite(GREEN_PIN, HIGH);
    }
  }
}
