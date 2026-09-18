# Contributing

Development notes for working on this plugin. Users should install it from the RuneLite Plugin Hub
(search "Solus"); everything below is for building from source.

## Build

```
./gradlew build
```

The jar lands in `build/libs/`.

## Run a development client

```
./gradlew runClient
```

This compiles the plugin and launches a development client with it loaded, via the test launcher in
`src/test/java/com/droplogger/ClanManagementPluginTest.java`.

`def runeLiteVersion` in `build.gradle` must match the current live RuneLite release, otherwise the
development client is rejected by the game with `error_game_js5connect_outofdate`. The current
version is listed at https://repo.runelite.net/net/runelite/client/maven-metadata.xml.

## Tests

```
./gradlew test
```

## Notes for changes

- The plugin talks only to the clan platform API host it is compiled with. Never call a URL taken
  from a server response, and never post to Discord from the client; Discord posting is server-side.
- Bank and item data must not leave the client. Requirements that depend on owning an item are
  evaluated locally and only the pass/fail result is sent.
- All data sharing is opt-in and off by default. Keep it that way.
