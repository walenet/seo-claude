# Protocollo 1Mii Y8 / Y8+ (ricostruito da YeeMall 2.8.7)

Fonte: `YeeMall_2.8.7.apk` (package `com.ymconnect`), decompilato con jadx 1.5.1.
Classi chiave: `com.ymconnect.bean.bleInstruct.BleInstructY8` / `BleInstructY8u`,
view-model `ut5` (Y8) e `vt5` (Y8+ U), `com.ymconnect.bean.BluetoothConfig`, enum prodotti `jr3`.

> Tutto ciò che è marcato **(da verificare)** è dedotto dal codice e non ancora provato sulle cuffie.

## 1. Varianti "Y8+" note all'app

L'app conosce **tre** prodotti chiamati "Y8+", distinti da `productId` / `versionId`:

| Enum app | PID | VID | Controllo | Aggiornamento firmware (OTA) |
|---|---|---|---|---|
| `Y8` | 7 | 1 | `BleInstructY8` (EQ 10 bande) | SDK **JieLi** (`jlota`) |
| `Y8_PLUS` | – | – | `BleInstructY8` (EQ 10 bande) | SDK **JieLi** |
| `Y8_PLUS_U` | 2 | 3 | `BleInstructY8u` (EQ 8 bande) | SDK **JieLi** |
| `Y8_PLUS_U_LANXUN` | 2 | 4 | `BleInstructY8u` (EQ 8 bande) | SDK **Bluetrum** (Lanxun = nome cinese di Bluetrum) |

Le tue cuffie hanno firmware **1.7.13**. Per `Y8_PLUS_U` l'app controlla versioni ≥16 e ≥20, quindi è probabile
che le tue siano `Y8_PLUS` o la variante Bluetrum **(da verificare)**: basta il comando `geteq`
(10 valori = Y8 classico, 1+8 valori = variante U).

## 2. Trasporto

Bluetooth **LE (GATT)**. Non serve SPP.

| Ruolo | UUID |
|---|---|
| Servizio | `0000faa0-0000-1000-8000-00805f9b34fb` |
| Scrittura (app → cuffie) | `0000faa1-0000-1000-8000-00805f9b34fb` |
| Notifica (cuffie → app) | `0000faa2-0000-1000-8000-00805f9b34fb` |
| CCCD | `00002902-...` |

Nell'annuncio BLE, i byte 9-14 dello scan record contengono il MAC classic delle cuffie: l'app li usa
per abbinare il dispositivo BLE a quello audio.

## 3. Formato del pacchetto

```
AA | C1 | C2 | LEN | DATA[LEN] | SUM
```
- `C1 C2` = comando di due lettere ASCII (es. `AC` = 0x41 0x43)
- `SUM` = somma di tutti i byte precedenti (incluso `AA`) & 0xFF
- Le richieste "di lettura" mandano `LEN=1, DATA=00`
- **Risposte (verificato sulle cuffie, fw 1.7.13):** iniziano con **`A5`** invece di `AA`.
  I dati vanno dal byte 4 al penultimo; l'ultimo è un checksum (non è la semplice somma).
  Il campo LEN delle risposte non è affidabile (GA riporta `04` ma contiene 5 byte).

Esempi reali:
```
>> AA 47 41 01 00 33          << A5 47 41 04 01 07 01 00 00 74   (ANC=1, EQ=7, play=1, LDAC=0, multipoint=0)
>> AA 42 56 01 00 43          << A5 42 56 02 3E 01 EC            (batteria 62%)
>> AA 53 46 01 00 44          << A5 53 46 03 01 07 0D 03         (firmware 1.7.13)
>> AA 47 45 01 00 37          << A5 47 45 0A 02 05 03 00 FF 00 00 01 02 01 61  (EQ 10 bande)
```
- La Y8+ dell'autore è la variante **a 10 bande** (`Y8_PLUS`).
- Accendendo LDAC (`LC 1`) la connessione BLE cade per qualche secondo: va ricollegata.

Esempio: stato completo → `AA 47 41 01 00 33`

## 4. Comandi

| Cmd | Dati inviati | Significato | Risposta (dati) |
|---|---|---|---|
| `GA` | 00 | Leggi stato completo | `anc, eq, musica, ldac, multipoint[, game]` |
| `BV` | 00 | Batteria | `livello%, valido` |
| `SF` | 00 | Versione firmware | `a, b, c` → "a.b.c" |
| `TA` | 00 | Leggi timer spegnimento | minuti |
| `AC` | 0 / 1 / 2 | ANC: **0 = normale, 1 = cancellazione rumore, 2 = trasparenza** | modalità |
| `EQ` | 0-7 | Preset: 0 Default, 1 Pop, 2 Deep Bass, 3 Rock, 4 Classic, 5 Jazz, 6 Country, 7 Personalizzato | preset |
| `GE` | 00 | Leggi EQ personalizzato | Y8: 10 guadagni (int8, dB) · Y8+U: preset + 8 guadagni |
| `SQ` | banda, guadagno | **Y8**: imposta 1 banda (0-9) in dB, int8 | – |
| `SW` | slot, g1..g8 | **Y8+U**: scrive tutte e 8 le bande (vedi §5) | – (l'app rilegge con `GE`) |
| `SL` | 00 | Azzera EQ personalizzato | – |
| `LC` | 0 / 1 | **LDAC** (in app "Hi-Res Audio mode") | stato |
| `GM` | 0 / 1 | Game mode (bassa latenza) | – (in `GA`) |
| `OM` | 0 / 1 | Doppia connessione (multipoint) | stato |
| `OD` | minuti | Spegnimento programmato (l'app offre 5…60, 120) | – |
| `CL` | 00 | Annulla spegnimento programmato | – |
| `PL` | 0 / 1 | Pausa / Play | stato |
| `LT` | 0 / 1 | Brano precedente / successivo | – |
| `LS` | – | *Solo notifica*: `00` = audio classic disconnesso | – |

Vincoli imposti dall'app (non per forza dal firmware):
- attivando **LDAC** l'app spegne prima multipoint (`OM 0`) e game mode (`GM 0`);
- attivando multipoint o game mode l'app spegne prima LDAC (`LC 0`).

Probabilmente è un limite di banda del chip. Ignorarlo potrebbe funzionare oppure no **(da verificare)**.

## 5. Equalizzatore

**Y8 (10 bande):** 32, 64, 125, 250, 500, 1K, 2K, 4K, 8K, 16 kHz. Guadagno intero in dB (int8).
Preset di fabbrica (dB):

| Preset | 32 | 64 | 125 | 250 | 500 | 1K | 2K | 4K | 8K | 16K |
|---|---|---|---|---|---|---|---|---|---|---|
| Default | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Pop | 4 | 5 | 0 | -8 | -5 | -4 | -2 | -2 | -6 | 0 |
| Deep Bass | -2 | 0 | 2 | 4 | -2 | -2 | 0 | 0 | 4 | 4 |
| Rock | 3 | 1 | 0 | -2 | -4 | -4 | -2 | 0 | 1 | 2 |
| Classic | 0 | 8 | 8 | 4 | 0 | 0 | 0 | 0 | 2 | 2 |
| Jazz | 0 | 0 | 0 | 4 | 4 | 4 | 0 | 2 | 3 | 4 |
| Country | -2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 4 | 4 |

(L'ordine segue la lista nel codice. L'abbinamento nome↔curva è **da verificare**.)

**Y8+ U (8 bande):** byte = `round(guadagno_UI × fattore × 10)`:
- `Y8_PLUS_U`: fattore 1.0 → passi da 0.1 dB;
- `Y8_PLUS_U_LANXUN` (Bluetrum): fattore 0.5.

Le frequenze delle 8 bande non sono nel codice dell'app **(da ricavare)**.

## 6. Aggiornamento firmware

- L'app interroga `https://app1.1mii.com:8080/app/firmwareVersion/checkFirmwareUpgrade?firmwareVersion=<ver>&productName=<nome>`.
- La risposta contiene `versionNumber`, `url`, `md5`, `description`. Da lì si può scaricare l'immagine ufficiale per studiarla.
- Il flash avviene con l'SDK del produttore del chip: JieLi (`com.jieli.jl_bt_ota`, BLE `AE00/AE01/AE02` o SPP) oppure Bluetrum (`com.bluetrum.fota`).
- Un firmware **modificato** dovrebbe passare gli stessi controlli dell'SDK (header, CRC ed eventuale firma).
  Prima serve un'immagine ufficiale da analizzare.

## 7. Prossimi passi

1. Provare i comandi (con nRF Connect sul telefono o con `y8ctl.py` da PC) e confermare la variante.
2. Provare comandi "nascosti": le stesse due lettere usate da altri modelli 1Mii presenti nell'app
   (E700, E800, MK03, B03…).
3. App Android personale (Kotlin) basata su questa tabella.
4. Scaricare il firmware ufficiale dall'URL del §6 e identificare il chip esatto.

## Appendice: comandi pronti (per nRF Connect)

Connettiti a "Y8+", apri il servizio `FAA0`, attiva le notifiche su `FAA2` e scrivi su `FAA1` in formato **byte array (hex)**:

| Azione | Hex |
|---|---|
| Stato completo | `AA 47 41 01 00 33` |
| Batteria | `AA 42 56 01 00 43` |
| Versione firmware | `AA 53 46 01 00 44` |
| Leggi EQ personalizzato | `AA 47 45 01 00 37` |
| ANC normale | `AA 41 43 01 00 2F` |
| ANC cancellazione rumore | `AA 41 43 01 01 30` |
| ANC trasparenza | `AA 41 43 01 02 31` |
| LDAC on / off | `AA 4C 43 01 01 3B` / `AA 4C 43 01 00 3A` |
| Game mode on / off | `AA 47 4D 01 01 40` / `AA 47 4D 01 00 3F` |
| Multipoint on / off | `AA 4F 4D 01 01 48` / `AA 4F 4D 01 00 47` |
| EQ Default … Personalizzato | `AA 45 51 01 0X SS` (X = 0…7, SS = 41+X) |
| Spegnimento tra 30 min | `AA 4F 44 01 1E 5C` |
| Annulla spegnimento | `AA 43 4C 01 00 3A` |

## Appendice: canale JieLi RCSP (verificato, fw 1.7.13)

- Servizio `AE00`: scrittura `AE01` (write without response), notifiche `AE02`.
- Pacchetto: `FE DC BA | flag | opcode | len(2, BE) | sn | parametri | EF`
  (flag `C0` = comando con risposta; le risposte hanno flag `00` e iniziano con `status, sn`).
- **Non serve autenticazione**: le cuffie rispondono ai comandi RCSP senza la sequenza di auth JieLi
  (che comunque non ricevono risposta).
- `GET_SYS_INFO` (07) funzione `FF` restituisce solo l'attributo 15 (stato telefonata):
  **niente livelli ANC, assistenza all'udito, anti-vento o rinforzo voce** su questo firmware.
- Le cuffie inviano notifiche periodiche `C2` (ADV, ogni ~0,5 s) con VID `05D6` e dati di stato;
  si possono fermare con il comando `C3 00`.
- Durante il canale chiamata (HFP) le cuffie inviano `09 … FF 02 0F 01` (stato telefonata = 1)
  e spengono l'ANC.
