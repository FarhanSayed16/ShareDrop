# ClassDrop Offline Cheat Sheet (Projector / Board)

**No internet required.** Files stay on the teacher laptop.

## Host (1 minute)

1. Turn **Mobile Hotspot ON** (ClassDrop → Open Hotspot Settings).  
2. ClassDrop Mode = **HOTSPOT** → wait for **OFFLINE READY**.  
3. Once: run `scripts\allow-firewall-8080.bat` as Administrator.  
4. Keep ClassDrop window open. Show QR / room code.

## Students (1 minute)

1. Join **teacher hotspot Wi‑Fi** (not Somaiya Wi‑Fi).  
2. Ignore **No Internet** — stay connected.  
3. **Turn OFF mobile data**.  
4. Scan QR **or** open `http://192.168.137.1:8080`  
5. Enter room code + name → Join → share files.

## If it fails

| Symptom | Fix |
|---|---|
| Page never loads | Mobile data OFF? On teacher hotspot? |
| Timeout | Firewall script as Admin |
| Banner NOT OFFLINE-READY | Hotspot still OFF |
| Help on phone | `http://192.168.137.1:8080/connect` |

## Proof demo

Unplug host internet / airplane uplink → hotspot ON → phone data OFF → upload still works.

## Hotspot won’t start without internet?

Some Windows PCs refuse Mobile Hotspot with no uplink. Create a **Microsoft KM-TEST Loopback Adapter**, enable ICS/sharing to it, then turn Mobile Hotspot ON. Host stays at `192.168.137.1`. See also: [FULL_OFFLINE_CONNECTION_PLAN.md](FULL_OFFLINE_CONNECTION_PLAN.md).

## Host UI buttons

- **Start Hotspot** · **Open Hotspot Settings** · **Fix Firewall** · **Copy Invite Pack** · **Export Poster** · **USB Drop Folder** · **How Students Connect**
- Dual QR: **Join Wi‑Fi** (SSID) then **Room join**

## Reverse mode (laptop can’t host hotspot)

Phone creates hotspot → PC joins it → Mode **LAN** → share `http://PC-IP:8080`. See [PHONE_HOTSPOT_REVERSE_MODE.md](PHONE_HOTSPOT_REVERSE_MODE.md).
