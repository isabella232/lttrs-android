# Ltt.rs for Android

Ltt.rs (pronounced Letters) is a proof of concept email ([JMAP](https://jmap.io/)) client currently
in development. It makes heavy use of [Android Jetpack](https://developer.android.com/jetpack/) for
a more maintainable code base than some of the preexisting Android email clients.

![screenshot of Ltt.rs for Android](https://gultsch.de/files/lttrs-android.png)

If the above screenshots don’t do enough to convince you, you can watch this
[short video on YouTube](https://www.youtube.com/watch?v=ArCuudFwJX4).

## jmap-mua

Ltt.rs is based on [jmap-mua](https://github.com/iNPUTmice/jmap), which is basically a headless
email client, or a library that handles everything an email client would aside from data storage
and UI. There is also [lttrs-cli](https://github.com/iNPUTmice/lttrs-cli) which uses the same
library.

## Try it

JMAP servers are currently rare. As of Februrary 2020 you need to compile 
[Cyrus](https://github.com/cyrusimap/cyrus-imapd) from git. If you are looking for providers
[Fastmail](https://www.fastmail.com/) is the only known option.
