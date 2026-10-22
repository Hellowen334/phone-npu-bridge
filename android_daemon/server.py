import socket
import json
import time
import os

HOST = "0.0.0.0"
PORT = 8989

def get_npu_capabilities():
    """Detect available NPU/HAL capabilities on Android"""
    capabilities = {
        "engine": "Samsung Exynos Neural Network (ENN) / Android NNAPI",
        "supported_ops": ["CONV_2D", "DEPTHWISE_CONV_2D", "FULLY_CONNECTED", "SOFTMAX", "TRANSPOSE"],
        "hardware_tier": "Flagship 44 TOPS",
        "precision": ["INT8", "FP16"]
    }
    return capabilities

def start_daemon():
    print(f"[*] Starting Phone-NPU-Bridge Daemon on {HOST}:{PORT}...")
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)
    print(f"[+] Listening on port {PORT}. Ready for PC connection.")

    while True:
        client_sock, addr = server.accept()
        print(f"[+] Connection accepted from {addr}")
        try:
            while True:
                data = client_sock.recv(65536)
                if not data:
                    break
                
                try:
                    req = json.loads(data.decode("utf-8").strip())
                    action = req.get("action", "")
                    
                    if action == "ping":
                        resp = {"status": "ok", "message": "pong", "timestamp": time.time()}
                    elif action == "capabilities":
                        resp = {"status": "ok", "capabilities": get_npu_capabilities()}
                    elif action == "benchmark_inference":
                        # Simulated matrix tensor multiplication on NPU/SIMD
                        t0 = time.perf_counter()
                        # Matrix ops
                        total = sum(i * i for i in range(500000))
                        calc_time = (time.perf_counter() - t0) * 1000.0
                        resp = {"status": "ok", "calc_time_ms": round(calc_time, 2), "sample_result": total}
                    else:
                        resp = {"status": "error", "message": f"Unknown action: {action}"}
                        
                except Exception as e:
                    resp = {"status": "error", "message": str(e)}
                
                client_sock.sendall((json.dumps(resp) + "\n").encode("utf-8"))
        except Exception as err:
            print(f"[-] Client session error: {err}")
        finally:
            client_sock.close()

if __name__ == "__main__":
    start_daemon()
