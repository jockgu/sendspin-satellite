# Physical device UAT checklist

Run these checks on a real Android device connected to the same local network as the Sendspin server.

## Onboarding and connection

- [ ] Clean install with one compatible server available: local-network permission is clear, discovery starts automatically, and the app connects without entering an address.
- [ ] Clean install with multiple compatible servers available: a friendly selector appears and the selected server connects.
- [ ] Clean install with no compatible server available: the app explains that no server was found and offers retry and manual setup.
- [ ] Manual setup: enter an IPv4 address only and confirm the app uses the default Sendspin endpoint.
- [ ] Manual setup: enter a complete `ws://` address with a non-default port or path and confirm it connects.

## Remembered server behaviour

- [ ] After a successful discovered connection, close and reopen the app: it reconnects automatically to the selected server.
- [ ] After a successful manual connection, close and reopen the app: it reconnects automatically.
- [ ] Disconnect from Settings, close and reopen the app: the server is remembered and reconnects automatically.
- [ ] Forget server from Settings: the active session stops; reopening the app begins discovery instead of reconnecting to the old server.
- [ ] Try a new server that fails to connect: the previous preferred server remains available after reopening the app.

## Lifecycle and recovery

- [ ] Rotate the device while discovering and while connecting: no duplicate scan or connection occurs.
- [ ] Leave the app while it is connecting: a successful connection is still remembered on the next launch.
- [ ] With the screen off, temporarily disconnect Wi-Fi and restore it: playback recovers automatically.
- [ ] Restart the Sendspin server while connected: the client reconnects and resumes cleanly.
- [ ] Change the output route (Bluetooth, wired, or USB DAC where available): playback recovers without stale audio.

## Final smoke test

- [ ] Leave the device connected and playing for at least 30 minutes with the screen off; confirm continuous playback and no unexpected disconnect.
