# Reverse Offline Mode — Phone Hotspot, PC as Client

Use this when the **laptop cannot create** a Mobile Hotspot (no Wi‑Fi adapter / driver limits), but a phone can.

## How it works

1. Student/teacher phone: enable **Personal Hotspot / Mobile Hotspot**.  
2. Host Windows PC: join that phone Wi‑Fi.  
3. ClassDrop Mode = **LAN** (banner shows **REVERSE OFFLINE** when Android `192.168.43.x` or iPhone `172.20.10.x` is detected).  
4. Share URL becomes `http://<PC-IP-on-phone-hotspot>:8080/r/CODE`.  
5. Other students join the **same phone hotspot**, turn OFF their own mobile data if needed, open the URL/QR.

Files still stay local to the ClassDrop host — no cloud.

## Warnings

- While the PC is on the phone hotspot, Windows may use **phone mobile data** for updates. Set the Wi‑Fi as **Metered** in Windows.  
- The phone that hosts the hotspot is the Wi‑Fi AP; ClassDrop still runs on the PC.  
- Prefer **PC Mobile Hotspot** (normal offline mode) when available — reverse mode is a fallback.

## Related

- [FULL_OFFLINE_CONNECTION_PLAN.md](FULL_OFFLINE_CONNECTION_PLAN.md)  
- [OFFLINE_CHEAT_SHEET.md](OFFLINE_CHEAT_SHEET.md)  
