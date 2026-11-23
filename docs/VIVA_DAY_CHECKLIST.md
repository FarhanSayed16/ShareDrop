# ClassDrop — Viva Day Checklist

1. JDK 17+ available (`java -version`).
2. Optional: MySQL running + `schema.sql` applied (app works without it).
3. Firewall rule for TCP 8080 (private profile).
4. Enable Windows Mobile Hotspot if campus Wi-Fi isolates clients.
5. Launch: `launch-classdrop.bat` or `java -jar target\classdrop-1.0.0.jar`.
6. Confirm room code + QR on Swing dashboard; note Host Token in console/status bar.
7. Phone joins via QR / `http://192.168.137.1:8080`.
8. Demo path: join → upload → preview image → download → clipboard → Download All ZIP → host kick/remove.
9. Keep backup: project source + JAR; record a 60s demo video offline if network fails.
