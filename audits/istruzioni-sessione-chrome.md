# Istruzioni per la sessione Claude in Chrome — caldoefreddoroma.com

Lavora nel mio Chrome, dove sono già loggato su WordPress (caldoefreddoroma.com/wp-admin), Google Search Console, Google Business Profile e Ahrefs. Apri schede nuove e non toccare le altre.

**Regole:** prima di ogni salvataggio o sostituzione mostrami cosa stai per cambiare e aspetta il mio OK. Per le sostituzioni di massa fai sempre prima la prova a vuoto ("dry run"). Non modificare impostazioni diverse da quelle elencate. Alla fine fammi un riepilogo di cosa hai fatto e cosa no.

---

## 0. Backup
Prima di tutto verifica che esista un backup recente del sito (plugin di backup o pannello hosting). Se non c'è, fermati e chiedimi come procedere.

## 1. Scheda aziendale (Avada)
Avada → Opzioni → Avanzate → Campi codice → "Spazio prima di </head>": c'è già un blocco `<script type="application/ld+json">` con `"@type": "HVACBusiness"`. Sostituisci **solo quel blocco** con questo, lasciando intatto il resto del campo. Questa versione non ha più il link a Wikidata:

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "HVACBusiness",
  "@id": "https://caldoefreddoroma.com/#business",
  "name": "Caldo & Freddo di Renzetti Valerio",
  "alternateName": "Caldo e Freddo Roma",
  "url": "https://caldoefreddoroma.com/",
  "logo": "https://caldoefreddoroma.com/wp-content/uploads/2026/10/logo-caldofreddoroma-valerio-renzetti-960.webp",
  "image": "https://caldoefreddoroma.com/wp-content/uploads/2026/10/assistenza-caldaie-roma-tecnico.webp",
  "description": "Assistenza, manutenzione e installazione di caldaie e condizionatori a Roma e nella Città Metropolitana, con il tecnico termoidraulico Valerio Renzetti.",
  "telephone": "+393284076762",
  "email": "info@caldoefreddoroma.com",
  "vatID": "IT13198191002",
  "address": {
    "@type": "PostalAddress",
    "streetAddress": "Via Giovanni Borromeo 10",
    "postalCode": "00166",
    "addressLocality": "Roma",
    "addressRegion": "RM",
    "addressCountry": "IT"
  },
  "areaServed": {
    "@type": "AdministrativeArea",
    "name": "Città Metropolitana di Roma Capitale"
  },
  "founder": {
    "@type": "Person",
    "name": "Valerio Renzetti",
    "url": "https://caldoefreddoroma.com/chi-e-valerio-renzetti/"
  },
  "parentOrganization": { "@id": "https://caldoefreddoroma.com/#organization" },
  "sameAs": [
    "https://www.facebook.com/CaldoFreddoRoma",
    "https://www.instagram.com/caldofreddoroma",
    "https://www.tiktok.com/@valeriorenzetti",
    "https://www.youtube.com/@valeriorenzetti",
    "https://x.com/caldofreddoroma"
  ]
}
</script>
```

Salva.

## 2. Better Search Replace (link Wikidata e redirect interni)
Installa e attiva il plugin **Better Search Replace**. Strumenti → Better Search Replace. Seleziona **solo la tabella `wp_posts`**, spunta "Esegui come prova a vuoto", fai la prova, mostrami quante righe cambierebbe, poi ripeti senza prova a vuoto. Fai le sostituzioni **in quest'ordine, una alla volta**:

| # | Cerca | Sostituisci con |
|---|---|---|
| 1 | `https://www.wikidata.org/wiki/Q136071266` | `https://caldoefreddoroma.com/chi-e-valerio-renzetti/` |
| 2 | `>Profilo Wikidata<` | `>Chi è Valerio Renzetti<` |
| 3 | `>scheda Wikidata<` | `>Chi è Valerio Renzetti<` |
| 4 | `>Wikidata<` | `>Chi è Valerio Renzetti<` |
| 5 | `href="https://caldoefreddoroma.com/valerio-renzetti/"` | `href="https://caldoefreddoroma.com/chi-e-valerio-renzetti/"` |
| 6 | `href="https://caldoefreddoroma.com/valerio-renzetti"` | `href="https://caldoefreddoroma.com/chi-e-valerio-renzetti/"` |
| 7 | `href="https://caldoefreddoroma.com/accensione-riscaldamento"` | `href="https://caldoefreddoroma.com/accensione-riscaldamento/"` |
| 8 | `href="https://caldoefreddoroma.com/bonus-climatizzatori-2025"` | `href="https://caldoefreddoroma.com/bonus-climatizzatori-2025/"` |
| 9 | `href="https://caldoefreddoroma.com/controllo-caldaia-e-bollino-roma"` | `href="https://caldoefreddoroma.com/controllo-caldaia-e-bollino-roma/"` |
| 10 | `href="https://caldoefreddoroma.com/come-eliminare-umidita-in-casa"` | `href="https://caldoefreddoroma.com/come-eliminare-umidita-in-casa/"` |

Se la prova a vuoto della riga 4 (`>Wikidata<`) trova più di ~20 righe, fermati e mostrami dove compare prima di procedere.
Dopo le sostituzioni apri 2 articoli a campione (/calcare-caldaia-come-fare/ e /bonus-bollette-2025-luce-gas-acqua/) e controlla che il link autore porti a /chi-e-valerio-renzetti/ e che la pagina si veda bene.

## 3. Quattro link esterni rotti
Per ognuno cerca su Google il nuovo indirizzo ufficiale della stessa fonte. Se lo trovi, sostituisci il link nella pagina (editor Avada → elemento di testo → link). Se non trovi una fonte equivalente affidabile, togli il collegamento lasciando il testo. Mostrami la proposta prima di salvare.

| Pagina | Link rotto | Testo del link |
|---|---|---|
| https://caldoefreddoroma.com/caldaia-non-si-accende/ | https://www.unicalag.it/upload/blocchi/X5665allegatoMANUALE_INSTALLAZIONE2-1X_00339883v1.pdf | Unical — manuale 00339883, rilevamento guasti |
| https://caldoefreddoroma.com/caldaia-accesa-tutto-giorno-o-intervalli/ | https://www.beama.org.uk/portfolios/low-carbon-building-solutions/heating-controls/advice-for-householders.html | BEAMA — controlli ambiente, condensazione e programmazione |
| https://caldoefreddoroma.com/bonus-bollette-2026-isee-25000-arera/ | https://www.staffettaonline.com/articolo.aspx?id=408232 | Staffetta Quotidiana del 16 ottobre 2026 |
| https://caldoefreddoroma.com/bonus-climatizzatori-2025/ | https://www.agenziaentrate.gov.it/portale/web/guest/bonus-casa | Agenzia delle Entrate |

Attenzione: in molti articoli c'è un blocco "Codice" Avada con lo schema (JSON-LD). Non modificarlo e non cancellarlo.

## 4. Yoast SEO
- Yoast SEO → Impostazioni → Avanzate → Ottimizzazione della scansione: attiva "Rimuovi i feed dei tag", "Rimuovi i feed delle categorie" e "Rimuovi i feed dei commenti globali". Lascia attivo il feed principale.
- Yoast SEO → Impostazioni → Rappresentazione del sito (o Impostazioni del sito → Immagine predefinita per i social): imposta come immagine predefinita `valerio-renzetti-tecnico-caldaie-climatizzatori-roma.webp` (ID 53177) al posto del vecchio logo del 2016.

## 5. LiteSpeed Cache
LiteSpeed Cache → Crawler: attiva il crawler (preload della cache), con la sitemap `https://caldoefreddoroma.com/sitemap_index.xml`. Poi LiteSpeed Cache → Svuota tutto.

## 6. Google Search Console
Apri https://search.google.com/search-console/sitemaps?resource_id=sc-domain%3Acaldoefreddoroma.com&hl=IT
- Elenca tutte le sitemap inviate. Tieni solo sitemap_index.xml, page-sitemap.xml, post-sitemap.xml, category-sitemap.xml. La vecchia sitemap del 26 ott 2021 ("Impossibile recuperare") potrei averla già rimossa a mano: se non c'è più, scrivimi solo "già rimossa" e vai avanti. Se invece c'è, o ci sono altre sitemap oltre alle 4 da tenere, mostramele e, dopo il mio OK, rimuovile (⋮ → Rimuovi sitemap).
- Reinvia sitemap_index.xml.
- Indicizzazione → Pagine → "Errore di reindirizzamento" → "Convalida correzione".

## 7. Errore dati strutturati (test gratuito di Google)
Apri https://search.google.com/test/rich-results e testa queste due pagine, una alla volta:
- https://caldoefreddoroma.com/assistenza-climatizzatore-roma/casalotti/
- https://caldoefreddoroma.com/
Per ognuna riportami il **testo esatto** di tutti gli errori e gli avvisi (soprattutto "Attività commerciale locale" / "Local business" e "Organizzazione"). Non modificare niente: riportami solo i messaggi.
Facoltativo: se Ahrefs si apre con il mio account, vai in Site Audit → issue "Structured data has Google rich results validation error" e riportami il messaggio di una pagina. Se Ahrefs non si apre o chiede un abbonamento, salta questa parte.

## 8. Google Business Profile (solo lettura)
Apri il profilo dell'attività e riportami esattamente nome, indirizzo, telefono, sito web, categoria e orari. Confrontali con: "Caldo & Freddo di Renzetti Valerio", "Via Giovanni Borromeo 10, 00166 Roma RM", "+39 328 407 6762", https://caldoefreddoroma.com/. Non salvare modifiche.

---
Al termine chiudi le schede che hai aperto e dammi un riepilogo punto per punto (fatto / non fatto / cosa serve da me).
