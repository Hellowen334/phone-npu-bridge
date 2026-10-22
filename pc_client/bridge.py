import socket
import json
import time
import subprocess
import os
import sys

DEFAULT_PORT = 8989
ADB_PATH = "C:\\platform-tools\\adb.exe" if os.path.exists("C:\\platform-tools\\adb.exe") else "adb"

class PhoneNPUBridge:
    def __init__(self, port=DEFAULT_PORT, adb_path=ADB_PATH):
        self.port = port
        self.adb = adb_path
        self.sock = None

    def setup_adb_forward(self):
        """Set up ADB port forward over USB: localhost:PORT -> phone:PORT"""
        print(f"[*] Setting up ADB port forwarding (tcp:{self.port} -> tcp:{self.port})...")
        cmd = [self.adb, "forward", f"tcp:{self.port}", f"tcp:{self.port}"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Failed to forward ADB port: {res.stderr.strip()}")
        print("[+] USB Port forwarding established successfully.")

    def get_device_info(self):
        """Query connected Android device name and chipset"""
        cmd = [self.adb, "shell", "getprop ro.product.model; getprop ro.hardware"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        lines = res.stdout.strip().split("\n")
        model = lines[0].strip() if len(lines) > 0 else "Unknown"
        soc = lines[1].strip() if len(lines) > 1 else "Unknown"
        return {"model": model, "soc": soc}

    def get_hardware_telemetry(self):
        """Query real-time battery and thermal status over ADB"""
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

    def connect(self):
        """Connect to the Phone-NPU-Bridge daemon on the phone"""
        self.setup_adb_forward()
        print(f"[*] Connecting to Phone NPU Bridge at 127.0.0.1:{self.port}...")
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10.0)
        self.sock.connect(("127.0.0.1", self.port))
        print("[+] Connected to Phone NPU Daemon!")

    def send_request(self, payload: dict) -> dict:
        """Send JSON payload to phone and await result"""
        if not self.sock:
            raise ConnectionError("Bridge not connected. Call connect() first.")
        
        req_bytes = json.dumps(payload).encode("utf-8") + b"\n"
        start_time = time.perf_counter()
        self.sock.sendall(req_bytes)
        
        resp_bytes = self.sock.recv(65536)
        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        
        resp = json.loads(resp_bytes.decode("utf-8").strip())
        resp["roundtrip_latency_ms"] = round(elapsed_ms, 2)
        return resp

    def benchmark_usb_latency(self, iterations=10):
        """Measure round-trip ping latency between PC and Phone NPU daemon"""
        print(f"\n--- Running USB Latency Benchmark ({iterations} iterations) ---")
        latencies = []
        for i in range(iterations):
            resp = self.send_request({"action": "ping", "id": i})
            lat = resp["roundtrip_latency_ms"]
            latencies.append(lat)
            print(f"  Iteration {i+1}: {lat} ms")
        avg = sum(latencies) / len(latencies)
        print(f"[✓] Average USB Roundtrip Latency: {avg:.2f} ms\n")
        return avg

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None


if __name__ == "__main__":
    print("========================================")
    print("      PHONE-NPU-BRIDGE PC CLIENT        ")
    print("========================================")
    
    bridge = PhoneNPUBridge()
    info = bridge.get_device_info()
    telem = bridge.get_hardware_telemetry()
    
    print(f"[*] Detected Device : {info['model']} (SoC: {info['soc']})")
    print(f"[*] Battery Status  : %{telem.get('battery_level', '?')} | {telem.get('temp_c', '?')}°C | {telem.get('voltage_mv', '?')} mV")
    print("\nBridge is ready to link with Android NPU daemon.")
