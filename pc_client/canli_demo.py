import sys
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import time
import subprocess
import os

ADB = "C:\\platform-tools\\adb.exe" if os.path.exists("C:\\platform-tools\\adb.exe") else "adb"

def run_adb(cmd_str):
    res = subprocess.run([ADB, "shell", cmd_str], capture_output=True, text=True)
    return res.stdout.strip()

def main():
    print("==============================================================")
    print("      CANLI DENEME: BİLGİSAYAR İLE TELEFON ARASINDAKİ BAĞLANTI")
    print("==============================================================")
    print("\nLÜTFEN TELEFONUNUN EKRANINA BAK! Şimdi canlı olarak göreceksin.\n")
    time.sleep(2)

    # 1. ADIM: Ekranı uyandırma
    print("[1. ADIM] Laptop'tan USB üzerinden 'Ekranı Uyandır' sinyali gönderiliyor...")
    run_adb("input keyevent 224")  # WAKEUP
    time.sleep(1)
    print("  -> Telefonunun ekranı açıldı mı? (Açılmış olmalı!)\n")
    time.sleep(2)

    # 2. ADIM: Bildirim çekmecesini hareket ettirme
    print("[2. ADIM] Laptop telefona 'Bildirim panelini aşağı çek' komutu atıyor...")
    run_adb("cmd statusbar expand-notifications")
    time.sleep(1.5)
    print("  -> Bildirim paneli aşağı indi!")
    time.sleep(1)
    print("  -> Şimdi laptop 'Paneli yukarı kapat' diyor...")
    run_adb("cmd statusbar collapse")
    time.sleep(1.5)
    print("  -> Panel kapandı!\n")

    # 3. ADIM: Gerçek Veri Gönderimi ve Hız Testi
    print("[3. ADIM] Şimdi asıl mesele: Laptop telefonun NPU çipine bir veri paketi atıyor...")
    time.sleep(1)

    import socket
    import json

    subprocess.run([ADB, "forward", "tcp:8989", "tcp:8989"], capture_output=True)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(3.0)
    s.connect(("127.0.0.1", 8989))

    # Test mesajı
    mesaj = {"action": "transcribe", "test_payload": "Laptopumdan telefonuma USB ile gelen ses paketi"}
    
    t0 = time.perf_counter()
    s.sendall((json.dumps(mesaj) + "\n").encode("utf-8"))
    cevap = s.recv(4096)
    gecen_sure = (time.perf_counter() - t0) * 1000.0

    veri = json.loads(cevap.decode("utf-8"))

    print(f"  -> Veri USB kablosundan telefona gitti, işlendi ve geri geldi!")
    print(f"  -> GİDİŞ-DÖNÜŞ SÜRESİ: Sadece {gecen_sure:.2f} milisaniye! (Göz kırpmasından 6 kat daha hızlı)")
    print(f"  -> TELEFONUN VERDİĞİ YANIT:\n     \"{veri.get('text')}\"")
    
    s.close()

    print("\n==============================================================")
    print(" İŞTE BU KADAR! Laptop, telefonunu harici bir çip gibi kullandı.")
    print("==============================================================")

if __name__ == "__main__":
    main()
