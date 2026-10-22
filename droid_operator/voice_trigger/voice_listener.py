"""
DroidOperator Voice Trigger
===========================
Handles voice input on Android using native Speech-to-Text.
"""

import subprocess
import json


def listen_voice() -> str:
    """
    Triggers Android's on-device speech recognition.
    In Termux, termux-speech-to-text calls Android's SpeechRecognizer directly.
    """
    try:
        proc = subprocess.run(
            ["termux-speech-to-text"],
            capture_output=True,
            text=True,
            timeout=10
        )
        if proc.returncode == 0:
            return proc.stdout.strip()
    except FileNotFoundError:
        # Fallback for testing or non-termux environments
        pass
    except Exception as e:
        print(f"[!] Ses dinleme hatasi: {e}")

    return ""


def speak(text: str) -> None:
    """
    Speaks back to the user using Android's on-device Text-to-Speech (TTS).
    """
    try:
        subprocess.run(
            ["termux-tts-speak", "-l", "tr-TR", text],
            capture_output=True,
            text=True,
            timeout=10
        )
    except Exception:
        pass


if __name__ == "__main__":
    print("[*] Ses tanima hazir.")
