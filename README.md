# Gold Tracker (India)

Android app that shows estimated Indian gold prices (24K / 22K / 18K / 14K, per gram and per 10 g)
with the rise/fall versus the previous close, a trend chart (1D / 1W / 1M / 3M / 6M, any karat),
a notification when the price crosses an upper or lower limit you set,
a home-screen widget, a jewellery price calculator and a "My gold" holdings tracker.

## How the price is calculated

International spot (USD/oz, from gold-api.com) × USD/INR (open.er-api.com) ÷ 31.1035 g,
then + import duty (default 15%: 10% BCD + 5% AIDC since 13 May 2026) + optional 3% GST
(off by default, as jewellers and IBJA quote rates before GST) + your own adjustment %.
If the live source fails it falls back to a daily XAU/INR rate.
This is an estimate; jeweller and MCX rates differ, so tune the adjustment % to match your local rate.

## Trend and daily change

1W to 6M charts and the previous close come from the daily XAU/INR archive of
[currency-api](https://github.com/fawazahmed0/exchange-api) (one snapshot per day, around 05:30 IST).
Past days are cached on the phone so each is downloaded once; 3M and 6M use every 2nd / 3rd day.
There is no free intraday Indian source, so the 1D chart is built from the app's own price checks,
which run in the background at the chosen interval even when alerts are off.

## Calculator and My gold

The calculator estimates a jewellery bill: weight × rate + making charges %, plus 3% GST on both.
My gold values what you own at today's rate (before GST) and shows profit/loss against what you paid.
Holdings are stored only on the phone.

## Widget

Long-press the home screen → Widgets → Gold Tracker. It shows the rate per 10 g for the karat you pick in the app and the change
from the previous close, and updates after every background check.

## Alerts

Turn on the alert, pick 24K or 22K, enter limits in ₹ per 10 g, choose how often to check
(15 min minimum, an Android limit) and tap Save. You get one notification per crossing;
it re-arms once the price moves back past the limit. Battery saver / Doze can delay checks.

## Getting the APK

- GitHub: every push runs the "Build APK" workflow; download `gold-tracker-apk` from the run's artifacts.
- Locally: open the folder in Android Studio and run, or `./gradlew assembleDebug`
  (output: `app/build/outputs/apk/debug/app-debug.apk`).

Install on the phone by opening the APK (allow "install unknown apps" when asked).
