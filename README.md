# Gold Tracker (India)

Android app that shows estimated Indian gold prices (24K / 22K / 18K, per gram and per 10 g)
and sends a notification when the price crosses an upper or lower limit you set.

## How the price is calculated

International spot (USD/oz, from gold-api.com) × USD/INR (open.er-api.com) ÷ 31.1035 g,
then + import duty (default 6%) + optional 3% GST + your own adjustment %.
If the live source fails it falls back to a daily XAU/INR rate.
This is an estimate; jeweller and MCX rates differ, so tune the adjustment % to match your local rate.

## Alerts

Turn on the alert, pick 24K or 22K, enter limits in ₹ per 10 g, choose how often to check
(15 min minimum, an Android limit) and tap Save. You get one notification per crossing;
it re-arms once the price moves back past the limit. Battery saver / Doze can delay checks.

## Getting the APK

- GitHub: every push runs the "Build APK" workflow; download `gold-tracker-apk` from the run's artifacts.
- Locally: open the folder in Android Studio and run, or `./gradlew assembleDebug`
  (output: `app/build/outputs/apk/debug/app-debug.apk`).

Install on the phone by opening the APK (allow "install unknown apps" when asked).
