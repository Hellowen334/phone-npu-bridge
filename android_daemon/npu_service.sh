#!/system/bin/sh
# Phone-NPU-Bridge Server Launcher
PORT=8989
HANDLER="/data/local/tmp/npu_bridge/handler.sh"

chmod +x "$HANDLER"
pkill -f "nc.*8989" 2>/dev/null

echo "[Phone-NPU-Bridge] Launching multi-client server on port $PORT..."
exec toybox nc -L -p "$PORT" "$HANDLER"
