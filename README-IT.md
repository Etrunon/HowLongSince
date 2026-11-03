# How Long Since?

[English](README.md) | **Italiano**

<img src="logo.svg" width="96" alt="">

Una piccola app Android che tiene traccia di quanto tempo è passato dall'ultima volta che è successo qualcosa, come annaffiare le piante, cambiare lo spazzolino o l'ultimo taglio di capelli.

Ogni voce è un **contatore**: un'etichetta, alcuni tag e il registro di ogni volta che è stato creato o azzerato. L'app mostra il tempo trascorso dall'ultimo azzeramento e può avvisarti quando un contatore è fermo da troppo. Un adesivo NFC può azzerare un contatore con un solo tocco.

Un server opzionale, ospitato da te, sincronizza i contatori tra il telefono e una web app che funziona in qualsiasi browser.

<img src="docs/app-list.png" width="240" alt="Elenco dei contatori"> <img src="docs/app-detail.png" width="240" alt="Dettaglio di un contatore con il registro degli azzeramenti">

## Funzionalità

- **Contatori**
  - Ogni contatore ha un'etichetta e dei tag separati da virgole.
  - Le schede mostrano il tempo trascorso con un numero grande ("12 days") e sotto la data dell'ultimo azzeramento.
- **Registro degli azzeramenti**
  - Ogni creazione o azzeramento viene salvato, marcato `create`, `manual` o `nfc`.
  - La pagina di dettaglio di ogni contatore mostra il registro come una timeline, con l'intervallo tra un azzeramento e l'altro.
  - Un piccolo grafico a barre mostra gli intervalli recenti e la loro media.
- **Azzeramento nel passato**: "Reset at…" nella pagina di dettaglio, per quando ti sei dimenticato di registrarlo.
- **Note**: "Reset…" nella pagina di dettaglio accetta una nota opzionale (es. "nuova marca di filtro"), mostrata nella timeline.
- **Archivio**: nasconde un contatore senza perderne la storia; i contatori archiviati non hanno promemoria e stanno dietro il chip "Archived".
- **Ricerca e ordinamento**: cerca tra etichette e tag; ordina per più in ritardo, azzerati di recente o nome (app e web).
- **Widget per la schermata Home**: i contatori che più hanno bisogno di un azzeramento, ognuno con il suo pulsante. I tempi si aggiornano a ogni modifica e ogni 30 minuti.
- **Usa la mia media**: la finestra di modifica può impostare "notify after" sul tuo intervallo medio tra gli azzeramenti.
- **Prima i ritardatari**: i contatori oltre il loro promemoria compaiono in cima (app e web).
- **Scorciatoie del launcher**: tieni premuta l'icona dell'app per azzerare i 4 contatori che ne hanno più bisogno.
- **Annulla**: ogni azzeramento mostra uno snackbar "Undo", e Undo rimuove quella voce dal registro.
- **Promemoria**
  - Imposta facoltativamente "notify after" in ore, giorni o settimane (o minuti in modalità Debug).
  - Un anello attorno al pulsante di azzeramento si riempie man mano che si avvicina il promemoria e diventa rosso quando è scaduto.
  - Toccando la notifica si apre la pagina del contatore. I suoi pulsanti azzerano il contatore o rimandano di un giorno.
- **Tag NFC**
  - Abbina un tag dalla pagina di dettaglio del contatore. L'app scrive `howlongsince://reset/<uuid>` (l'id di sincronizzazione del contatore, così il tag funziona su ogni telefono sincronizzato) sul tag e ne ricorda l'ID.
  - Toccare il tag azzera il contatore, anche ad app chiusa.
  - I tag non abbinati vengono rifiutati, e Unpair o Re-pair fanno smettere di funzionare un vecchio tag.
  - Oppure "Copy URL" e scrivi il tag con qualsiasi altra app NFC; il primo tocco di quel tag lo abbina.

  <img src="docs/nfc-pairing.png" width="240" alt="Finestra di abbinamento: copia l'URL o abbina ora">

- **Gruppi** (menu ☰ → Gruppi): un gruppo è una raccolta di contatori che si sincronizza, o no, per conto suo. La schermata Home mostra una sezione comprimibile per gruppo, il menu apre un singolo gruppo, e la finestra di modifica di un contatore lo sposta tra i gruppi. I contatori senza gruppo restano sul telefono. Eliminare un gruppo riporta i suoi contatori in "Senza gruppo".
  - **La sincronizzazione è per gruppo**: tocca un gruppo, inserisci l'URL del tuo server e una password di recupero, e attiva la sincronizzazione. Il telefono crea una chiave casuale e cifra il gruppo prima di inviarlo: il server non vede mai etichette, tag, note o nomi, né quale dispositivo ha registrato un azzeramento. Vede i metadati dei contatori (quanti, quando sono stati azzerati, intervalli dei promemoria). Questo telefono diventa amministratore del gruppo.
  - **Condividi** (l'icona condividi su un gruppo sincronizzato): per un amministratore, un link e un codice QR che contengono server, chiave e un invito monouso valido 24 ore. Su un altro telefono apri Gruppi → Unisciti e incolla il link (o apri un link `howlongsince://join…`). Chi entra con un invito è un membro: sincronizza, ma non può invitare né revocare.
  - **Amministratori**: vedono i dispositivi del gruppo (tocca il gruppo) e possono revocarli. Un dispositivo revocato smette di sincronizzare ma tiene ciò che ha già; per escludere qualcuno del tutto, crea un nuovo gruppo e spostaci i contatori.
  - **Password di recupero**: con il link del gruppo (il Condividi dei membri lo mostra senza invito, ed è il backup della chiave) fa entrare un nuovo telefono come amministratore, anche se ogni altro dispositivo è perso. Un membro che la conosce diventa amministratore inserendola. Non si può reimpostare, e chi ha sia il link sia la password è amministratore.
- **Tag**: una riga di filtri in cima all'elenco, con un colore fisso per ogni tag.
- **Impostazioni** (menu ☰)
  - Interruttore del tema scuro. Segue il sistema finché non lo cambi.
  - Modalità Debug, che consente promemoria in minuti.
  - Esportazione JSON e un'importazione. Il file viene controllato e confermi prima che venga sovrascritto qualcosa. "Sostituisci" scambia i gruppi con lo stesso nome con quelli del file, "Tieni entrambi" li importa come nuovi gruppi. In un gruppo sincronizzato, la sostituzione arriva a tutti i suoi membri.
- **Lingue**: inglese e italiano, secondo la lingua del telefono o del browser. Da Android 13 la lingua dell'app si può anche impostare a parte nelle impostazioni di sistema (Impostazioni → App → How Long Since? → Lingua).
- **Aspetto**: Material 3 con i colori del tuo sfondo (Android 12+) e una barra del titolo grande che si riduce scorrendo.

## Tecnologia

- Kotlin e Jetpack Compose (Material 3), in una sola Activity. Android 8.0 (API 26) o successivo.
- **Room** salva i dati in due tabelle: `Counter` ed `Event`. Il tempo trascorso viene calcolato dall'ultimo `Event` e mai salvato.
- **WorkManager** esegue un job ritardato per ogni contatore, sostituito a ogni modifica.
- L'**API NFC di Android**: record URI NDEF per il tocco, e reader mode durante l'abbinamento.
- La libreria integrata `org.json` per esportazione, importazione e sincronizzazione, e `HttpURLConnection` per la sincronizzazione.

## Struttura del codice

```
app/src/main/java/eu/etrunon/howlongsince/
  Data.kt          Entità Room, query al database, formattazione di tempi e tag
  MainActivity.kt  Schermata elenco, finestra di modifica, impostazioni, gestione degli intent di notifiche e NFC
  Detail.kt        Schermata di dettaglio: timeline, grafico degli intervalli, abbinamento NFC
  Notify.kt        Pianificazione dei promemoria e worker delle notifiche
  Nfc.kt           Scrittura dei tag e lettura dell'URI di azzeramento
  Export.kt        Esportazione, importazione e ripristino JSON
  Sync.kt          Sincronizzazione col server: formato JSON, unione e chiamate HTTP
  Crypto.kt        Derivazione delle chiavi e cifratura di un gruppo
  Groups.kt        Schermata Gruppi: crea, rinomina, elimina, sincronizza e condividi
  Join.kt          Link di condivisione, codice QR e unione a un gruppo
  Widget.kt        Widget per la schermata Home
  Theme.kt         Tema a colori dinamici e colori dei tag
app/src/test/.../SinceTest.kt  Test unitari (formattazione, tag, ritardo dei promemoria, URI NFC, andata e ritorno JSON di backup e sync)
server/
  main.go          Server di sincronizzazione: archivio di blob cifrati e handler HTTP
  main_test.go     Test dell'archivio dei blob (eseguito durante il docker build)
  web.test.mjs     Test della logica della web app: node --test server/web.test.mjs
  web/             Web app (index.html, style.css, core.js, app.js), inclusa nel binario del server
  Dockerfile, compose.yml
logo.svg           Logo (l'icona dell'app e la favicon web derivano da qui)
```

## Compilazione

Richiede JDK 21 (Gradle 8.11 non funziona con JDK più recenti) e l'Android SDK. Imposta `sdk.dir` in `local.properties`, oppure `ANDROID_HOME`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew test          # test unitari
./gradlew installDebug  # compila e installa su un telefono collegato (debug USB attivo)
```

L'NFC richiede un telefono vero e tag NDEF scrivibili (per esempio NTAG213/215/216).

## Server di sincronizzazione e web app (opzionali)

`server/` è un piccolo server Go che salva ogni gruppo sincronizzato come un file cifrato e serve la web app. L'immagine Docker è un singolo binario di circa 8 MB. Ognuno esegue la propria copia; l'app non ha un indirizzo di server predefinito. Il server non può leggere i tuoi contatori: cifratura e decifratura avvengono sul telefono e nel browser.

![Web app](docs/web.png)

**Web app**: tutto quello che fa il telefono, tranne ciò che richiede il telefono stesso.
- **Gruppi**: la pagina mostra ogni gruppo che conosce come sezione comprimibile (più "Senza gruppo", che vive solo nel local storage di questo browser), ognuna con il proprio stato di sincronizzazione. Una barra laterale (un cassetto che si apre con ☰ sugli schermi stretti, come sul telefono) elenca Tutti, Senza gruppo e ogni gruppo, poi Nuovo gruppo e Unisciti, e per il gruppo scelto Condividi (codice QR, link, copia, il menu di condivisione del browser), Rinomina, Dimentica e Apri nell'app, e le impostazioni. Puoi anche spostare un contatore tra gruppi dal suo modulo di modifica (lasciare un gruppo sincronizzato chiede conferma). La chiave è nella parte del link dopo `#`, che il browser non invia mai; i gruppi aperti vengono ricordati nel local storage di questo browser, e un link "Apri nell'app" ne passa uno al telefono. Richiede https (o localhost) per la crittografia del browser.
- **Contatori**: aggiungi (il pulsante + in basso a destra, come sul telefono, apre un modulo per etichetta, tag, gruppo e promemoria), azzera (adesso, con una nota o in un momento passato), annulla, archivia ed elimina. Aprendone uno vedi il registro degli azzeramenti, il grafico degli intervalli e un modulo per etichetta, tag, gruppo e promemoria "avvisa dopo" (il promemoria scatta sul telefono). Un tag NFC abbinato può essere disabbinato, ma l'abbinamento richiede il telefono. Ricerca, filtro per tag e ordinamento funzionano dentro ogni sezione. I tempi si aggiornano ogni minuto.
- **Esporta e importa** usano il file JSON del telefono, quindi un file passa dal telefono al web e viceversa: Sostituisci scambia i gruppi con lo stesso nome con quelli del file, Tieni entrambi li importa come nuovi gruppi.
- **Impostazioni** nella barra laterale: un interruttore del tema scuro (segue il sistema finché non lo usi), un interruttore debug che consente i minuti in "avvisa dopo" e la lingua (inglese o italiano, secondo il browser).
- **Non sul web**: abbinamento NFC, promemoria e notifiche, il widget e i gruppi che stanno su un server diverso da quello della pagina.

**Avvio**
```bash
cd server
echo HLS_TOKEN=$(openssl rand -hex 16) > .env   # facoltativo: per GET /stats e per i gruppi creati prima dei token per dispositivo
mkdir -p data                                   # deve essere scrivibile dall'uid 1000
docker compose up -d --build                    # in ascolto su 127.0.0.1:13387
```
Metti davanti un reverse proxy con HTTPS. Il server parla solo HTTP semplice; il proxy gestisce il certificato (Let's Encrypt, il tuo, …), limita i tentativi di indovinare il token e aggiunge gli header di sicurezza per il browser. nginx fa tutto questo senza plugin:
```nginx
# nel blocco http {}
limit_req_zone $binary_remote_addr zone=hls:1m rate=30r/m;

server {
    listen 443 ssl;
    server_name hls.example.com;
    ssl_certificate     /etc/letsencrypt/live/hls.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/hls.example.com/privkey.pem;

    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy no-referrer always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'" always;

    location /s/ {
        limit_req zone=hls burst=20 nodelay;   # ~30 sync/min per IP, picchi consentiti
        proxy_pass http://127.0.0.1:13387;
    }
    location / {
        proxy_pass http://127.0.0.1:13387;
    }
}
```
Con Caddy, gli stessi header vanno in un blocco `header`; il rate limiting richiede il plugin `caddy-ratelimit`. `tailscale serve` fornisce HTTPS ma non può aggiungere header né limiti, il che va bene quando il server è raggiungibile solo dalla tua tailnet.

L'app Android richiede un URL `https://` con un certificato di cui Android si fida di default (Let's Encrypt, Tailscale, una CA commerciale). I certificati autofirmati o di una CA privata installati sul telefono non sono accettati dall'app.

Poi:
- **Web**: apri il link di condivisione di un gruppo: con un invito si unisce, senza chiede la password di recupero. "Nuovo gruppo" ne crea uno qui e chiede la sua password di recupero.
- **Installa la web app** (finestra e icona proprie):
  - Chrome / Chromium / Edge (qualsiasi OS): l'icona di installazione a destra della barra degli indirizzi.
  - Firefox su Windows (143+): l'icona "aggiungi alla barra delle applicazioni" nella barra degli indirizzi la fissa sulla barra di Windows. Se manca, imposta `browser.taskbarTabs.enabled` a `true` in `about:config`.
  - Firefox su Android: menu → "Aggiungi a schermata Home".
  - Firefox su Linux: nessuna opzione di installazione; usa una scheda fissata o Chromium.
- **Telefono**: Gruppi → tocca un gruppo → inserisci URL e una password di recupero → Attiva la sincronizzazione.
- **Aggiornare dal `TOKEN` condiviso**: lascia `TOKEN` impostato. Ogni dispositivo ci rivendica il proprio token alla prima sincronizzazione, come membro; il primo che inserisce una password di recupero (web: la chiede; telefono: tocca il gruppo) diventa amministratore, e da lì `TOKEN` non apre più quel gruppo. Quando tutti i gruppi sono rivendicati, `TOKEN` serve solo per `/stats`.

**Configurazione** (variabili d'ambiente): `TOKEN` (facoltativo: rivendica i gruppi creati prima dei token per dispositivo, e legge `/stats`), `CREATES_PER_IP_HOUR` (predefinito 10), `MAX_GROUPS` (predefinito 10000), `DATA_DIR` (predefinito `/data`, i blob vanno in `s/` al suo interno), `ADDR` (predefinito `:8080`). `compose.yml` collega `HLS_TOKEN` da `.env` a `TOKEN`. Chiunque può creare un gruppo, entro questi limiti. I limiti per IP leggono l'ultimo indirizzo di `X-Forwarded-For`, che imposta il reverse proxy. Una volta al giorno il server elimina i gruppi non sincronizzati da un anno, o da sei mesi con meno di 3 contatori.

**API** (`{id}` sono 32 caratteri esadecimali derivati dal segreto del gruppo; "dispositivo" significa il token del dispositivo come `Authorization: Bearer`):
- `GET /` web app, `GET /health` risponde `ok`, `GET /stats` (con `TOKEN`) restituisce i totali: gruppi, dispositivi, contatori attivi, azzeramenti negli ultimi 7 e 30 giorni.
- `GET /s/{id}` (dispositivo) restituisce `{etag, ciphertext}`. `PUT /s/{id}` (dispositivo) riceve `{ciphertext}` e `If-Match: <etag>`, e risponde 409 se qualcun altro ha scritto prima. Senza `If-Match` crea il gruppo: `{ciphertext, deviceId, recovery}`, senza token, restituisce `{etag, token}` e chi chiama è amministratore.
- `POST /s/{id}/invite` (amministratore) restituisce `{nonce}`, monouso, 24 h. `POST /s/{id}/enroll {deviceId, nonce}` crea un membro, `{deviceId, recovery}` un amministratore; entrambi restituiscono `{token}`. `POST /s/{id}/promote {recovery}` (dispositivo) rende amministratore chi chiama. `POST /s/{id}/recovery {recovery}` (amministratore) cambia la password.
- `GET /s/{id}/devices` (amministratore) elenca `{id, admin, created, lastSeen}`. `POST /s/{id}/revoke {deviceId}` (amministratore). `POST /s/{id}/leave` (dispositivo); il gruppo viene eliminato quando non resta nessun dispositivo.
- `POST /s/{id}/claim {deviceId}` (con `TOKEN`, solo gruppi senza password di recupero) restituisce un `{token}` da membro.
- `recovery` è PBKDF2-HMAC-SHA256 della password, con `{id}` come sale, 600 000 iterazioni, in base64: la password non lascia mai il dispositivo. Il server salva lo SHA-256 di questo valore e di ogni token, e ammette 5 password errate per gruppo all'ora e 20 errori per IP all'ora.

**Come funziona la sincronizzazione**
- La chiave: ogni gruppo sincronizzato ha un segreto casuale di 32 byte creato sul telefono. HKDF-SHA256 lo trasforma nell'id che vede il server e in una chiave AES-256-GCM. Il segreto e il token del dispositivo stanno in un file di preferenze separato che i backup saltano.
- Il blob (formato v2) è JSON: etichette, tag, id NFC, note, nomi di gruppi e dispositivi, e quale dispositivo ha registrato ogni azzeramento sono cifrati uno per uno con AES-GCM (autenticati con id del gruppo, id del record e nome del campo). Il resto è leggibile: id dei contatori, `updatedAt`, `deleted`, `archived`, intervalli dei promemoria e orari degli azzeramenti. Il documento viaggia come un'unica stringa con un MAC (SHA-256 dei suoi byte esatti, sigillato con la chiave), così un server senza la chiave non può cambiare nulla senza che si veda. I client leggono ancora il vecchio formato tutto cifrato.
- Il server vede i campi leggibili qui sopra, la dimensione del blob, i tempi e il tuo indirizzo IP.
- Una sincronizzazione è: scarica il blob, decifra, unisci con il gruppo locale, cifra, rimettilo con `If-Match`; con un 409 ricomincia. L'unione la fanno il telefono e la web app, mai il server.
- Contatori e azzeramenti hanno un UUID. Il telefono mantiene i suoi id locali per i promemoria; i tag NFC contengono l'UUID.
- Modifiche ai contatori: vince l'`updatedAt` più recente. Gli azzeramenti vengono uniti per id. Spostare un contatore in un altro gruppo mantiene il suo UUID e scrive un tombstone nel vecchio gruppo, così lì sparisce per tutti.
- Le eliminazioni sono tombstone (`deleted: true`), così raggiungono ogni dispositivo.
- L'app si sincronizza all'apertura, poco dopo ogni modifica e ogni 15 minuti in background (il minimo di Android). La web app si sincronizza ogni 15 minuti e quando la sua scheda torna visibile.
- I contatori eliminati vengono rimossi definitivamente dopo 90 giorni. Un dispositivo offline più a lungo potrebbe riportarne indietro uno. Può farlo anche un server che serve un blob vecchio.
- Se vanno persi tutti i dispositivi che hanno la chiave di un gruppo, il link e la password di recupero lo riportano indietro. Conserva entrambi.
- Chi ha sia il link sia la password di recupero è amministratore, e gli amministratori sono pari, quindi uno può revocare l'altro. Chi ha solo il link può provare password online, 5 all'ora; il gestore del server può provarle offline contro l'hash salvato, rallentato da PBKDF2. Una password dimenticata senza più dispositivi amministratori significa che nessuno può più invitare o revocare; i membri continuano a sincronizzare, e la via d'uscita è un nuovo gruppo.
- La revoca impedisce a un dispositivo di sincronizzare, ma gli restano la chiave e ciò che ha già scaricato. Per escludere qualcuno del tutto, crea un nuovo gruppo e spostaci i contatori.
- Le app precedenti al formato v2 non leggono i gruppi v2.
- L'app web tiene chiavi e token in `localStorage`: cancellare i dati del sito li perde, insieme ai contatori Senza gruppo che esistono solo lì. "Copia link" salva i gruppi; Esporta salva i Senza gruppo.
- Accanto al blob il server tiene, senza mai inviarli ai client, i dispositivi del gruppo (id, hash del token, ruolo, creazione, ultimo accesso), gli inviti aperti, l'hash di recupero e quando è stato sincronizzato l'ultima volta (aggiornato al massimo una volta all'ora).
- Il server chiude le connessioni lente o bloccate (10 s per inviare gli header, 30 s per richiesta) e scrive i blob in modo atomico.

**Più telefoni sullo stesso server**
- Un gruppo è condiviso dai suoi amministratori con inviti, il che va bene per una famiglia che si divide le faccende; gli altri gruppi restano privati su un telefono o vanno ad altre persone. Un server può ospitare molti gruppi.
- Gli azzeramenti non vanno mai persi: ognuno ha un UUID e il server li tiene tutti, anche quando due telefoni azzerano nello stesso istante.
- Le modifiche valgono per l'intero contatore: l'`updatedAt` più recente sostituisce insieme etichetta, tag e promemoria, quindi due persone che cambiano campi diversi quasi nello stesso momento perdono una delle due modifiche.
- L'ordine delle modifiche usa l'orologio di ogni telefono; un telefono con l'orologio sballato di qualche minuto può vincere o perdere una modifica a torto.
- I promemoria scattano su ogni telefono che ha il contatore. Un tag abbinato su un telefono funziona su tutti: il tag contiene l'id di sincronizzazione del contatore, e l'abbinamento si sincronizza.
- Ogni sincronizzazione trasporta lo stato completo del gruppo, il che va bene per pochi telefoni e centinaia di contatori (le richieste sono limitate a 10 MB).

## Licenza

GPL-3.0, vedi [LICENSE](LICENSE). Puoi usarlo, modificarlo e condividerlo; le modifiche condivise devono restare sotto GPL.
