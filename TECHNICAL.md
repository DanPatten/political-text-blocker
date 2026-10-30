# Political Text Blocker: technical notes

For installing and using the app, see the [README](README.md).

## How it works

1. **Notification access**: a `NotificationListenerService`
   ([`SmsNotificationListener.kt`](app/src/main/java/com/danpatten/politicalblocker/SmsNotificationListener.kt))
   watches notifications posted by SMS apps (Google Messages, Samsung Messages, etc. and whatever the default SMS
   app is).
2. **Unknown senders only**: texts from anyone in your contacts (checked via `READ_CONTACTS`), group
   conversations, and senders marked *Not political* or *Not spam* are never touched.
3. **Local model**: a binarized Naive Bayes classifier
   ([`PoliticalClassifier.kt`](app/src/main/java/com/danpatten/politicalblocker/PoliticalClassifier.kt)) trained on
   startup from [`training.tsv`](app/src/main/assets/training.tsv) plus the user's corrections, blended with a
   weighted keyword list ([`Keywords.kt`](app/src/main/java/com/danpatten/politicalblocker/Keywords.kt)) of strong
   markers like `Paid for by`, `ActBlue`, `WinRed`, `STOP2END`, `5X MATCH`, party and candidate names.
   A second model scores commercial spam (sales, promos, loan offers) against ordinary texts; it is never applied
   to one-time codes. A text with no link and no opt-out footer that reads like a conversation ("I", "my", "lol",
   "ride", "grandma"…) has its political score lowered, so a friend texting about voting isn't hidden; about 1,000
   generated personal texts in the unit tests guard this. Nothing leaves the device; the app has no network permission.
4. **Suppress**: two toggles at the top of the app, *Political texts* and *Other spam ads* (both on by default),
   choose which models apply. If the political score is ≥ 75% or the spam score is ≥ 80%, the notification is cancelled, the SMS app's own **Archive** action is fired
   (or **Mark as read** if it has none), and the message is saved to the app's **Blocked texts** list.
   Buttons are matched by semantic action first, because redacted notifications have blank button labels.
5. **Quiet mode**: a notification listener can only remove a notification after it has already popped up and
   played its sound (about 0.5 s on an emulator). If the SMS app's notification channel is set to **Silent**
   (importance below `IMPORTANCE_DEFAULT`, read from the listener's `Ranking`), the app posts its own high-importance
   alert ([`Alerts.kt`](app/src/main/java/com/danpatten/politicalblocker/Alerts.kt)) for every text it lets through,
   with the SMS app's tap and **Mark as read** intents. Political texts then never alert at all. The alert is cleared
   when the SMS app's notification goes away for more than 3 s (Messages removes and re-posts its notifications
   when it refreshes them).
6. **Auto-archive in Google Messages** (off by default): Google Messages has no archive action or API, so an
   optional accessibility service
   ([`ArchiveService.kt`](app/src/main/java/com/danpatten/politicalblocker/ArchiveService.kt)) opens its
   conversation list, long-presses the blocked sender's row (`conversation_name`), taps **Archive**
   (`action_archive`), and backs out. It waits until the phone is unlocked and the user isn't in Google Messages.
7. **Redacted notifications**: Android 15+ can hide a notification's content from listener apps
   ("Sensitive notification content hidden"), and Messages shows a text sent to your own number as your own
   message. In both cases the app reads the text from the SMS inbox instead
   ([`SmsReader.kt`](app/src/main/java/com/danpatten/politicalblocker/SmsReader.kt), `READ_SMS`), matched by the
   notification's message timestamp (`when`, not `postTime`, which changes on every re-post).
8. **Recent activity**: every decision is logged to SQLite with its reason and shown in the app (collapsed by
   default under *Recent activity → Show*).

## Permissions

| Permission | Why |
|---|---|
| Notification access | See and remove SMS notifications |
| `READ_CONTACTS` | Skip senders in your contacts |
| `READ_SMS` | Read texts whose notifications Android redacted |
| `POST_NOTIFICATIONS` | Quiet-mode alerts |
| Accessibility (optional) | Auto-archive in Google Messages |

## Building

```
./gradlew testDebugUnitTest assembleDebug     # debug APK
./gradlew assembleRelease                     # release APK (debug-signed, see app/build.gradle.kts)
```

Outputs are in `app/build/outputs/apk/`. Requires JDK 17 and Android SDK 36.

## Releasing

The README's download link is
`https://github.com/DanPatten/political-text-blocker/releases/latest/download/political-text-blocker.apk`,
which GitHub redirects to the newest release. It keeps working as long as every release:

1. Bumps `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Attaches the APK named exactly **`political-text-blocker.apk`** (rename `app-release.apk`).
3. Is a normal release, not a draft or pre-release (those are skipped by `latest`).

```
./gradlew testDebugUnitTest assembleRelease
cp app/build/outputs/apk/release/app-release.apk political-text-blocker.apk
gh release create vX.Y.Z political-text-blocker.apk --title "Political Text Blocker vX.Y.Z" --notes "..."
```

## Limitations

- An Android app can only truly archive or delete SMS if it is the default SMS app. Google Messages only offers
  **Mark as read** on its notifications, so without auto-archive blocked threads stay in the inbox, marked read.
- Auto-archive depends on Google Messages' screen layout (the view IDs above) and can break when Google redesigns
  it; blocked texts are then still hidden and marked read. It briefly opens Messages on screen.
- Without quiet mode, a political text's notification can pop up for about half a second before it's removed.
- Quiet-mode alerts don't include Reply; a reply sent from a copied action would leave a spinner only the SMS app
  can clear.
- The model is small and keyword-heavy; *Not political* corrections retrain it on the device.

## Related projects

- [IvanChanPing/sms-spam-shield](https://github.com/IvanChanPing/sms-spam-shield): a political-spam detection
  *library* for apps that are the default SMS app.
- [mattmillerai/ElectionDeflection](https://github.com/mattmillerai/ElectionDeflection): iOS political text filter.
