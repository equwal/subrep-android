# Subrep Streamer

Live captions of the sound of an Android device, with the
[Subrep](https://honjimaku.com/subrep/) engine on your computer.

The app captures the sound of the apps on the device (a video, a game, a
browser) or the microphone. It streams the sound to `subrep serve` on your
computer. The captions come back to the app, to your share link on
`honjimaku.com`, and to [SubRead Overlay](https://github.com/equwal/subread-overlay)
over the player, where a tap on a word opens the dictionary.

Whisper does not run on the phone. The computer does the speech recognition
with the models and the languages of Subrep, and the phone only sends sound.
A phone from 2019 works the same as a new one.

## Setup

On the computer, once:

1. Start the engine for the language of the video:

   ```
   subrep serve --host 0.0.0.0 --port 8794 --lang ja
   ```

2. Let the phone reach port 8794. On Windows, as administrator:

   ```
   netsh advfirewall firewall add rule name="Subrep engine" dir=in action=allow protocol=TCP localport=8794
   ```

3. Find the address of the computer on the network: `ipconfig` on Windows,
   `ip addr` on Linux. For example `192.168.0.9`.

On the phone:

1. Install the app. Install SubRead Overlay too, and allow its two steps.
2. Enter the engine as `address:port`, for example `192.168.0.9:8794`.
3. Enter the language.
4. Choose the sound: the apps on this device (Android 10 or later), or the
   microphone.
5. Press "Start the captions". Android asks once for the capture of the
   screen, and once for the microphone. The microphone permission also
   covers the sound of the apps.
6. Start the video. The captions come to the app, to the link and to the
   overlay.

The notification has a "Stop" button.

## What the device cannot capture

- An app that refuses the capture of its sound: a video app with DRM, and
  some music apps. Android then gives silence. Use the microphone.
- Sound from another device over Bluetooth. A phone is a Bluetooth source,
  not a speaker, so it cannot receive sound this way without root.

## The share link

The link is `https://honjimaku.com/subrep/w/<room>`, the same relay as the
desktop app. It stays the same across restarts. "New link" makes another
one, and the old one stops.

## Protocol

The engine protocol is that of `subrep serve`: a JSON hello
(`{"type":"hello","sampleRate":16000,"lang":"ja","source":"android"}`), then
binary frames of 16-bit little-endian mono PCM, and captions back as
`{"type":"partial"|"final"|"clear","text":...}`.

The relay protocol is that of `subrep.share`: a websocket to
`wss://honjimaku.com/subrep/pub/<room>`, a hello with the room secret, then
the captions as they came from the engine.

The overlay protocol is the `line` method of the content provider
`content://space.subread.overlay.player`, with the extra `partial`.

## Build

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For a release, set `SUBREP_KEYSTORE_FILE`, `SUBREP_KEYSTORE_PASSWORD` and
`SUBREP_KEY_ALIAS` (default `subrep`), then `./gradlew :app:assembleRelease`.
