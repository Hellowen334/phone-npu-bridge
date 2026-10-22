# DroidOperator: Autonomous On-Device Android Phone Operator
> Bilgisayardan bağımsız, yerel erişilebilirlik (Accessibility) ve yapay zeka ajan mimarisiyle Android telefonları otonom yöneten açık kaynaklı ajan motoru.

---

## 1. Mimari Genel Bakış

DroidOperator, PC dünyasındaki **OpenClaw** veya **Claude Computer Use** konseptini, bilgisayar bağlantısına (ADB kablosuna) ihtiyaç duymadan **doğrudan Android telefonun içinde** çalıştıran bir otonom mobil ajandır.

```
       ┌────────────────────────────────────────────────────────┐
       │                   SAMSUNG GALAXY S24+                  │
       │                                                        │
       │   [ Mikrofon / Ses ]                                   │
       │           │                                            │
       │           ▼                                            │
       │   [ VoiceTrigger ] (SpeechRecognizer / Offline TR)     │
       │           │                                            │
       │           ▼                                            │
       │   [ DroidAgent Core ] ◄── ReAct Decision Loop          │
       │      (Sense - Think - Act)                             │
       │           │                                            │
       │           ▼                                            │
       │   [ Localhost HTTP Bridge ] (127.0.0.1:8765)           │
       │      ├── GET  /ui         (Accessibility Tree JSON)   │
       │      ├── GET  /screenshot (Native Screen Capture)      │
       │      ├── POST /click      (dispatchGesture Tap)        │
       │      ├── POST /type       (Input Text Injection)       │
       │      ├── POST /swipe      (Smooth Gesture Drag)        │
       │      └── POST /key        (Back / Home / Recents)      │
       │           │                                            │
       │           ▼                                            │
       │   [ AccessibilityService ]                             │
       │           │                                            │
       │           ▼                                            │
       │   [ Android UI Framework & Diğer Uygulamalar ]         │
       └────────────────────────────────────────────────────────┘
```

---

## 2. Modüller

* `android_service/`: Telefon üzerinde arka planda çalışan, Android Accessibility Service ve yerel HTTP kontrol sunucusunu barındıran Java/Kotlin projesi.
* `agent_core/`: Ajan karar döngüsü (Sense-Think-Act). Ekran ağacını okur, hedef doğrultusunda en mantıklı butonu/aksiyonu seçer ve yerel servise gönderir.
* `voice_trigger/`: Cihazın yerel ses tanıma ve sentezleme arabirimi.
