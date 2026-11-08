# abraxas-mobile

ABRASAX mobile companion. Creator: Sir Charles Spikes (basedgod55hjl).
Core AI: Kairos.

## What works (measured 2026-11-08)
- S25 (SM-S938U, Android 17, SD8 Elite) paired over adb.
- UsbFfs reverse tunnels: gateway :2645, UI :8090, LM Studio :1234,
  phone API :8788 (abneo serve; default provider = tiles-restored LFM).
- Phone-side curl -> gateway /health: ok (device-side evidence).
- Phone-side chat -> PC GPU -> tiles-restored model -> reply (device-side).
- com.abraxas.companion v1 APK built (app/build_apk.ps1, no gradle),
  installed, launched, foreground-verified on device.

## Layout
- app/ - hand-assembled APK sources + build script
- device_registry.json - measured device facts
- BOND_MANIFEST.{json,sig} - signed by the TPM-sealed soul
