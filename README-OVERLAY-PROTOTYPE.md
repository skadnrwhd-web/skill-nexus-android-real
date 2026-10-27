# Skill Nexus system overlay prototype

This branch adds an Android AccessibilityService-based overlay prototype.

## What it does
- Attempts to show a top overlay over selected AI apps.
- Uses visible accessibility-event text to choose a *possible* skill category.
- Auto-hides after approximately 2.8 seconds.
- The overlay explicitly says detection is heuristic, not a confirmed tool call.

## Important limitations
- Claude, ChatGPT, and Meta AI do not generally expose their internal tool-call events to third-party apps. This is only best-effort inference from visible accessibility events.
- Package IDs in `ToolOverlayAccessibilityService.kt` are guesses and may need to be verified on the target phone.
- Android requires the user to explicitly enable the accessibility service in Settings. Overlay behavior varies by Android version and manufacturer.
- This prototype has not been validated on a physical device. Build/CI status must be checked before treating it as runnable.

## Enable on device
1. Install the debug APK.
2. Open Android Settings → Accessibility → Installed/Downloaded apps (wording varies).
3. Enable **Skill Nexus REAL**.
4. Open one of the monitored AI apps and observe whether the brief overlay appears.

No screen text is stored or transmitted by this prototype.
