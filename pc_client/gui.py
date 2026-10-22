import sys
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

import tkinter as tk
from tkinter import ttk, messagebox
import threading
from bridge import PhoneNPUBridge

class PhoneNPUApp:
    def __init__(self, root):
        self.root = root
        self.root.title("⚡ Phone-NPU-Bridge | External AI Coprocessor")
        self.root.geometry("640x540")
        self.root.resizable(False, False)
        self.bridge = PhoneNPUBridge()

        # Styles
        style = ttk.Style()
        style.theme_use('clam')

        # Header Frame
        header = tk.Frame(root, bg="#1e1e2e", padx=15, pady=15)
        header.pack(fill="x")
        
        title_label = tk.Label(header, text="Phone-NPU-Bridge", font=("Segoe UI", 16, "bold"), fg="#cdd6f4", bg="#1e1e2e")
        title_label.pack(anchor="w")
        
        subtitle = tk.Label(header, text="Turn Phone's 44 TOPS NPU into a Zero-Heat PC AI Accelerator", font=("Segoe UI", 10), fg="#a6adc8", bg="#1e1e2e")
        subtitle.pack(anchor="w")

        # Telemetry Bar
        self.telem_label = tk.Label(root, text="[Hardware] Connecting to USB device...", font=("Consolas", 9), fg="#585b70", pady=6)
        self.telem_label.pack()

        # Action Buttons Frame
        btn_frame = tk.Frame(root, padx=15, pady=10)
        btn_frame.pack(fill="x")

        self.btn_transcribe = ttk.Button(btn_frame, text="🎙️ Transcribe Audio (Whisper)", command=self.run_transcribe)
        self.btn_transcribe.grid(row=0, column=0, padx=5, pady=5, sticky="ew")

        self.btn_code = ttk.Button(btn_frame, text="💻 Code Copilot (Qwen-Coder)", command=self.run_code)
        self.btn_code.grid(row=0, column=1, padx=5, pady=5, sticky="ew")

        self.btn_ocr = ttk.Button(btn_frame, text="📄 Extract OCR / Invoice", command=self.run_ocr)
        self.btn_ocr.grid(row=1, column=0, padx=5, pady=5, sticky="ew")

        self.btn_bench = ttk.Button(btn_frame, text="⚡ Run USB Latency Benchmark", command=self.run_benchmark)
        self.btn_bench.grid(row=1, column=1, padx=5, pady=5, sticky="ew")

        btn_frame.columnconfigure(0, weight=1)
        btn_frame.columnconfigure(1, weight=1)

        # Output Text Area
        output_frame = tk.Frame(root, padx=15, pady=5)
        output_frame.pack(fill="both", expand=True)

        lbl_out = tk.Label(output_frame, text="Live Output & NPU Telemetry:", font=("Segoe UI", 10, "bold"), anchor="w")
        lbl_out.pack(fill="x")

        self.txt_output = tk.Text(output_frame, font=("Consolas", 10), bg="#181825", fg="#a6e3a1", insertbackground="white", padx=10, pady=10)
        self.txt_output.pack(fill="both", expand=True)

        # Initialize connection in background
        threading.Thread(target=self.init_hardware, daemon=True).start()

    def log(self, text):
        self.txt_output.insert("end", text + "\n")
        self.txt_output.see("end")

    def init_hardware(self):
        try:
            info = self.bridge.get_device_info()
            telem = self.bridge.get_hardware_telemetry()
            self.telem_label.config(text=f"[Device: {info['model']}] SoC: {info['soc']} | Batt: %{telem.get('battery_level', '?')} | Temp: {telem.get('temp_c', '?')}°C")
            self.log("[✓] Link established over USB 3.2.")
            self.log(f"[✓] Connected Device: {info['model']} ({info['soc']})")
            self.log("[✓] NPU Acceleration Engine Ready: Samsung ENN / Android NNAPI\n")
        except Exception as e:
            self.telem_label.config(text="[Error] No USB debugging device detected.")
            self.log(f"[-] USB connection error: {e}")

    def run_transcribe(self):
        threading.Thread(target=self._transcribe_worker, daemon=True).start()

    def _transcribe_worker(self):
        self.log("\n[*] Offloading 18.5s Audio Chunk to Phone NPU...")
        try:
            resp = self.bridge.transcribe("sample_meeting.wav")
            self.log(f"[+] Engine: {resp.get('engine')} (Chip Time: {resp.get('infer_time_ms')} ms)")
            self.log(f"[+] Decoded Text: \"{resp.get('text')}\"")
        except Exception as e:
            self.log(f"[-] Error: {e}")

    def run_code(self):
        threading.Thread(target=self._code_worker, daemon=True).start()

    def _code_worker(self):
        self.log("\n[*] Prompting Phone NPU for Code Completion...")
        try:
            resp = self.bridge.complete_code("def calculate_roi(")
            self.log(f"[+] Engine: {resp.get('engine')} ({resp.get('tokens_generated')} tokens in {resp.get('infer_time_ms')} ms)")
            self.log(f"[+] Completed Code:\n{resp.get('code_completion')}")
        except Exception as e:
            self.log(f"[-] Error: {e}")

    def run_ocr(self):
        threading.Thread(target=self._ocr_worker, daemon=True).start()

    def _ocr_worker(self):
        self.log("\n[*] Streaming Invoice Image to Phone NPU for OCR...")
        try:
            resp = self.bridge.extract_ocr("invoice.png")
            self.log(f"[+] Engine: {resp.get('engine')} ({resp.get('infer_time_ms')} ms)")
            for k, v in resp.get('extracted_fields', {}).items():
                self.log(f"    • {k}: {v}")
        except Exception as e:
            self.log(f"[-] Error: {e}")

    def run_benchmark(self):
        threading.Thread(target=self._bench_worker, daemon=True).start()

    def _bench_worker(self):
        self.log("\n[*] Running 5-Cycle USB Roundtrip Latency Test...")
        try:
            for i in range(5):
                self.bridge.connect()
                resp = self.bridge.send_request({"action": "ping"})
                self.log(f"  Cycle {i+1}: {resp.get('roundtrip_latency_ms')} ms")
                self.bridge.close()
            self.log("[✓] Benchmark Complete.")
        except Exception as e:
            self.log(f"[-] Benchmark error: {e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = PhoneNPUApp(root)
    root.mainloop()
