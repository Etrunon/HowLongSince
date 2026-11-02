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
- **Tag**: una riga di filtri in cima all'elenco, con un colore fisso per ogni tag.
- **Impostazioni** (menu ☰)
  - Interruttore del tema scuro. Segue il sistema finché non lo cambi.
  - Modalità Debug, che consente promemoria in minuti.
  - Esportazione JSON e un'importazione che sostituisce tutti i dati. Il file viene controllato e confermi prima che venga sovrascritto qualcosa. Con la sincronizzazione attiva, l'importazione sostituisce anche i dati sul server.
  - URL e token del server di sincronizzazione, con lo stato dell'ultima sincronizzazione e un pulsante "Sync now". Lascia l'URL vuoto per disattivare la sincronizzazione.
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
  Sync.kt          Sincronizzazione col server: formato JSON e chiamata HTTP
  Widget.kt        Widget per la schermata Home
  Theme.kt         Tema a colori dinamici e colori dei tag
app/src/test/.../SinceTest.kt  Test unitari (formattazione, tag, ritardo dei promemoria, URI NFC, andata e ritorno JSON di backup e sync)
server/
  main.go          Server di sincronizzazione: logica di merge, salvataggio su file JSON, handler HTTP
  main_test.go     Test unitario del merge (eseguito durante il docker build)
  index.html       Web app, inclusa nel binario del server
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

`server/` è un piccolo server Go che salva tutto in un unico file JSON e serve la web app. L'immagine Docker è un singolo binario di circa 8 MB. Ognuno esegue la propria copia; l'app non ha un indirizzo di server predefinito.

![Web app](docs/web.png)

**Web app**: elenco dei contatori con il tempo dall'ultimo azzeramento, ordinati dal più vecchio. Puoi aggiungere, azzerare ed eliminare contatori. Aprendo un contatore vedi il registro degli azzeramenti, il grafico degli intervalli e un modulo per etichetta, tag e promemoria "notify after" (il promemoria scatta sul telefono). Un tag NFC abbinato può essere disabbinato, ma l'abbinamento richiede il telefono. I tempi sulla pagina si aggiornano ogni minuto.

**Avvio**
```bash
cd server
echo HLS_TOKEN=$(openssl rand -hex 16) > .env   # il token che serve ai client
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

    location /sync {
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
- **Web**: apri l'URL e accedi con il token. Il modulo di accesso funziona con i password manager, e il token resta poi salvato nel browser.
- **Installa la web app** (finestra e icona proprie):
  - Chrome / Chromium / Edge (qualsiasi OS): l'icona di installazione a destra della barra degli indirizzi.
  - Firefox su Windows (143+): l'icona "aggiungi alla barra delle applicazioni" nella barra degli indirizzi la fissa sulla barra di Windows. Se manca, imposta `browser.taskbarTabs.enabled` a `true` in `about:config`.
  - Firefox su Android: menu → "Aggiungi a schermata Home".
  - Firefox su Linux: nessuna opzione di installazione; usa una scheda fissata o Chromium.
- **Telefono**: Impostazioni → Sync server, inserisci URL e token.

**Configurazione** (variabili d'ambiente): `TOKEN` (token bearer, obbligatorio: senza il server non parte), `INSECURE=1` (avvio senza token, solo su una rete fidata), `DATA_FILE` (default `/data/data.json`), `ADDR` (default `:8080`). `compose.yml` mappa `HLS_TOKEN` da `.env` su `TOKEN`.

**API**: `GET /` web app, `GET /health` risponde `ok`, `POST /sync` riceve `{counters, events}` (tutti o solo quelli modificati) e restituisce lo stato completo unito.

**Come funziona la sincronizzazione**
- Contatori e azzeramenti hanno un UUID per la sincronizzazione. Il telefono mantiene i suoi id locali per i promemoria; i tag NFC contengono l'UUID.
- Modifiche ai contatori: vince l'`updatedAt` più recente. Gli azzeramenti vengono uniti per id.
- Le eliminazioni sono tombstone (`deleted: true`), così raggiungono ogni dispositivo.
- L'app invia tutto all'apertura, poco dopo ogni modifica e ogni 15 minuti in background (il minimo di Android), poi applica il risultato unito. La web app si sincronizza ogni 15 minuti e quando la sua scheda torna visibile.
- I contatori eliminati vengono rimossi definitivamente dopo 90 giorni, sul server e sul telefono. Un dispositivo offline più a lungo potrebbe riportarne indietro uno.
- Le risposte sono compresse con gzip. La web app si può installare come app (manifest + icona).
- Il server chiude le connessioni lente o bloccate (10 s per inviare gli header, 30 s per richiesta).
- Il server scrive il file in modo atomico e si rifiuta di partire se è corrotto, invece di sovrascriverlo.

**Più telefoni sullo stesso server**
- Ogni telefono (e browser) con il token condivide gli stessi dati, ideale per una famiglia che si divide le faccende. Per persone separate, esegui un container per persona, ognuno sulla sua porta.
- Gli azzeramenti non vanno mai persi: ognuno ha un UUID e il server li tiene tutti, anche quando due telefoni azzerano nello stesso istante.
- Le modifiche valgono per l'intero contatore: l'`updatedAt` più recente sostituisce insieme etichetta, tag e promemoria, quindi due persone che cambiano campi diversi quasi nello stesso momento perdono una delle due modifiche.
- L'ordine delle modifiche usa l'orologio di ogni telefono; un telefono con l'orologio sballato di qualche minuto può vincere o perdere una modifica a torto.
- I promemoria scattano su ogni telefono che ha il contatore. Un tag abbinato su un telefono funziona su tutti: il tag contiene l'id di sincronizzazione del contatore, e l'abbinamento si sincronizza.
- Il server gestisce una sincronizzazione alla volta, e ogni sincronizzazione trasporta lo stato completo, il che va bene per pochi telefoni e centinaia di contatori (le richieste sono limitate a 10 MB).

## Licenza

GPL-3.0, vedi [LICENSE](LICENSE). Puoi usarlo, modificarlo e condividerlo; le modifiche condivise devono restare sotto GPL.
