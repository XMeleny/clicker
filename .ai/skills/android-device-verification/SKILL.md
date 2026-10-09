---
name: android-device-verification
description: Verify Android app changes in this project on a connected physical device, especially UI changes that need screenshots or installation.
---

# Android Device Verification

Use the connected physical Android device for installation, interaction, and screenshots. Check `adb devices -l` and target its serial explicitly when more than one device is listed.

Do not start an Android emulator for this project unless the user explicitly asks for one. Emulators consume too much CPU on this machine. If the physical device is offline, try reconnecting it; if it remains unavailable, complete build and other available checks, then report what could not be verified on-device.
