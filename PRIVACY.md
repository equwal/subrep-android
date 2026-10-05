# Privacy policy of Subrep for Android

This policy is for the Android app Subrep (package `com.honjimaku.subrep`):
the build on Google Play, and the builds on GitHub and F-Droid. Contact:
support@honjimaku.com.

## Captions on the phone

When Whisper on the phone makes the captions, the sound stays on the phone.
The app sends no sound.

## Data that leaves the phone

- **Captions, when "Share the captions by link" is on.** The app sends each
  caption line to the relay of honjimaku.com. The relay gives the lines to the
  people who open your link. It keeps only the last 40 lines of each link, in
  memory, for new viewers. Turn off the share to stop it, or press "New link"
  to stop the old link.
- **Speech, only when you choose the cloud captions.** The app sends each
  piece of speech to subread.space, which gives it to DeepInfra for the text.
  subread.space keeps neither the sound nor the text. DeepInfra says that it
  does not store them
  ([data privacy](https://docs.deepinfra.com/account/data-privacy)).
  subread.space takes the length of each piece from your hours.
- **An account.** When you choose the cloud captions, the app asks
  subread.space for the hours of your account. The first time that you choose
  them, subread.space makes the account. With the captions on the phone, the
  app does not contact subread.space. The account has a random id (`acct_...`)
  and a random device token.
  It holds your hours. It has no name, email address or phone number.
- **Purchases.** In the Google Play build, Google Play takes the payment. The
  app sends the purchase token to subread.space. subread.space asks Google Play
  if the purchase is paid, and then adds the hours. It keeps the product, the
  hours, the Google Play order number, the time and a hash of the token. If
  Google Play refunds a purchase, subread.space takes back its hours. In the
  GitHub and F-Droid builds, Stripe takes the payment on subread.space (see
  the [terms of subread.space](https://subread.space/terms.html)).
- **The speech model.** The app downloads the model from Hugging Face.
  Hugging Face sees the IP address of the phone, as for each download.

## What the app does not do

The app shows no ads. It has no analytics and no tracking. We do not sell
data. We give data only to the services above, for the work above: DeepInfra
for the cloud text, Google Play or Stripe for the payment.

## Permissions

- Screen capture and the microphone permission: for the sound of other apps,
  or of the microphone. Android asks each time that the captions start, and
  shows a notification while the capture runs.
- Internet: for the model download, the share link and the cloud captions.
- Notifications: for the notification with the "Stop" button.

## Delete your data

Write to support@honjimaku.com with the account id that the app shows. We
delete the account and its hours. We keep only the purchase records that the
law tells us to keep. To delete the data on the phone, uninstall the app.

## Changes

A change to this policy is a change to this file. The history of the file
shows each change.
