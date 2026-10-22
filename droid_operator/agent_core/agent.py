"""
DroidOperator Agent Core (Brain)
================================
Runs on-device (via Termux Python or native service).
Communicates with DroidAccessibilityService via localhost:8765.
"""

import json
import time
import urllib.request
import urllib.parse
from typing import Dict, Any, List, Optional

SERVER_URL = "http://127.0.0.1:8765"


class DroidClient:
    """HTTP Client communicating directly with the on-device DroidAccessibilityService."""

    def __init__(self, base_url: str = SERVER_URL):
        self.base_url = base_url

    def get_ui(self) -> List[Dict[str, Any]]:
        """Retrieves the active UI hierarchy from the phone's accessibility tree."""
        req = urllib.request.Request(f"{self.base_url}/ui")
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                data = response.read().decode("utf-8")
                return json.loads(data)
        except Exception as e:
            print(f"[!] UI alma hatasi: {e}")
            return []

    def click(self, x: int, y: int) -> bool:
        """Sends a tap action to physical screen coordinates (x, y)."""
        payload = json.dumps({"x": x, "y": y}).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}/click",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                res = json.loads(response.read().decode("utf-8"))
                return res.get("success", False)
        except Exception as e:
            print(f"[!] Tiklama hatasi: {e}")
            return False

    def type_text(self, text: str) -> bool:
        """Types text into the active focused field."""
        payload = json.dumps({"text": text}).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}/type",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                res = json.loads(response.read().decode("utf-8"))
                return res.get("success", False)
        except Exception as e:
            print(f"[!] Yazi yazma hatasi: {e}")
            return False

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration: int = 300) -> bool:
        """Swipes from (x1, y1) to (x2, y2)."""
        payload = json.dumps({
            "x1": x1, "y1": y1, "x2": x2, "y2": y2, "duration": duration
        }).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}/swipe",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                res = json.loads(response.read().decode("utf-8"))
                return res.get("success", False)
        except Exception as e:
            print(f"[!] Kaydirma hatasi: {e}")
            return False

    def press_key(self, action: str) -> bool:
        """action can be 'back', 'home', 'recents'."""
        payload = json.dumps({"action": action}).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}/key",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                res = json.loads(response.read().decode("utf-8"))
                return res.get("success", False)
        except Exception as e:
            print(f"[!] Sistem tusu hatasi: {e}")
            return False


class DroidOperatorAgent:
    """Autonomous ReAct Mobile Agent."""

    def __init__(self, client: Optional[DroidClient] = None):
        self.client = client or DroidClient()

    def format_ui_prompt(self, nodes: List[Dict[str, Any]]) -> str:
        """Formats the UI elements into a compact list for the LLM."""
        lines = []
        for n in nodes:
            label = n.get("text") or n.get("desc") or n.get("id") or n.get("class", "").split(".")[-1]
            clickable = "✓" if n.get("clickable") else " "
            editable = "✎" if n.get("editable") else " "
            coords = f"({n.get('centerX')}, {n.get('centerY')})"
            lines.append(f"[{n.get('index')}] [{clickable}{editable}] {label} @ {coords}")
        return "\n".join(lines[:60])  # Limit to top 60 relevant nodes

    def run_step(self, goal: str, history: List[str]) -> Dict[str, Any]:
        """
        Observes the screen, asks the model for decision, and executes the action.
        """
        nodes = self.client.get_ui()
        if not nodes:
            return {"status": "error", "message": "Ekran bos veya erisilebilirlik kapali"}

        ui_text = self.format_ui_prompt(nodes)

        # In production on device: This is sent to local llama-server (127.0.0.1:8080)
        # or local GGUF model via llama.cpp / python-binding
        print(f"--- [Mevcut Ekran Elementleri: {len(nodes)}] ---")
        print(ui_text[:500] + ("..." if len(ui_text) > 500 else ""))

        # Return inspection data for verification
        return {
            "status": "ready",
            "element_count": len(nodes),
            "elements": nodes
        }


if __name__ == "__main__":
    client = DroidClient()
    agent = DroidOperatorAgent(client)
    print("[*] DroidOperator Agent Baslatildi.")
