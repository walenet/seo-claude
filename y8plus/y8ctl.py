#!/usr/bin/env python3
"""
y8ctl - controllo da PC delle cuffie 1Mii Y8 / Y8+ via Bluetooth LE.

Protocollo ricostruito da YeeMall 2.8.7 (com.ymconnect). Vedi PROTOCOLLO.md.

Requisiti:  pip install bleak
Esempi:
    python y8ctl.py scan
    python y8ctl.py status
    python y8ctl.py anc nc            # off | nc | transparency
    python y8ctl.py eq bass           # default pop bass rock classic jazz country custom
    python y8ctl.py band 0 +4         # Y8 classico: banda 0..9, guadagno in dB
    python y8ctl.py ldac on
    python y8ctl.py raw GA 00         # comando grezzo: 2 lettere + payload hex
"""
import argparse
import asyncio
import sys

SERVICE = "0000faa0-0000-1000-8000-00805f9b34fb"
WRITE = "0000faa1-0000-1000-8000-00805f9b34fb"
NOTIFY = "0000faa2-0000-1000-8000-00805f9b34fb"
NAMES = ("Y8+", "Y8")

ANC = {"off": 0, "nc": 1, "transparency": 2}
ANC_R = {0: "normale (ANC off)", 1: "cancellazione rumore", 2: "trasparenza", 3: "sconosciuto"}
EQ = {"default": 0, "pop": 1, "bass": 2, "rock": 3, "classic": 4, "jazz": 5, "country": 6, "custom": 7}
EQ_R = {v: k for k, v in EQ.items()}
BANDS_10 = ["32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"]


def frame(cmd: str, data: bytes = b"\x00") -> bytes:
    """AA | cmd[0] | cmd[1] | len | data... | somma di tutti i byte precedenti & 0xFF"""
    assert len(cmd) == 2
    body = bytes([0xAA]) + cmd.encode("ascii") + bytes([len(data)]) + data
    return body + bytes([sum(body) & 0xFF])


def s8(b: int) -> int:
    return b - 256 if b > 127 else b


def decode(pkt: bytes) -> str:
    if len(pkt) < 5 or pkt[0] != 0xAA:
        return f"?? {pkt.hex(' ')}"
    cmd = pkt[1:3].decode("ascii", "replace")
    d = pkt[4:-1] if len(pkt) > 5 else pkt[4:]
    out = f"{cmd} {pkt.hex(' ')}"
    try:
        if cmd == "GA":
            out += (f"\n  ANC: {ANC_R.get(d[0], d[0])}\n  EQ: {EQ_R.get(d[1], d[1])}"
                    f"\n  musica: {'play' if d[2] else 'pausa'}\n  LDAC: {d[3]}\n  multipoint: {d[4]}")
            if len(d) > 5:
                out += f"\n  game mode: {d[5]}"
        elif cmd == "BV":
            out += f"\n  batteria: {d[0]}%" + ("" if d[1] else " (n/d)")
        elif cmd == "SF":
            out += f"\n  firmware: {d[0]}.{d[1]}.{d[2]}"
        elif cmd == "GE":
            if len(d) >= 10:
                out += "\n  EQ custom (10 bande, Y8): " + ", ".join(
                    f"{f}={s8(g)}dB" for f, g in zip(BANDS_10, d[:10]))
            else:
                out += f"\n  preset {d[0]} | 8 bande grezze (Y8+ U): {[s8(x) for x in d[1:9]]}"
        elif cmd == "TA":
            out += f"\n  timer spegnimento: {d[0]} min"
        elif cmd in ("AC", "EQ", "LC", "OM", "GM", "PL", "LS"):
            out += f"\n  valore: {d[0]}"
    except IndexError:
        pass
    return out


async def find(addr):
    from bleak import BleakScanner
    if addr:
        return addr
    print("Cerco le cuffie...", file=sys.stderr)
    for dev in await BleakScanner.discover(timeout=6.0):
        if dev.name and dev.name.strip() in NAMES:
            print(f"Trovate: {dev.name} {dev.address}", file=sys.stderr)
            return dev.address
    sys.exit("Cuffie non trovate. Accendile e chiudi l'app ufficiale (una sola connessione BLE alla volta).")


async def run(addr, packets, wait=1.5):
    from bleak import BleakClient
    addr = await find(addr)
    async with BleakClient(addr) as c:
        await c.start_notify(NOTIFY, lambda _, data: print("<<", decode(bytes(data))))
        for p in packets:
            print(">>", p.hex(" "))
            await c.write_gatt_char(WRITE, p, response=False)
            await asyncio.sleep(0.3)
        await asyncio.sleep(wait)


async def scan():
    from bleak import BleakScanner
    for dev, adv in (await BleakScanner.discover(timeout=8.0, return_adv=True)).values():
        mark = " <==" if (dev.name or "").strip() in NAMES else ""
        print(f"{dev.address}  {dev.name!s:24} rssi={adv.rssi} uuids={adv.service_uuids} "
              f"manuf={ {k: v.hex() for k, v in adv.manufacturer_data.items()} }{mark}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-a", "--address", help="indirizzo BLE (salta la scansione)")
    sp = ap.add_subparsers(dest="cmd", required=True)
    sp.add_parser("scan")
    sp.add_parser("status")
    sp.add_parser("version")
    sp.add_parser("battery")
    sp.add_parser("geteq")
    sp.add_parser("monitor").add_argument("seconds", type=int, nargs="?", default=60)
    sp.add_parser("anc").add_argument("mode", choices=ANC)
    sp.add_parser("eq").add_argument("preset", choices=EQ)
    b = sp.add_parser("band", help="Y8 classico: imposta una banda (0-9) dell'EQ custom")
    b.add_argument("index", type=int)
    b.add_argument("gain", type=int)
    u = sp.add_parser("eq8", help="Y8+ U: scrive le 8 bande in un colpo (valori grezzi, vedi PROTOCOLLO.md)")
    u.add_argument("values", type=int, nargs=8)
    u.add_argument("--slot", type=int, default=0)
    sp.add_parser("eqreset")
    for n in ("ldac", "game", "multipoint"):
        sp.add_parser(n).add_argument("state", choices=["on", "off"])
    sp.add_parser("timer").add_argument("minutes", type=int, help="0 = annulla")
    sp.add_parser("play")
    sp.add_parser("pause")
    sp.add_parser("next")
    sp.add_parser("prev")
    r = sp.add_parser("raw")
    r.add_argument("code")
    r.add_argument("payload", nargs="?", default="00")
    a = ap.parse_args()

    if a.cmd == "scan":
        return asyncio.run(scan())
    onoff = lambda s: bytes([1 if s == "on" else 0])
    wait = 1.5
    pk = {
        "status": lambda: [frame("GA"), frame("BV"), frame("SF"), frame("TA")],
        "version": lambda: [frame("SF")],
        "battery": lambda: [frame("BV")],
        "geteq": lambda: [frame("GE")],
        "monitor": lambda: [frame("GA")],
        "anc": lambda: [frame("AC", bytes([ANC[a.mode]]))],
        "eq": lambda: [frame("EQ", bytes([EQ[a.preset]]))],
        "band": lambda: [frame("SQ", bytes([a.index, a.gain & 0xFF]))],
        "eq8": lambda: [frame("SW", bytes([a.slot] + [v & 0xFF for v in a.values]))],
        "eqreset": lambda: [frame("SL")],
        "ldac": lambda: [frame("LC", onoff(a.state))],
        "game": lambda: [frame("GM", onoff(a.state))],
        "multipoint": lambda: [frame("OM", onoff(a.state))],
        "timer": lambda: [frame("CL") if a.minutes == 0 else frame("OD", bytes([a.minutes]))],
        "play": lambda: [frame("PL", b"\x01")],
        "pause": lambda: [frame("PL", b"\x00")],
        "next": lambda: [frame("LT", b"\x01")],
        "prev": lambda: [frame("LT", b"\x00")],
        "raw": lambda: [frame(a.code.upper(), bytes.fromhex(a.payload))],
    }[a.cmd]()
    if a.cmd == "monitor":
        wait = a.seconds
    asyncio.run(run(a.address, pk, wait))


if __name__ == "__main__":
    main()
