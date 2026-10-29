# Political Text Blocker (Android)

A small Android app that silently hides political campaign / PAC / get-out-the-vote text messages
from **unknown senders**, using a fully on-device model. No network access, no default-SMS-app role.

## How it works

1. **Notification access**: a `NotificationListenerService` watches notifications posted by SMS apps
   (Google Messages, Samsung Messages, etc. and whatever the default SMS app is).
2. **Unknown senders only**: texts from anyone in your contacts (checked via `READ_CONTACTS`),
   group conversations, and senders you've allow-listed are never touched.
3. **Local model**: a binarized Naive Bayes classifier trained on startup from
   [`training.tsv`](app/src/main/assets/training.tsv), blended with a weighted keyword list
   ([`Keywords.kt`](app/src/main/java/com/danpatten/politicalblocker/Keywords.kt)) of strong markers like
   `Paid for by`, `ActBlue`, `WinRed`, `STOP2END`, `5X MATCH`, party and candidate names.
4. **Suppress + archive**: if the score is ≥ 75%, the notification is cancelled, the SMS app's own
   **Archive** action is fired (or **Mark as read** if the app has no archive action), and the message
   is saved to the app's **Suppressed messages** list.
5. **Feedback**: tap **Not political** on a suppressed message to allow-list that sender and retrain the
   model with the text as a negative example.

## Setup

1. Install the APK and open the app.
2. Tap **Grant** next to *Notification access* and enable *Political Text Blocker*.
3. Tap **Grant** next to *Contacts* so known contacts are skipped.
4. Use the test box to try the model on any text.

## Building

```
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and Android SDK 36.

## Limitations

- An Android app can only truly archive or delete SMS if it is the default SMS app. This app instead uses
  the SMS app's own notification actions, so whether it's archived or only marked read depends on the messaging app.
- The notification can briefly flash before it's cancelled (the listener runs right after it's posted).
- The model is small and keyword-heavy; use **Not political** to correct false positives.

## Related projects

- [IvanChanPing/sms-spam-shield](https://github.com/IvanChanPing/sms-spam-shield): a political-spam
  detection *library* for apps that are the default SMS app.
- [mattmillerai/ElectionDeflection](https://github.com/mattmillerai/ElectionDeflection): iOS political text filter.
