import sys
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import time
import json
import urllib.request
import argparse
from bridge import PhoneNPUBridge

OLLAMA_API_URL = "http://localhost:11434/api/generate"

def query_local_ollama(prompt: str):
    """Query locally running DeepSeek-R1 model on the laptop"""
    payload = {
        "model": "deepseek-r1:1.5b",
        "prompt": prompt,
        "stream": False
    }
    req = urllib.request.Request(
        OLLAMA_API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        elapsed = (time.perf_counter() - t0) * 1000.0
        return data.get("response", ""), elapsed

def main():
    parser = argparse.ArgumentParser(description="DeepSeek-R1 Local & NPU Chat")
    parser.add_argument("prompt", type=str, nargs="?", default="1 + 1 kaç eder? Açıkla.", help="Prompt to send to DeepSeek")
    parser.add_argument("--mode", choices=["local", "phone-npu"], default="local", help="Run on local laptop or offload to phone NPU")
    args = parser.parse_args()

    print("==================================================================")
    print("  🧠 DEEPSEEK-R1 LOCAL & NPU AI RUNNER (100% FREE & OFFLINE)")
    print(f"  Mode: {args.mode.upper()} | Model: deepseek-r1:1.5b")
    print("==================================================================")
    print(f"\n[Soru]: \"{args.prompt}\"\n")

    if args.mode == "local":
        print("[*] Yerel laptop modelinden yanıt üretiliyor (Ollama Local Engine)...")
        try:
            response_text, elapsed_ms = query_local_ollama(args.prompt)
            print("------------------------------------------------------------------")
            print(f"🤖 [DeepSeek-R1 Yanıtı] (Süre: {elapsed_ms:.1f} ms):")
            print("------------------------------------------------------------------\n")
            print(response_text)
            print("\n------------------------------------------------------------------")
            print("✅ Yanıt tamamen yerel diskteki modelden, internetsiz ve ücretsiz üretildi.")
        except Exception as e:
            print(f"[-] Yerel model hatası: {e}")
            print("[!] Ollama arka planda çalışmıyor olabilir. 'ollama serve' komutunu kontrol edin.")
    else:
        # Offload to Phone NPU
        print("[*] Telefon NPU köprüsüne aktarılıyor...")
        bridge = PhoneNPUBridge()
        try:
            bridge.connect()
            resp = bridge.send_request({"action": "deepseek", "prompt": args.prompt})
            print(resp.get("reply", ""))
        finally:
            bridge.close()

if __name__ == "__main__":
    main()
