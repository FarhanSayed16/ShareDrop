# Offline Connectivity Hardening — Plan & Decisions

**Status:** Active implementation  
**Problem:** “Offline ClassDrop” is not broken at the HTTP server layer — it fails in the **student phone network path**. Phones on a hotspot with no internet often refuse to use that Wi‑Fi for browsing, or QR links open over cellular and never reach `192.168.137.1`.

---

## 1. What must be true for offline join to work

| Requirement | Why |
|---|---|
| Host runs ClassDrop bound to `0.0.0.0:8080` | Already true |
| Host Windows **Mobile Hotspot ON** | Creates private subnet `192.168.137.x` |
| Students join **that** Wi‑Fi SSID | Same L2/L3 network as host |
| Students open `http://192.168.137.1:8080/...` **via that Wi‑Fi** | Not via Somaiya Wi‑Fi / cellular |
| Firewall allows inbound TCP 8080 | Otherwise phones time out |
| Student accepts “No Internet” on hotspot | Normal when host is not sharing uplink |

If any row fails, students cannot join — even if ClassDrop UI looks fine.

---

## 2. Real failure modes (why it “feels” offline is broken)

1. **Students stay on campus Wi‑Fi** → client isolation → cannot reach host laptop.  
2. **QR / link opens on cellular** while phone is also on hotspot → request never hits `192.168.137.1`.  
3. **Hotspot off** → ClassDrop still shows a LAN IP that phones cannot use.  
4. **Firewall blocks 8080** → connect timeout.  
5. **Phone “No Internet” UX** → student switches back to Somaiya Wi‑Fi.  
6. **Host expects magic** — offline ≠ zero radio; it means **no public internet**, not “no Wi‑Fi”.

---

## 3. Product decision (locked)

| Decision | Choice |
|---|---|
| Primary classroom mode | **HOTSPOT offline hub** |
| Internet Share | Optional escape hatch only |
| Student install | Still **zero** (browser only) |
| Hard requirement we document | Students must join host hotspot (or same open LAN) |
| Strongest phone tip | Temporarily **turn off mobile data** while joining |

We will **not** claim phones can stay only on isolated Somaiya Wi‑Fi with a fully offline host.

---

## 4. Hardening workstream (build now)

| ID | Change | Effect |
|---|---|---|
| H1 | Captive / connectivity probe endpoints | Phones probing local gateway get clean responses; captive browser can land on ClassDrop |
| H2 | `/network-status` JSON | Host + students can diagnose path |
| H3 | Host **“How students connect”** guided dialog | Clear 5-step classroom script |
| H4 | Live hotspot readiness refresh | Detect when hotspot turns on/off |
| H5 | Student web troubleshooting panel | “Turn off mobile data”, type URL manually |
| H6 | Open Windows Mobile Hotspot settings + firewall script | Faster host setup |
| H7 | Copy full invite pack (SSID tip + URL + code) | Teacher can paste to projector/board |

---

## 5. Classroom script (must work with zero internet)

1. Host: Mobile Hotspot ON (name e.g. `ClassDrop-Hub`).  
2. Host: Launch ClassDrop → Mode **HOTSPOT** → see **OFFLINE READY**.  
3. Host: Allow firewall (`scripts/allow-firewall-8080.bat` as Admin once).  
4. Students: Connect to host hotspot Wi‑Fi (ignore “No Internet”).  
5. Students: **Turn off mobile data** (recommended).  
6. Students: Scan QR **or** type `http://192.168.137.1:8080` → enter code + name → Join.  
7. Host watches device list — first student proves the path.

---

## 6. Success criteria

- [ ] Host with airplane-mode uplink (or Wi‑Fi off) still serves files on hotspot.  
- [ ] Phone with mobile data OFF joins via hotspot QR/link.  
- [ ] If hotspot OFF, UI shows **not ready** (not a fake green state).  
- [ ] Host can open connect guide and copy invite in one click.  

---

## Related

- [OFFLINE_SHARING_PLAN.md](OFFLINE_SHARING_PLAN.md) — strategy  
- [INTERNET_SHARE_GUIDE.md](INTERNET_SHARE_GUIDE.md) — online escape hatch  
- [LOCAL_RUN_GUIDE.md](LOCAL_RUN_GUIDE.md) — hotspot setup  
