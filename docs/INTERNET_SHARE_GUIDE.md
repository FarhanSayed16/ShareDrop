# ClassDrop — Internet Share Guide

Use **Internet Share** when students are **not** on your laptop hotspot/LAN (for example Somaiya campus Wi-Fi or mobile data) but still need to open the ClassDrop QR/link.

## Local vs Internet

| Mode | Who can join | Share URL shape | Passphrase |
|------|----------------|-----------------|------------|
| **LAN / HOTSPOT** | Same hotspot or LAN only | `http://192.168.x.x:8080/r/CODE` | Optional |
| **INTERNET** | Anyone who can reach the public HTTPS URL | `https://….trycloudflare.com/r/CODE` | **Required** |

Internet Share does **not** move ClassDrop to the cloud. Your laptop still runs the server; a temporary HTTPS tunnel forwards public traffic to `127.0.0.1:8080`.

## Prerequisites

1. Host laptop has **internet uplink** (Wi-Fi, Ethernet, or phone tether) while sharing.
2. ClassDrop is running (Swing host UI).
3. Either:
   - **cloudflared** installed and on `PATH`, or
   - A manual public base URL (ngrok / Cloudflare) set in `public.base.url` or pasted when prompted.

Install Cloudflare quick tunnel CLI:  
https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/

## Host steps (demo)

1. Start ClassDrop on the host laptop.
2. Enter a **passphrase** (min 4 characters) in the Passphrase field.
3. Switch Mode to **INTERNET**.
4. Wait for the public HTTPS link (or paste a manual base URL if cloudflared is missing).
5. Share the URL / QR with students.
6. When finished, switch back to LAN/HOTSPOT or **End Session** — public access stops when the tunnel/session ends.

## Student steps

1. Open the HTTPS link (or scan the updated QR) on **any** network (campus Wi-Fi, home Wi-Fi, cellular).
2. Enter the 6-digit room code (prefilled from `/r/CODE` if present).
3. Enter the **room passphrase** from the host.
4. Join and download/upload as usual.

## Config keys (`application.properties`)

```properties
internet.share.enabled=false
public.base.url=
tunnel.command=cloudflared
room.passphrase=
internet.session.timeout.minutes=60
```

- `public.base.url` — skip auto tunnel; always advertise this HTTPS base.
- `tunnel.command` — binary name/path for cloudflared.
- `room.passphrase` — optional default prefilled in the Swing field.
- Internet sessions auto-expire sooner (default **60 minutes**) than local sessions.

## Security notes

- Internet Share makes the room reachable **beyond the classroom**. Always set a passphrase.
- Existing token auth, file size limits, extension blocklist, and brute-force throttle still apply.
- Stop ClassDrop or End Session when the lecture ends so the public link stops working.
- Prefer Local/Hotspot when everyone is already on your hotspot — no public exposure needed.

## Campus scenarios

| Situation | Recommended mode |
|-----------|-------------------|
| Students connected to your Windows hotspot | HOTSPOT |
| Same lab LAN / ethernet switch | LAN |
| Students on Somaiya Wi-Fi / cellular; host has uplink | INTERNET |
| Host has **no** internet at all | Local only — outside phones cannot reach a private IP |

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Mode switch asks for passphrase | Enter ≥4 characters, then choose INTERNET again |
| “cloudflared was not found” | Install cloudflared **or** paste an ngrok/Cloudflare HTTPS base URL |
| Public link opens but join fails | Confirm host ClassDrop is still running; check passphrase |
| QR still shows `192.168…` | Confirm Mode badge shows **INTERNET** and a public URL appears in the share field |
| Outside phones never connect | Host needs internet uplink; tunnel cannot invent a path without it |
