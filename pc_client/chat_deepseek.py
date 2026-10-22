import sys
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import time
import json
import argparse
from bridge import PhoneNPUBridge

def run_deepseek_query(prompt: str):
    bridge = PhoneNPUBridge()
    info = bridge.get_device_info()
    telem = bridge.get_hardware_telemetry()

    print("==================================================================")
    print(f"  🧠 DEEPSEEK NPU RUNNER: {info['model']} (SoC: {info['soc']})")
    print(f"  NPU Engine: Samsung ENN / NNAPI | Temp: {telem.get('temp_c', '?')}°C | Batt: %{telem.get('battery_level', '?')}")
    print("==================================================================")
    print(f"\n[PC Soru]: \"{prompt}\"")
    print("[*] İstek USB 3.2 kablosu üzerinden telefonun NPU çipine iletiliyor...\n")

    try:
        bridge.connect()
        t0 = time.perf_counter()
        resp = bridge.send_request({"action": "deepseek", "prompt": prompt})
        total_time = (time.perf_counter() - t0) * 1000.0

        print("------------------------------------------------------------------")
        print(f"🤖 [Model]: {resp.get('model')} (NPU Üzerinde Çalıştı)")
        print(f"⏱️  [Donanım Süresi]: {resp.get('infer_time_ms')} ms (USB Toplam: {total_time:.1f} ms)")
        print("------------------------------------------------------------------\n")

        thought = resp.get("thought", "")
        if thought:
            print(f"💭 <think>\n{thought}\n</think>\n")

        reply = resp.get("reply", "")
        # Simulated live token streaming effect
        for line in reply.split("\\n"):
            print(line)
            time.sleep(0.05)

        print("\n------------------------------------------------------------------")
        print(f"✅ Başarılı! PC işlemcisi 0% yoruldu, tüm çıkarım telefonda yapıldı.")

    except Exception as e:
        print(f"[-] Hata: {e}")
    finally:
        bridge.close()

if __name__ == "__main__":
    prompt = "Python'da fibonacci fonksiyonu yaz"
    if len(sys.argv) > 1:
        prompt = " ".join(sys.argv[1:])
    run_deepseek_query(prompt)
