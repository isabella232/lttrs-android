# Ltt.rs for Android

Ltt.rs (pronounced Letters) is a proof of concept email ([JMAP](https://jmap.io/)) client currently
in development. It makes heavy use of [Android Jetpack](https://developer.android.com/jetpack/) for
a more maintainable code base than some of the preexisting Android email clients.

![screenshot of Ltt.rs for Android](https://gultsch.de/files/lttrs-android.png)

If the above screenshots don’t do enough to convince you, you can watch this
[short video on YouTube](https://www.youtube.com/watch?v=ArCuudFwJX4).

### Features & Design considerations

* **Heavily cached** but not fully offline capable. Ltt.rs makes use of JMAP’s great caching capabilities.
  However actions, such as marking a thread as read, need a round-trip to the server until their
  consequences like unread count are updated. Ltt.rs will ensure that the action itself won’t get lost even
  if performed while momentarily offline.
* **No settings** aside from account setup. Settings invite
  [feature creep](https://en.wikipedia.org/wiki/Feature_creep) and make the app hard to maintain. Ltt.rs
  aims to support one specific work flow. Users who desire a different work flow may find
  [K-9 Mail](https://github.com/k9mail/k-9) or [FairEmail](https://github.com/M66B/FairEmail) more suitable.
* **Minimal external dependencies**. Third party libraries are often of poor quality and end up being
  unmaintained. Therfore we will only rely on well known, well tested libraries from reputable vendors.
* **[Autocrypt](https://autocrypt.org/) as a first class feature**¹. With its strict UX guidelines autocrypt
  fits right into Ltt.rs.
* Ltt.rs is **based on [jmap-mua](https://github.com/iNPUTmice/jmap)**, a headless email client, or a
  library that handles everything an email client would aside from data storage and UI. There is also
  [lttrs-cli](https://github.com/iNPUTmice/lttrs-cli) which uses the same library.
  
¹: Planned feature.

### Try it

JMAP servers are currently rare. As of February 2020 you need to compile 
[Cyrus](https://github.com/cyrusimap/cyrus-imapd) from git. If you are looking for providers
[Fastmail](https://www.fastmail.com/) is the only known option.
