import sys
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import socket
import json
import time
import subprocess
import os
import argparse

DEFAULT_PORT = 8989
ADB_PATH = "C:\\platform-tools\\adb.exe" if os.path.exists("C:\\platform-tools\\adb.exe") else "adb"

class PhoneNPUBridge:
    """
    PhoneNPUBridge SDK: Connects your PC to an Android Phone's NPU over USB 3.2.
    Turns dormant mobile AI silicon into a zero-heat desktop coprocessor.
    """
    def __init__(self, port=DEFAULT_PORT, adb_path=ADB_PATH):
        self.port = port
        self.adb = adb_path
        self.sock = None

    def ensure_daemon_running(self):
        check_cmd = [self.adb, "shell", f"toybox netstat -tlpn 2>/dev/null | grep {self.port}"]
        res = subprocess.run(check_cmd, capture_output=True, text=True)
        if str(self.port) not in res.stdout:
            start_cmd = [self.adb, "shell", "nohup /data/local/tmp/npu_bridge/npu_service.sh > /dev/null 2>&1 &"]
            subprocess.run(start_cmd)
            time.sleep(1.0)

    def setup_adb_forward(self):
        cmd = [self.adb, "forward", f"tcp:{self.port}", f"tcp:{self.port}"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Failed to forward ADB port: {res.stderr.strip()}")

    def connect(self):
        self.ensure_daemon_running()
        self.setup_adb_forward()
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(5.0)
        self.sock.connect(("127.0.0.1", self.port))

    def send_request(self, payload: dict) -> dict:
        if not self.sock:
            self.connect()
        
        req_bytes = json.dumps(payload).encode("utf-8") + b"\n"
        start_time = time.perf_counter()
        self.sock.sendall(req_bytes)
        
        resp_bytes = self.sock.recv(65536)
        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        
        resp = json.loads(resp_bytes.decode("utf-8").strip())
        resp["roundtrip_latency_ms"] = round(elapsed_ms, 2)
        return resp

    def get_device_info(self):
        cmd = [self.adb, "shell", "getprop ro.product.model; getprop ro.hardware; getprop ro.build.version.release"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        lines = res.stdout.strip().split("\n")
        model = lines[0].strip() if len(lines) > 0 else "Android Device"
        soc = lines[1].strip() if len(lines) > 1 else "Generic SoC"
        android_ver = lines[2].strip() if len(lines) > 2 else "14+"
        return {"model": model, "soc": soc, "android": android_ver}

    def get_hardware_telemetry(self):
        cmd = [self.adb, "shell", "dumpsys battery | grep -E 'temperature|level|voltage'"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        telemetry = {}
        for line in res.stdout.strip().split("\n"):
            if "temperature:" in line:
                telemetry["temp_c"] = float(line.split(":")[1].strip()) / 10.0
            elif "level:" in line:
                telemetry["battery_level"] = int(line.split(":")[1].strip())
            elif "voltage:" in line:
                telemetry["voltage_mv"] = int(line.split(":")[1].strip())
        return telemetry

    def transcribe(self, audio_path: str = "input.wav"):
        """Speech-to-Text: Subtitles, Meeting notes, Voice transcription"""
        return self.send_request({"action": "transcribe", "file": audio_path})

    def complete_code(self, prompt: str):
        """Code & Text Auto-complete on phone NPU"""
        return self.send_request({"action": "complete", "prompt": prompt})

    def extract_ocr(self, image_path: str = "document.png"):
        """Vision & Document OCR data extraction"""
        return self.send_request({"action": "ocr", "image": image_path})

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None


def main():
    parser = argparse.ArgumentParser(description="Phone-NPU-Bridge: Turn your phone into an external AI coprocessor for PC")
    parser.add_argument("--status", action="store_true", help="Display device connection & hardware status")
    parser.add_argument("--capabilities", action="store_true", help="Show NPU hardware acceleration specs")
    parser.add_argument("--benchmark", action="store_true", help="Run 10-cycle USB roundtrip latency test")
    parser.add_argument("--transcribe", type=str, nargs="?", const="sample.wav", help="Transcribe audio/video file using phone NPU")
    parser.add_argument("--code", type=str, nargs="?", const="def calculate_roi(", help="Auto-complete code using phone NPU")
    parser.add_argument("--ocr", type=str, nargs="?", const="invoice.png", help="Extract text/data from image via phone NPU")
    args = parser.parse_args()

    bridge = PhoneNPUBridge()
    info = bridge.get_device_info()
    telem = bridge.get_hardware_telemetry()

    print("==================================================================")
    print(f"  [+] PHONE-NPU-BRIDGE: {info['model']} (Android {info['android']})")
    print(f"  Hardware SoC: {info['soc']} | Batt: %{telem.get('battery_level', '?')} | Temp: {telem.get('temp_c', '?')}°C")
    print("==================================================================")

    try:
        if args.capabilities:
            print("\n[*] Querying Phone NPU Hardware Capabilities...")
            resp = bridge.send_request({"action": "capabilities"})
            print(json.dumps(resp, indent=2, ensure_ascii=False))

        elif args.benchmark:
            print("\n[*] Running 10-cycle USB Roundtrip Benchmark...")
            times = []
            for i in range(10):
                bridge.connect()
                resp = bridge.send_request({"action": "ping", "seq": i})
                lat = resp["roundtrip_latency_ms"]
                times.append(lat)
                print(f"  Cycle {i+1:02d}: {lat:5.2f} ms")
                bridge.close()
            avg = sum(times) / len(times)
            print(f"\n[+] Results: Avg={avg:.2f} ms | Min={min(times):.2f} ms | Max={max(times):.2f} ms")

        elif args.transcribe:
            print(f"\n[*] Mode: Video/Audio Speech-to-Text Pipeline...")
            resp = bridge.transcribe(args.transcribe)
            print(f"[+] Engine       : {resp.get('engine')}")
            print(f"[+] On-Chip Time : {resp.get('infer_time_ms')} ms (USB Pipeline: {resp.get('roundtrip_latency_ms')} ms)")
            print(f"[+] Transcription: \"{resp.get('text')}\"")

        elif args.code:
            print(f"\n[*] Mode: AI Local Code-Copilot Completion...")
            resp = bridge.complete_code(args.code)
            print(f"[+] Engine       : {resp.get('engine')}")
            print(f"[+] Tokens/s     : {resp.get('tokens_generated')} tokens in {resp.get('infer_time_ms')} ms")
            print(f"[+] Code Output  :\n{resp.get('code_completion')}")

        elif args.ocr:
            print(f"\n[*] Mode: Document & Invoice OCR Extraction...")
            resp = bridge.extract_ocr(args.ocr)
            print(f"[+] Engine       : {resp.get('engine')} (Time: {resp.get('infer_time_ms')} ms)")
            print(f"[+] Extracted    : {json.dumps(resp.get('extracted_fields'), indent=2)}")

        else:
            print("\n[*] Testing USB Link and Phone NPU Daemon...")
            resp = bridge.send_request({"action": "ping"})
            print(f"[+] Ping Success! Engine: {resp.get('engine')} (Latency: {resp['roundtrip_latency_ms']} ms)")
            print("\nTry running:")
            print("  python bridge.py --transcribe")
            print("  python bridge.py --code")
            print("  python bridge.py --ocr")
            print("  python bridge.py --benchmark")

    except Exception as e:
        print(f"[-] Error: {e}")
    finally:
        bridge.close()

if __name__ == "__main__":
    main()
