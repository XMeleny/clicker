---
name: android-device-verification
description: Choose the build and device verification path for completed Android changes in this project based on whether the designated physical device is connected.
---

# Android Device Verification

For completed app code changes, check `adb devices -l` for device `SG4DGUTK5L4T6DMV` in the `device` state.

- When it is connected, run `:app:runDebugWithoutForceStop` from the Gradle wrapper. This task compiles, installs, and launches the debug app without an explicit force-stop. Set `ANDROID_SERIAL=SG4DGUTK5L4T6DMV` for the command so installation and launch target this device. Then perform relevant UI checks on the physical device.
- When it is absent or offline, commit the completed change without running Gradle compilation, build, install, or launch tasks. Report that device verification was skipped.

Do not start an Android emulator unless the user explicitly requests one. A task-specific user instruction to skip or run a check takes precedence over this default workflow.
