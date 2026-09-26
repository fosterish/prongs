# Prongs

A chromatic tuner for Android with a flexible, but refreshingly no-nonsense
interface. Track the nearest semitone, or any pitches you select with the
keyboard. Long-press a key to hear that tone.

So many things get crammed into tuner apps that musicians don't care about in any
practical sense. No one's checking the spectrograph when their A goes out. No one's
using the meantone, 7 string guitar preset.

Prongs aims to be ascetically aesthetic and frugally functional.

## Features

- Chromatic by default, or listens only for the notes you select
- Long-press any key for an audible tone
- Adjustable concert A, 415-466 Hz
- Light and dark theming, to follow your preference or the system default
- Separate layouts for portrait and landscape
- No advertisements, trackers, or analytics, and no `INTERNET` permission
- Loads instantly, no bloated frameworks, ~60 kB binary.

## Installing

Grab the APK from [Releases](https://github.com/fosterish/prongs/releases) and install
it with `adb install prongs.apk` or your file manager.

`RECORD_AUDIO` is the only permission requested, and it is requested on first launch.

## Building

Requires Android SDK 36. The Gradle build pins a Java 21 toolchain, so any JDK Gradle
can provision from will do.

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/
./gradlew installDebug      # debug builds install alongside as net.fosterish.prongs.debug
./gradlew test lint
```

### Signing

Releases are signed with a 4096-bit RSA key kept outside this repo. `assembleRelease`
picks it up from a gitignored `keystore.properties` in the project root:

```properties
storeFile=/path/to/prongs-release.p12
storeType=PKCS12
keyAlias=prongs
storePassword=…
keyPassword=…
```

Without that file the build still succeeds and emits `app-release-unsigned.apk`, so a
fresh clone needs no secrets.

Released APKs carry an APK Signature Scheme v2 signature from this certificate:

```
SHA-256 70:7C:47:E8:F7:2B:D0:79:2D:C3:8C:F5:90:B5:94:31:A3:60:1C:C7:94:67:9E:55:B3:F7:A6:AC:B8:09:C7:FF
```

Check a download against it with `apksigner verify --print-certs prongs.apk`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
