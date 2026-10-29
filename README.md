# Political Text Blocker for Android

Tired of campaign and PAC texts from numbers you don't know? This Android app quietly hides them, so they
never buzz your phone. Texts from people in your contacts are never touched.

Everything happens on your phone. The app never sends your messages anywhere.

## Download

**[Download the latest version (APK)](https://github.com/DanPatten/political-text-blocker/releases/latest/download/political-text-blocker.apk)**,
or see [all releases](https://github.com/DanPatten/political-text-blocker/releases).

## Install

1. Open the downloaded file on your Android phone and tap **Install**. If your phone asks, allow installs from
   your browser or files app.
2. Open **Political Text Blocker**.

## Set up

Because the app isn't from the Play Store, Android blocks its most important permission at first. You'll see
**"App was denied access"** or **"Restricted setting"**. This is normal, and you only have to unlock it once:

1. In the app, tap **Grant** next to *Notification access*, then tap *Political Text Blocker*. Android shows the
   "denied access" message. Tap **OK**. (This first try is what makes the unlock option appear.)
2. Open your phone's **Settings → Apps → Political Text Blocker**. In the app, you can also tap **App info**.
3. Tap **⋮** in the top-right corner, then **Allow restricted settings**, and confirm with your PIN or fingerprint.
   - Samsung: the **⋮** menu is on the same *App info* screen, top right.
   - Don't see **⋮**? Do step 1 again first; the option only appears after Android has blocked the app once.
4. Go back to the app, tap **Grant** next to *Notification access* again, and switch on
   *Political Text Blocker*. It works this time.
5. Tap **Grant** next to *Contacts, SMS & notifications* and allow everything it asks for.
6. **Recommended:** go to **Settings → Apps → Messages → Notifications** and set *Incoming messages* to
   **Silent** (not Off). Political texts then never make a sound or pop up, and this app alerts you for
   every other text instead.

That's it. Blocked texts are marked as read and listed in the app.

## Try it

- Paste any text into the box at the top and tap **Test** to see whether it would be blocked.
- Or enter your own phone number, tap **Send test text in 10s**, and close the app. A sample political text is
  sent to you as a normal text message and should arrive without a notification. (This won't work if your own
  number is saved in your contacts.)

## Good to know

- **Something got blocked by mistake?** Tap **Not political** on it in the app. That sender won't be blocked
  again, and the app learns from it.
- **Wondering why a text did or didn't get blocked?** Check **Recent activity** in the app.
- **Want blocked texts archived, not just marked read?** Turn on *Auto-archive in Google Messages* and allow it
  under Accessibility (it needs the same *Allow restricted settings* step as above). It works by briefly opening
  Messages after you unlock your phone.

Technical details are in [TECHNICAL.md](TECHNICAL.md).
