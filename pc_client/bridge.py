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
import sys
import argparse

DEFAULT_PORT = 8989
ADB_PATH = "C:\\platform-tools\\adb.exe" if os.path.exists("C:\\platform-tools\\adb.exe") else "adb"

class PhoneNPUBridge:
    def __init__(self, port=DEFAULT_PORT, adb_path=ADB_PATH):
        self.port = port
        self.adb = adb_path
        self.sock = None

    def ensure_daemon_running(self):
        """Ensure the lightweight Android NPU daemon is active on the phone"""
        # Check if already listening
        check_cmd = [self.adb, "shell", f"toybox netstat -tlpn 2>/dev/null | grep {self.port}"]
        res = subprocess.run(check_cmd, capture_output=True, text=True)
        if str(self.port) not in res.stdout:
            print("[*] Launching NPU daemon on phone (/data/local/tmp/npu_bridge/npu_service.sh)...")
            start_cmd = [self.adb, "shell", "nohup /data/local/tmp/npu_bridge/npu_service.sh > /dev/null 2>&1 &"]
            subprocess.run(start_cmd)
            time.sleep(1.0)
            print("[+] Phone daemon started.")
        else:
            print("[+] Phone daemon is already running.")

    def setup_adb_forward(self):
        """Establish USB tunnel from localhost:PORT to phone:PORT"""
        cmd = [self.adb, "forward", f"tcp:{self.port}", f"tcp:{self.port}"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Failed to forward ADB port: {res.stderr.strip()}")

    def connect(self):
        """Connect to the Phone NPU Bridge daemon over USB"""
        self.ensure_daemon_running()
        self.setup_adb_forward()
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(5.0)
        self.sock.connect(("127.0.0.1", self.port))

    def send_request(self, payload: dict) -> dict:
        """Send JSON request to phone and return response with precise latency"""
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
        model = lines[0].strip() if len(lines) > 0 else "Unknown"
        soc = lines[1].strip() if len(lines) > 1 else "Unknown"
        android_ver = lines[2].strip() if len(lines) > 2 else "Unknown"
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

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None


def main():
    parser = argparse.ArgumentParser(description="Phone-NPU-Bridge: Turn phone into PC AI accelerator")
    parser.add_argument("--status", action="store_true", help="Show connected device and NPU status")
    parser.add_argument("--benchmark", action="store_true", help="Run latency benchmark over USB")
    parser.add_argument("--capabilities", action="store_true", help="Query Phone NPU / AI specs")
    parser.add_argument("--transcribe", type=str, nargs="?", const="default_sample.wav", help="Test Speech-to-Text inference on Phone NPU")
    args = parser.parse_args()

    bridge = PhoneNPUBridge()
    info = bridge.get_device_info()
    telem = bridge.get_hardware_telemetry()

    print("==================================================")
    print(f"  [*] PHONE-NPU-BRIDGE: {info['model']} (Android {info['android']})")
    print(f"  Hardware SoC: {info['soc']} | Batt: %{telem.get('battery_level', '?')} | Temp: {telem.get('temp_c', '?')}°C")
    print("==================================================")

    if args.capabilities:
        print("\n[*] Querying Phone NPU Hardware Capabilities...")
        try:
            bridge.connect()
            resp = bridge.send_request({"action": "capabilities"})
            print(json.dumps(resp, indent=2, ensure_ascii=False))
        except Exception as e:
            print(f"[-] Error: {e}")
        finally:
            bridge.close()
        return

    if args.benchmark:
        print("\n[*] Running 10-cycle USB Roundtrip Benchmark...")
        try:
            bridge.connect()
            times = []
            for i in range(10):
                resp = bridge.send_request({"action": "ping", "seq": i})
                lat = resp["roundtrip_latency_ms"]
                times.append(lat)
                print(f"  Cycle {i+1:02d}: {lat:5.2f} ms")
            avg = sum(times) / len(times)
            min_t = min(times)
            max_t = max(times)
            print(f"\n[+] Results: Avg={avg:.2f} ms | Min={min_t:.2f} ms | Max={max_t:.2f} ms")
        except Exception as e:
            print(f"[-] Benchmark error: {e}")
        finally:
            bridge.close()
        return

    if args.transcribe:
        print(f"\n[*] Transmitting Audio Payload to Phone NPU for Transcription...")
        try:
            bridge.connect()
            t0 = time.perf_counter()
            resp = bridge.send_request({"action": "transcribe", "file": args.transcribe})
            total_elapsed = (time.perf_counter() - t0) * 1000.0
            print(f"[+] Inference Completed on Phone NPU!")
            print(f"  Model Engine       : {resp.get('engine')}")
            print(f"  On-Chip Time       : {resp.get('infer_time_ms')} ms")
            print(f"  Total USB Pipeline : {total_elapsed:.2f} ms")
            print(f"  Decoded Text       : \"{resp.get('text')}\"")
            print(f"  Confidence Score   : {resp.get('confidence')}")
        except Exception as e:
            print(f"[-] Transcription error: {e}")
        finally:
            bridge.close()
        return

    # Default: status
    print("\n[*] Testing USB Link and Phone NPU Daemon...")
    try:
        bridge.connect()
        resp = bridge.send_request({"action": "ping"})
        print(f"[+] Ping Success! Engine: {resp.get('engine')} (USB Latency: {resp['roundtrip_latency_ms']} ms)")
    except Exception as e:
        print(f"[-] Link error: {e}")
    finally:
        bridge.close()


if __name__ == "__main__":
    main()

