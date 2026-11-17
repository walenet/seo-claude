# Audit SEO: caldoefreddoroma.com

**Data:** 17 novembre 2026
**Tipo di attività:** servizio locale (tecnico termoidraulico, area servita Roma e Città Metropolitana) con blog divulgativo
**CMS:** WordPress + tema Avada + Yoast SEO

## Come è stato fatto l'audit, e cosa manca

La rete di questo ambiente blocca caldoefreddoroma.com e la richiesta di sbloccarla è stata rifiutata. Quindi il sito **non è stato analizzato dal vivo**. Tutti i dati vengono dal connettore WordPress del sito, in sola lettura:

- inventario completo: 93 pagine e 42 articoli pubblicati, con categorie;
- lettura completa di 9 contenuti: home, Assistenza Caldaie Roma, Caldaie Casalotti, Caldaie Boccea, Condizionatore Casalotti, Caldaie Baxi, Contatti, Dove siamo, /ai/;
- campi Yoast (title e meta description) delle pagine lette.

**Cosa non è stato verificato:** robots.txt, sitemap XML, header HTTP, redirect, canonical, HTML finale (header, footer, schema generato da Yoast), Core Web Vitals, indicizzazione, backlink e Google Business Profile. Il punteggio Tecnico e quello Performance sono quindi **stime provvisorie**.

## SEO Health Score: circa 65/100 (provvisorio)

| Categoria | Peso | Punteggio | Nota |
|---|---|---|---|
| SEO tecnico | 22% | 60* | *non verificato dal vivo |
| Qualità dei contenuti | 23% | 72 | contenuti tecnici solidi, ma molto testo ripetuto tra le pagine di zona |
| SEO on-page | 20% | 68 | H1 mancante su alcuni template, cannibalizzazione |
| Schema / dati strutturati | 10% | 55 | entità aziendale incoerente, nessun indirizzo |
| Performance (CWV) | 10% | 55* | *stimata dal codice sorgente |
| Pronto per la ricerca AI | 10% | 75 | hub /ai/, risposte brevi e citabili, autore riconoscibile |
| Immagini | 5% | 70 | alt presenti, ma loghi presi da siti terzi e foto riusate tra marchi diversi |

## Punti di forza

- **Esperienza (E-E-A-T) ben visibile:** l'autore è riconoscibile (Valerio Renzetti), le date di aggiornamento sono indicate, c'è una pagina autore e si citano fonti primarie (MIMIT-ENEA, regolamento di Roma Capitale, ARERA, Agenzia delle Entrate).
- **Pagine marca molto approfondite** (es. Baxi): tabella dei codici errore, modelli, manutenzione, avvertenze sul marchio. Sono pagine utili e non ripetitive.
- **Linking interno ricco:** pagine principali, pagine di zona e guide sono collegate tra loro, e ogni pagina rimanda alla pagina principale.
- **La pagina /ai/** offre risposte brevi e datate che rimandano alle guide, un formato adatto a essere citato da AI Overviews e ChatGPT.
- **Alt text** compilati in modo descrittivo su quasi tutte le immagini; le immagini dei quartieri riportano la licenza.

## Criticità e azioni, in ordine di priorità

### Critico

**1. 44 pagine di zona con circa l'80-85% di testo identico (vicino alla soglia di 50 pagine)**
- *Cosa si vede:* ci sono 22 pagine `/assistenza-caldaie-roma/{zona}/` e 22 `/assistenza-climatizzatore-roma/{zona}/`. Confrontando Casalotti e Boccea, cambiano solo l'introduzione, il paragrafo con le vie e un box di approfondimento (circa 250 parole). Il resto è identico: problemi, servizi, marchi, procedura, 6 FAQ su 8, autore.
- *Perché conta:* oltre 30 pagine di zona serve almeno il 60% di contenuto unico. Altrimenti Google può trattarle come contenuti a basso valore e indicizzarne solo alcune.
- *Azione:* non creare altre pagine di zona finché queste non sono differenziate. Per ogni zona aggiungere elementi reali: interventi svolti nel quartiere (anche anonimi), problemi tipici degli edifici della zona, tempi effettivi, foto proprie, recensioni di clienti del quartiere. Se una zona non ha niente di specifico da dire, valutare di accorparla.
- *Come capire se non funziona:* se dopo 8-12 settimane Search Console continua a segnare queste pagine come "Scansionata, ma attualmente non indicizzata" o senza impressioni, vuol dire che la differenziazione non basta e conviene accorparle.
- *Indicatore da seguire:* numero di pagine di zona con almeno 1 impressione al mese in Search Console.

**2. NAP (nome, indirizzo, telefono) quasi assente e indirizzo mai presente negli schema**
- *Cosa si vede:* la pagina Contatti contiene solo l'H1 e un modulo. L'indirizzo (Via Giovanni Borromeo 10, 00166 Roma) compare solo in una mappa nella pagina "Dove siamo", ferma al 2016 e senza testo né campi SEO. Gli schema Service indicano come fornitore un'organizzazione senza indirizzo.
- *Perché conta:* la coerenza dei dati NAP tra sito, Google Business Profile e directory è un fattore di ranking locale centrale.
- *Azione:* scrivere i dati NAP come testo nella pagina Contatti (e nel footer, da verificare dal vivo). Unire "Dove siamo" a Contatti con un redirect 301. Definire una sola entità `HVACBusiness` con `@id` `https://caldoefreddoroma.com/#business`, completa di `address`, `geo`, `telephone`, `openingHours`, `areaServed` e `sameAs` (Google Business Profile, Facebook, Instagram, TikTok).
- *Dipendenze:* è la base per gli interventi 4 e 7.

### Alta priorità

**3. Cannibalizzazione tra pagine di servizio e articoli**
- Caldaia e bollino: `/assistenza-caldaie-roma/`, `/assistenza-manutenzione-ed-installazione-caldaie/`, `/controllo-caldaia-e-bollino-roma/`, `/bollino-blu-caldaia-roma/` e `/rilascio-bollino-condizionatori-e-caldaie/` puntano alle stesse ricerche.
- Condizionatori: `/assistenza-climatizzatore-roma/` e `/installazione-e-assistenza-condizionatori-roma/`.
- Accensione del riscaldamento: 3 articoli del 2025 (`accensione-riscaldamento-2025-quando-e-come`, `quando-si-accende-il-riscaldamento-2025-…`, `accensione-riscaldamento`) e 2 del 2026.
- Caldaia che non si accende: la pagina `/caldaia-non-si-accende/` e l'articolo `/caldaia-non-si-accende-reset-caldaia/`.
- *Azione:* scegliere una pagina principale per ciascun gruppo e unire le altre con redirect 301. Per le date stagionali tenere un URL fisso senza anno (es. `/accensione-riscaldamento-roma/`) e aggiornarlo ogni anno.
- *Come capire se non funziona:* in Search Console più URL che ricevono impressioni per la stessa query.

**4. H1 mancante su tutte le pagine di zona dei condizionatori (probabile)**
- La pagina Condizionatore Casalotti usa un `<h2>` come titolo principale e il contenuto non ha nessun H1. Il template è lo stesso per tutte le 22 pagine. Anche la pagina /ai/ non ha un H1 nel contenuto. Bisogna verificare dal vivo se Avada aggiunge un H1 nella barra del titolo, ma le pagine caldaie la nascondono via CSS, quindi probabilmente non c'è.
- *Azione:* trasformare in `<h1>` il titolo principale del template condizionatori e aggiungere un H1 alla pagina /ai/.

**5. Contenuti con informazioni contraddittorie sull'autorizzazione Ariston**
- La pagina principale caldaie dice "Installatore Tecnico Autorizzato Ariston". Le pagine di zona dicono "centro assistenza autorizzato Ariston".
- Le pagine di zona elencano anche Riello tra i marchi assistiti, ma non esiste una pagina Riello.
- *Azione:* usare una sola dicitura verificabile, possibilmente con un link alla scheda sul sito Ariston. Aggiungere una pagina Riello oppure togliere Riello dall'elenco.

**6. Immagine principale caricata in lazy-load sulle pagine di zona caldaie**
- L'immagine hero, quella che determina l'LCP, ha `loading="lazy"`. Le pagine condizionatori invece usano correttamente `fetchpriority="high"`.
- Ogni pagina di zona caldaie include anche circa 15 KB di CSS ripetuto in `<style>` dentro il contenuto, cioè 22 copie dello stesso CSS.
- *Azione:* togliere `loading="lazy"` dall'immagine hero e aggiungere `fetchpriority="high"`. Spostare il CSS condiviso in un unico foglio di stile del child theme, o usare il CSS della singola pagina che il connettore supporta.
- *Come capire se non funziona:* l'LCP mobile su PageSpeed Insights resta sopra i 2,5 s.

### Media priorità

**7. Schema incoerente tra i diversi template**
- Pagine caldaie: il fornitore è un `Organization` senza `@id`. Pagine condizionatori: è un `HVACBusiness` con `@id #business`, ma il nome cambia ("Caldo e Freddo Roma" invece di "Caldo & Freddo di Renzetti Valerio").
- Tutte le pagine hanno uno schema `FAQPage`. Dal 7 maggio 2026 Google non mostra più i risultati FAQ arricchiti per nessun sito: è un'informazione, non un errore, e non serve rimuoverlo. Però non ne va aggiunto altro aspettandosi un beneficio in SERP.
- *Azione:* in ogni Service, far puntare `provider` a `{"@id":"https://caldoefreddoroma.com/#business"}` e usare un solo nome legale. Prima verificare dal vivo cosa genera già Yoast, per non avere entità duplicate.

**8. Campi Yoast da sistemare**
- Contatti: manca il title. La meta description usa `%%title%%` seguito da un elenco di parole chiave.
- Pagina principale caldaie: nella meta description manca uno spazio ("riparazione.Prenota").
- "Dove siamo": nessun campo SEO.
- Lo slug della home è `home-page`. Non crea problemi se la pagina è impostata come homepage statica, ma va verificato.

**9. Collegamenti alle zone incompleti**
- Nelle mappe delle zone delle pagine di quartiere mancano Monteverde, Salario, Portuense e Marconi, che compaiono solo nella pagina principale. Queste 4 pagine ricevono quindi meno link interni.
- *Azione:* allineare il blocco delle zone su tutte le 44 pagine.

**10. Immagini prese da siti terzi e foto riusate tra marchi**
- I loghi dei marchi nelle pagine condizionatori sono caricati da climaconvenienza.it, mitsubishielectric.it e olimpiasplendid.it. Si rischiano immagini rotte, richieste lente e problemi di diritti.
- La pagina Baxi usa file chiamati `ariston-manutenzione.webp` e `ariston-sostituzione.webp` con alt che parlano di Baxi.
- *Azione:* caricare i loghi nella libreria media del sito e usare foto proprie o neutre per ogni marca.

**11. Articoli fuori tema**
- "Elon Musk vuole oscurare il sole", "Valerio Renzetti alla Biblioteca del Senato: social e minori", "Piano cottura a gas o induzione?".
- Diluiscono la coerenza tematica del sito. Conviene spostare in noindex, o in una sezione "Media" separata, gli articoli che non riguardano impianti, energia o casa.
- 39 articoli su 42 sono nella categoria generica "Notizie": usare come categoria principale le categorie tematiche (Caldaia, Climatizzatori, Riscaldamento).

### Bassa priorità

- Refusi nella pagina principale caldaie: "va' effettuato" va corretto in "va effettuato", "se bisogno" in "se necessario".
- Struttura URL: `/assistenza-caldaie-roma/lavaggio-impianti-di-riscaldamento/` è un servizio, non una zona, ma sta nella stessa cartella delle zone. Accettabile, ma per coerenza meglio `/servizi/…`.
- Privacy Policy e Cookie Policy sono ferme al 2019: vanno aggiornate per conformità, non per la SEO.
- Gli URL usano "climatizzatore" mentre i titoli usano "condizionatore". Va bene coprire entrambi i termini, ma è meglio usarli in modo coerente nei title.

## Piano d'azione con le dipendenze

1. **Settimana 1:** NAP e entità `HVACBusiness` unica (punti 2 e 7), H1 sui template (4), hero senza lazy-load (6), campi Yoast (8). Sono interventi rapidi e indipendenti tra loro.
2. **Settimane 2-3:** accorpamenti e redirect contro la cannibalizzazione (3), dicitura Ariston e Riello (5), blocco zone completo (9).
3. **Settimane 3-8:** differenziazione delle pagine di zona (1), partendo dalle 10 con più impressioni in Search Console. Nessuna nuova pagina di zona in questo periodo.
4. **In parallelo:** pulizia del blog (11) e delle immagini (10).

## Prossimi passi per completare l'audit

- **Abilitare `caldoefreddoroma.com` nella rete dell'ambiente** per verificare robots.txt, sitemap, canonical, schema generato da Yoast, HTML finale e Core Web Vitals (PageSpeed/CrUX).
- **Collegare Google Search Console** per avere dati reali su indicizzazione e cannibalizzazione.
- **Fare un audit locale dedicato** (`/seo local`) su Google Business Profile, recensioni e citazioni.
