# Audit SEO: caldoefreddoroma.com

**Data:** 17 novembre 2026
**Tipo di attività:** servizio locale (tecnico termoidraulico, area servita Roma e Città Metropolitana) con blog divulgativo
**CMS:** WordPress + tema Avada + Yoast SEO

## Come è stato fatto l'audit, e cosa manca

La rete di questo ambiente blocca caldoefreddoroma.com e la richiesta di sbloccarla è stata rifiutata. Quindi il sito **non è stato analizzato dal vivo**. Tutti i dati vengono dal connettore WordPress del sito, in sola lettura:

- inventario completo: 93 pagine e 42 articoli pubblicati, con categorie;
- lettura completa di 9 contenuti: home, Assistenza Caldaie Roma, Caldaie Casalotti, Caldaie Boccea, Condizionatore Casalotti, Caldaie Baxi, Contatti, Dove siamo, /ai/;
- campi Yoast (title e meta description) delle pagine lette.

**Aggiornamento del 18 novembre 2026:** dopo lo sblocco della rete ho verificato il sito dal vivo. Ho scansionato tutte le 143 URL delle sitemap di articoli, pagine e categorie, ho controllato robots.txt, sitemap, header, canonical, meta robots e l'HTML finale, e ho misurato le performance con Lighthouse (mobile, 7 pagine). I risultati sono nella sezione "Verifica tecnica dal vivo" qui sotto.

**Cosa resta non verificato:** i dati reali di Core Web Vitals degli utenti (CrUX), perché senza una chiave API PageSpeed Insights risponde con l'errore 429 di quota esaurita. Restano fuori anche il redirect da http e da www, che il proxy dell'ambiente non permette di testare, Search Console, backlink e Google Business Profile.

## SEO Health Score: 68/100

| Categoria | Peso | Punteggio | Nota |
|---|---|---|---|
| SEO tecnico | 22% | 70 | 143/143 URL rispondono 200, canonical corretti; 157 archivi tag indicizzati, HSTS assente |
| Qualità dei contenuti | 23% | 70 | contenuti tecnici solidi, ma pagine di zona molto simili e 14 pagine servizio sotto le 300 parole |
| SEO on-page | 20% | 62 | 10 title corrotti, H1 doppio su 28 pagine, cannibalizzazione |
| Schema / dati strutturati | 10% | 55 | nessun LocalBusiness/HVACBusiness su 123 pagine su 143, nessun indirizzo |
| Performance (dati di laboratorio) | 10% | 75 | Lighthouse mobile 83-91, LCP 2,0-3,7 s, CLS 0 |
| Pronto per la ricerca AI | 10% | 75 | hub /ai/, risposte brevi e citabili, autore riconoscibile |
| Immagini | 5% | 70 | alt presenti, ma loghi presi da siti terzi e og:image del 2016 su 36 pagine |

## Verifica tecnica dal vivo (18 novembre)

### Cosa funziona
- Tutte le 143 URL delle sitemap rispondono 200 e hanno il canonical che punta a sé stesse. Nessuna pagina in noindex per errore.
- Le pagine inesistenti restituiscono un vero 404 in noindex. La ricerca interna è in noindex. Gli archivi autore fanno redirect alla home. L'elenco utenti della REST API è bloccato (403).
- robots.txt è corretto e punta a `sitemap_index.xml`. Non blocca i crawler AI: GPTBot riceve 200.
- La cache LiteSpeed è attiva (TTFB circa 0,55 s con cache, 1,1 s senza).
- CLS 0 su tutte le pagine testate.

### Problemi trovati

**A. 10 title SEO corrotti (alta priorità, correzione rapida).** Il campo title di Yoast contiene ancora le variabili di un vecchio modello (`%%sitename%%, %%parent_title%% %%title%%`) davanti al title vero. Su Google risultano title di 95-148 caratteri, con il nome del servizio ripetuto due volte. Esempio: "Caldo e Freddo Roma, Installazione pannelli solari Installazione pannelli solari Roma | Caldo e Freddo Roma". Pagine coinvolte: chiller, pannelli solari, stufe a pellet, impianti radianti a pavimento e soffitto, impianti riscaldamento/condizionamento/gas, rilascio bollino, certificazioni energetiche, ristrutturazioni, sopralluoghi e preventivi, assistenza manutenzione e installazione caldaie. Anche Contatti e Offerte hanno title deboli ("Caldo e Freddo Roma, Contatti").

**B. 157 archivi tag indicizzati (alta priorità).** La sitemap `post_tag-sitemap.xml` contiene 157 pagine tag in `index, follow`, compresa `/tag/a/`. Per 42 articoli sono pagine quasi vuote e duplicate: sprecano budget di scansione e diluiscono la qualità del sito. *Azione:* in Yoast, Aspetto della ricerca → Tassonomie → Tag, impostare "Mostra i tag nei risultati di ricerca" su No. È un'impostazione globale, quindi va fatta dal pannello di WordPress.

**C. H1 doppio su 28 pagine (media priorità).** Le 10 pagine marca e 18 articoli hanno due H1: uno generato dal titolo del tema Avada e uno scritto nel contenuto. *Correzione rispetto a ieri:* le pagine di zona dei condizionatori un H1 ce l'hanno, generato da Avada; il problema segnalato al punto 4 non esiste. *Azione:* sulle pagine marca e negli articoli trasformare in H2 l'H1 del contenuto, oppure nascondere il titolo del tema.

**D. Schema aziendale incompleto (alta priorità).** Yoast pubblica su tutte le pagine solo un `Organization` senza indirizzo. `HVACBusiness` compare solo sulle 19 pagine condizionatori e `LocalBusiness` solo su una pagina. Nell'HTML della home e del footer l'indirizzo non compare: solo telefono, email e P.IVA. Questo conferma il punto 2.

**E. Pagine servizio troppo brevi (media priorità).** 14 pagine sono sotto le 300 parole: idraulica, chiller, pannelli solari, pompe di calore, stufe a pellet, impianti radianti, impianti gas, bollino, certificazioni, ristrutturazioni, offerte, contatti, dove siamo, categoria video. Sono le stesse pagine con i title corrotti: vanno arricchite oppure accorpate in /servizi/.

**F. Immagine social obsoleta (bassa priorità).** 36 pagine usano come og:image il logo del 2016 (`2016/09/logo.png`). Basta impostare in Yoast un'immagine predefinita aggiornata.

**G. Categorie senza meta description (bassa priorità).** 7 archivi di categoria non hanno description. "Notizie" ha come description solo "Notizie importanti".

**H. Header di sicurezza assenti (bassa priorità).** Mancano HSTS, X-Content-Type-Options e Referrer-Policy. Si aggiungono dal pannello LiteSpeed/hosting o con `.htaccess`.

### Performance (Lighthouse mobile, laboratorio)

| Pagina | Punteggio | LCP | TBT | CLS |
|---|---|---|---|---|
| Home | 88 | 2,0 s | 450 ms | 0 |
| Assistenza caldaie Roma | 88 | 3,4 s | 50 ms | 0 |
| Caldaie Casalotti | 90 | 3,1 s | 20 ms | 0 |
| Condizionatore Casalotti | 91 | 2,9 s | 10 ms | 0 |
| Caldaie Baxi | 91 | 3,0 s | 40 ms | 0 |
| Pressione giusta caldaia | 83 | 3,7 s | 200 ms | 0 |
| Contatti | 89 | 3,1 s | 20 ms | 0 |

Come leggere questi dati:
- Le misure passano dal proxy dell'ambiente, che aggiunge latenza.
- Il proxy ha bloccato gli script di terze parti (Google Tag Manager, widget recensioni TrustIndex). Nella realtà il TBT sarà più alto.
- Sono dati di laboratorio, non dati reali degli utenti.

In sintesi: LCP tra 2,9 e 3,7 s su quasi tutte le pagine interne (la soglia "buono" è 2,5 s). Il problema non sono le immagini: l'elemento LCP è quasi sempre un testo. Le cause sono il tempo di risposta del server (0,7-0,9 s senza cache) e circa 950 ms di CSS e JS che bloccano il rendering, con circa 150 KB di CSS inutilizzato per pagina.

*Correzione rispetto a ieri:* sulle pagine di zona caldaie l'immagine hero con lazy-load non è l'elemento LCP. Il punto 6 scende quindi a bassa priorità.

*Azioni:* in LiteSpeed Cache attivare "Load CSS Asynchronously" con Critical CSS e "Load JS Deferred". In Avada (Performance) attivare "Critical CSS" e "Dynamic CSS Compiler", e disattivare le librerie JS non usate: GSAP, ScrollTrigger, swiper, ilightbox. Valutare il preload della cache con il crawler di LiteSpeed. Sulla home ridurre il TBT (450 ms) caricando TrustIndex solo dopo l'interazione o con un ritardo.

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

**4. ~~H1 mancante sulle pagine di zona dei condizionatori~~ (smentito dalla verifica dal vivo: l'H1 lo genera Avada. Il problema reale è l'H1 doppio, vedi punto C)**
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

## Interventi eseguiti (18 novembre 2026)

Title SEO corretti, approvati dal titolare, verificati sul sito dal vivo:

| Pagina | Nuovo title |
|---|---|
| /installazione-e-manutenzione-gruppi-frigo-chiller/ | Installazione e manutenzione chiller a Roma \| Caldo e Freddo |
| /installazione-pannelli-solari/ | Installazione pannelli solari a Roma \| Caldo e Freddo Roma |
| /installazione-stufe-a-pellet/ | Installazione stufe a pellet a Roma \| Caldo e Freddo Roma |
| /realizzazione-impianti-di-climatizzazione-a-pavimento-e-soffitto/ | Impianti radianti a pavimento e soffitto a Roma |
| /realizzazione-impianti-di-riscaldamento-condizionamento-e-gas/ | Impianti di riscaldamento, condizionamento e gas a Roma |
| /rilascio-bollino-condizionatori-e-caldaie/ | Bollino caldaie e condizionatori a Roma \| Caldo e Freddo |
| /ristrutturazioni/ | Ristrutturazioni a Roma e provincia \| Caldo e Freddo Roma |
| /sopralluoghi-e-preventivi-per-caldaie-condizionatori/ | Sopralluoghi e preventivi caldaie e climatizzatori a Roma |
| /assistenza-manutenzione-ed-installazione-caldaie/ | Installazione e manutenzione caldaie a Roma \| Caldo e Freddo |

**Non applicato:** /rilascio-certificazioni-energetiche/. Il connettore non riesce a salvare la revisione di ripristino e quindi blocca la modifica. Va corretto a mano in Yoast con il title "Certificazioni energetiche a Roma | Caldo e Freddo Roma".

**Nuovo problema trovato durante il lavoro:** su /installazione-stufe-a-pellet/ e /rilascio-bollino-condizionatori-e-caldaie/ il pulsante "chiama ora" punta a `tel:+394076762`: manca "328", quindi la chiamata non va al numero giusto. Il testo visibile invece è corretto.

### Aggiornamento del 18 novembre, pomeriggio
- **Pubblicata la nuova versione di /assistenza-manutenzione-ed-installazione-caldaie/.** Il testo occupa tutta la larghezza su mobile (390/390 px, senza scorrimento orizzontale). Il contenuto è aggiornato: H1 "Installazione e manutenzione caldaie a Roma", corretta l'informazione sulla frequenza del bollino, dicitura Ariston uniformata, link alle marche e a tutte le 22 zone, nuova meta description. Il vecchio JSON-LD (Service e FAQ, con errori) è stato rimosso.
- **Scheda unica `HVACBusiness`:** il connettore non permette di inserire script né microdati, e la copia della pagina Contatti corrompe il suo blocco `<style>`. Per questo la copia non è stata pubblicata. Il codice pronto è in `audits/caldoefreddoroma-schema-hvacbusiness.html` e va incollato in Avada → Opzioni → Avanzate → Campi codice → "Spazio prima di </head>".

### Aggiornamento del 18 novembre, sera (verificato dal vivo)
- **Scheda `HVACBusiness` attiva in tutto il sito**, inserita dal titolare in Avada → Campi codice. JSON valido, con indirizzo Via Giovanni Borromeo 10, 00166 Roma, telefono, email, P.IVA e profili social. Le criticità 2 e D sono risolte per i dati strutturati; resta da mostrare l'indirizzo come testo nella pagina Contatti.
- **Archivi tag in noindex:** le pagine `/tag/…` ora rispondono `noindex, follow` e `post_tag-sitemap.xml` è uscita da `sitemap_index.xml`. Criticità B risolta.
- **Pagina /rilascio-certificazioni-energetiche/ spostata nel cestino** su richiesta del titolare, che non rilascia APE: risponde 404 ed è fuori dalla sitemap.

### Aggiornamento del 18 novembre, notte (verificato dal vivo)
- **Eliminate 7 pagine servizio obsolete** (cestino, rispondono 404): chiller, pannelli solari, stufe a pellet, impianti radianti, impianti riscaldamento/condizionamento/gas, rilascio bollino, ristrutturazioni. Prima di eliminarle ho controllato tutte le 143 pagine: nessun link dal menu o dal footer, un solo link interno, nell'articolo sul monossido, ora rimandato a /controllo-caldaia-e-bollino-roma/. La sitemap delle pagine passa da 91 a 83 URL.
- **/assistenza-manutenzione-ed-installazione-caldaie/ con sfondi a tutta larghezza anche su desktop** (1440/1440 px, testo centrato in max 1140 px), nessuno scorrimento orizzontale.
- **/sopralluoghi-e-preventivi-per-caldaie-condizionatori/ rifatta da zero in bozza**, in attesa di approvazione. Nuovi contenuti: costi del sopralluogo (a pagamento e scalato dal lavoro per le installazioni; per le riparazioni diritto di chiamata e manodopera, con trattamento di favore), procedura in 4 passi, stima a distanza via WhatsApp e FAQ. Yoast: title "Sopralluogo e preventivo caldaie e climatizzatori a Roma", nuova description, focus keyword "preventivo sostituzione caldaia Roma". Eliminato il testo "[php snippet=5]".
- **Pubblicata /sopralluoghi-e-preventivi-per-caldaie-condizionatori/**, riprogettata graficamente e verificata dal vivo su desktop e mobile, senza scorrimento orizzontale. Struttura: apertura con foto del titolare (nuova immagine ID 53177, alt e didascalia compilati), 6 schede servizio con foto (nuova immagine pompa di calore ID 53179), sezione costi, percorso in 4 passi, stima da foto, FAQ e chiusura. Rimossi lo slider Avada e la barra del titolo; il logo 2016 non è più l'immagine in evidenza, ora `og:image` è la foto del titolare. Title "Sopralluogo e preventivo caldaie e climatizzatori a Roma", nuova meta description.
