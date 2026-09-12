# Diario Lavorativo

App Android per registrare la giornata di cantiere: orari, pause, cantieri,
attivita', foto e note. Tutto in locale sul telefono, funziona senza rete.

## 1. Obiettivo
Sostituire il quaderno di cantiere. Un operaio deve poter aprire l'app,
premere un pulsante grande, e avere l'orario registrato senza pensarci.

## 2. Stack tecnico
- Kotlin, Jetpack Compose, Material 3
- Room per il database locale
- DataStore Preferences per le impostazioni
- ViewModel + StateFlow + Coroutines
- Navigation Compose
- Iniezione manuale delle dipendenze tramite AppContainer (no Hilt)
- minSdk 26, compileSdk e targetSdk 35
- Nessuna dipendenza dai Google Play Services

## 3. Struttura delle cartelle
app/src/main/java/it/diario/lavorativo/
- core/di          AppContainer, factory dei ViewModel
- core/location    LocationProvider e AndroidLocationProvider
- data/local       database Room, entity, dao, migrazioni
- data/mapper      conversioni entity <-> dominio
- data/prefs       impostazioni su DataStore
- data/repository  implementazioni dei repository
- domain/model     modelli puri (WorkDay, Break, Site, GeoPoint)
- domain/repository interfacce dei repository
- domain/service   WorkTimeCalculator, SiteLocationMatcher
- ui/today         schermata Oggi
- ui/sites         lista e scheda cantiere
- ui/settings      impostazioni
- ui/navigation    grafo di navigazione e barra in basso
- ui/vehicle       mezzo aziendale: schermata e moduli di inserimento
- ui/theme         colori, tipografia
- core/util        MoneyFormat: importi e numeri digitati a mano
- core/reminder    promemoria settimanale e avviso scadenze del mezzo

## 4. Database
Versione attuale: 6

Tabelle: work_days, breaks, sites, activities, events, communications,
photos, voice_notes, vehicles, fuel_stops, vehicle_expenses, maintenances

Migrazione 1 -> 2 (non distruttiva): aggiunge a sites le colonne
phone, latitude, longitude, radiusMeters (default 150).

Migrazione 2 -> 3 (puramente additiva): crea le tabelle activities,
events e communications, con gli stessi indici dichiarati nelle @Entity.
Nessun dato esistente viene toccato.

Migrazione 3 -> 4 (additiva): crea la tabella photos. I collegamenti a
lavorazione ed evento sono SET_NULL e non CASCADE: cancellando una di
quelle voci la foto resta, slegata. Una foto e' una prova, non deve
sparire per un ripensamento su un'altra voce.

Migrazione 4 -> 5 (additiva): crea la tabella voice_notes.

Migrazione 5 -> 6 (additiva): crea vehicles, fuel_stops, vehicle_expenses
e maintenances, e aggiunge a work_days la colonna travelKm con un ALTER
TABLE. Le giornate registrate prima si trovano il campo a null, cioe'
"non lo so", che e' esattamente la verita'. Nessuna riga viene riscritta
e nessuna tabella viene ricreata.

Convenzioni: le date sono epochDay, gli orari sono millisecondi epoch UTC.
La conversione al fuso locale avviene solo nella UI. Gli importi in denaro
sono numeri interi in centesimi: la virgola mobile a fine anno non torna
con gli scontrini.

## 5. Fasi di sviluppo
1. Base e schermata Oggi - COMPLETATA
2-5. Accorpate o rimandate secondo le priorita' concordate
6. Cantieri e geolocalizzazione - CODICE COMPLETO, test superati
7. Storico e calendario - CODICE COMPLETO, test superati
8. Attivita', eventi e comunicazioni - CODICE COMPLETO, test superati
9. Foto - CODICE COMPLETO, test superati
   RAPPORTINO SETTIMANALE - CODICE COMPLETO, test superati
   (inserito fuori sequenza su richiesta: e' la funzione che serve
   ogni lunedi', quindi ha la precedenza sulle foto)
10. Dashboard e statistiche - CODICE COMPLETO, test superati
11. Esportazione PDF, CSV, XLSX - CODICE COMPLETO, test superati
12. Backup, ripristino, note vocali - CODICE COMPLETO, test superati
13. Mezzo aziendale: rifornimenti, pedaggi, parcheggi, manutenzioni,
    scadenze e chilometri - CODICE COMPLETO, test superati

## 6. Cosa funziona
Fase 1:
- Schermata Oggi con stati NON INIZIATA, AL LAVORO, IN PAUSA, CONCLUSA
- INIZIA e TERMINA GIORNATA
- Pause multiple con un solo pulsante di attivazione
- Chiusura automatica della pausa aperta quando si termina la giornata
- Correzione manuale degli orari con selettore ora
- Calcolo lordo, netto e straordinario sul netto
- Banner di avviso se la giornata precedente e' rimasta aperta
- Scelta o creazione rapida del cantiere
- Impostazioni: nome, orario standard a passi di 15 minuti, straordinario

Fase 6:
- Anagrafica cantieri completa: nome, indirizzo, citta', committente,
  impresa, referente, telefono, note
- Ricerca per nome, citta', committente, impresa
- Archiviazione e riattivazione, con filtro per vedere gli archiviati
- Eliminazione con conferma, che avvisa se ci sono giornate collegate
  e suggerisce di archiviare invece di cancellare
- Registrazione della posizione del cantiere dal punto in cui si e'
  ("Registra qui"), con precisione mostrata in metri
- Raggio del cantiere selezionabile: 50, 100, 150, 300, 500, 1000 metri
- Pulsante "Dove sono" nella schermata Oggi: propone il cantiere
  riconosciuto dal GPS
- Gestione di tutti i casi: cantiere certo, piu' cantieri vicini,
  nessuno nel raggio, GPS spento, permesso negato, segnale assente
- La precisione del sensore allarga il raggio utile, con un limite
  di 200 metri di tolleranza
- Calcolo dei chilometri percorsi con soglia anti deriva GPS
  (metodo travelledKm, scritto e testato ma non ancora collegato alla UI)

Fase 7 (storico e calendario, unificati in una sola schermata):
- Vista ELENCO: una scheda per giornata con data, cantiere, orari,
  netto e straordinario.
- Vista CALENDARIO: griglia mensile da lunedi' a domenica, ogni giorno
  colorato secondo il tipo (lavoro, ferie, permesso, malattia, festivo).
  Il giorno selezionato apre la scheda corrispondente.
- Navigazione fra i mesi con i pulsanti avanti e indietro. Non si va
  oltre il mese corrente ne' prima della prima giornata registrata.
- Riepilogo del mese in testa: giorni lavorati, netto totale, media
  giornaliera, straordinario, pause, giorni di assenza per tipo.
- Dettaglio giornata: correzione di ingresso e uscita con selettore
  orario, cambio cantiere, cambio tipo giornata, modifica di lavoro
  svolto e note, eliminazione delle singole pause.
- Le modifiche si accumulano e si salvano con il pulsante SALVA:
  nessuna scrittura immediata al tocco.
- Turno oltre la mezzanotte riconosciuto: se l'uscita e' precedente
  all'ingresso viene registrata al giorno successivo.
- Giornata dimenticata: da una data vuota si puo' creare la giornata
  di lavoro oppure segnare ferie, permesso o malattia.
- Eliminazione di una giornata con richiesta di conferma.

Fase 8 (attivita', eventi e comunicazioni):
- Schermata a tre schede raggiungibile da Oggi e dal dettaglio giornata.
- LAVORI: lavorazione con categoria (muratura, marmo, intonaco, pavimenti,
  rivestimenti, demolizione, scavo, impianti, cartongesso, pulizia, carico
  e scarico, sopralluogo, preparazione, altro), descrizione, quantita'
  libera, orario facoltativo e note. Le descrizioni gia' usate per quella
  categoria vengono riproposte come suggerimento.
- EVENTI: ritardo, fermo lavori, consegna materiale, materiale mancante,
  visita, sopralluogo tecnico, infortunio, guasto attrezzatura, maltempo,
  variante, problema. Con orario, minuti persi, gravita' e segnalibro
  "ancora da risolvere". Gli eventi gravi o irrisolti sono evidenziati.
- CONTATTI: telefonate fatte e ricevute, WhatsApp, mail, SMS e parole dette
  di persona, con chi, quando, oggetto, contenuto integrale, durata e
  segnalibro "da mettere per iscritto".
- Ricezione della condivisione di sistema: da WhatsApp o dalla posta si
  selezionano i messaggi, si preme Condividi e si sceglie Diario Lavorativo.
  Il testo arriva integro nella scheda, gia' compilata.
- Riconoscimento automatico del formato WhatsApp: dalla prima riga del tipo
  "[12/03/26, 14:32] Mario Rossi: testo" viene estratto il nome del mittente.
  Riconosciuto anche il formato con trattino. Se il formato non e' noto il
  testo resta integro e il resto lo compila l'utente.
- Il canale viene dedotto dal pacchetto di origine (WhatsApp, Gmail,
  Outlook, messaggi); se non e' riconoscibile lo sceglie l'utente.
- Se si annota una voce in un giorno mai timbrato, la giornata viene creata
  al momento: la voce non va persa.

Rapportino settimanale e promemoria:
- Il foglio della settimana si costruisce da solo dalle giornate gia'
  registrate: orari, cantieri, lavorazioni, ore nette e straordinari.
  L'utente controlla e corregge, non ricompila nulla da zero.
- Sette righe fisse da lunedi' a domenica, anche per i giorni vuoti:
  sul cartellino cartaceo le righe ci sono comunque e servono a mostrare
  che non e' un buco.
- Ferie, permessi e malattia compaiono sulla riga senza produrre ore.
- Se una giornata e' rimasta senza orario di uscita il foglio lo segnala
  e non si considera pronto per la consegna.
- Elenco dei cantieri della settimana e degli eventi da segnalare (solo
  quelli gravi, importanti o ancora irrisolti).
- Campo note libero per la sede.
- Navigazione fra le settimane, senza poter andare oltre quella corrente.
- Promemoria: notifica il lunedi' alle 8:20, che riguarda SEMPRE la
  settimana appena conclusa. Giorno e ora sono modificabili.
- Toccando la notifica si apre direttamente il rapportino.
- Il promemoria viene riarmato dopo il riavvio del telefono e a ogni
  avvio dell'app.

Fase 9 (foto):
- Scatto con la fotocamera di sistema e scelta dalla galleria.
- Griglia a tre colonne per giornata, con miniature caricate fuori dal
  thread grafico per non far scattare lo scorrimento.
- Foto a schermo intero con didascalia.
- Collegamento facoltativo a una lavorazione o a un evento: "crepa" da
  sola non dice niente mesi dopo, "crepa - problema segnalato il 12
  marzo" e' quello che vale in una contestazione.
- Rotazione automatica secondo l'orientamento EXIF: senza, le foto
  verticali comparirebbero coricate su un lato.
- Le foto scattate vanno PRIMA in galleria, nell'album "Diario
  Lavorativo", e poi nell'app: cosi' restano anche disinstallando l'app o
  cambiando telefono, e si mandano al cliente da WhatsApp senza passare
  da qui.
- L'app ne tiene una copia ridotta a 1600 pixel di lato lungo, JPEG
  all'85 per cento, per le miniature e per il PDF. Tenere due volte gli
  otto megapixel dell'originale riempirebbe il telefono senza motivo.
- Eliminando una foto dal diario l'originale in galleria NON viene
  toccato: e' del telefono, non dell'app.
- Nome del file con data e ora leggibili, e suffisso automatico se due
  scatti cadono nello stesso secondo.
- Eliminazione con conferma; pulizia dei file rimasti orfani.
- Se si scatta in un giorno mai timbrato, la giornata viene creata al
  momento.

Fase 10 - Statistiche:
- Tre archi di tempo: settimana, mese, anno, con le frecce per
  scorrere indietro. Avanti si ferma a oggi: nel futuro non c'e'
  niente da vedere.
- Quadro del periodo: ore nette, giorni lavorati, straordinario,
  media al giorno, pause, e lo scarto rispetto al periodo prima.
  Sapere di avere fatto 160 ore dice poco, sapere di averne fatte 12
  in piu' del mese scorso dice molto.
- Grafico a barre con granularita' che segue il periodo: i sette
  giorni nella settimana, le settimane nel mese, i dodici mesi
  nell'anno. Trecentosessantacinque barre su un telefono non
  servirebbero a nulla.
- Ore per cantiere, dal piu' impegnativo al meno, con i giorni. Le
  giornate senza cantiere finiscono sotto "Senza cantiere" invece di
  sparire: sono ore lavorate e devono tornare nel totale.
- Le lavorazioni piu' frequenti, prime sei.
- In fondo ferie, permessi e malattia, le giornate con eventi e, in
  rosso, le questioni ancora aperte.
- I numeri escono dagli stessi calcolatori dello storico e del
  rapportino: se la dashboard dicesse cifre diverse, l'app perderebbe
  di credibilita'.

Fase 11 - Esportazione:
- Tre formati: PDF, CSV e foglio di calcolo XLSX.
- Quattro periodi: settimana, mese, anno, tutto.
- SETTIMANA + PDF produce il rapportino impaginato sul modulo cartaceo
  dell'azienda, "ORE DELLA SETTIMANA": cinque colonne (GIORNI, CLIENTE
  O CANTIERE, H, LAVORAZIONE E MATERIALE, COD / DOCUMENTI), sette righe
  da lunedi' a domenica con la domenica in rosso, e il totale ore in
  fondo gia' sommato. Pronto da stampare e consegnare.
- Gli altri periodi escono come tabelle: Riepilogo, Giornate,
  Lavorazioni, Eventi e, solo se richieste, Comunicazioni.
- Interruttori per scegliere cosa mettere dentro. Le comunicazioni
  sono spente di partenza.
- Il file si prepara e si apre subito la condivisione di sistema: mail,
  WhatsApp o salvataggio dove si vuole. C'e' anche il tasto per aprirlo
  e controllarlo prima di mandarlo.
- Si arriva dalle Impostazioni, col tasto ESPORTA E INVIA.

Fase 12 - Backup, ripristino e note vocali:
- Backup dell'intero diario in un solo file .zip, salvato dove decide
  l'utente tramite il selettore del sistema: Download, chiavetta, Drive.
  Nessun permesso di archiviazione da chiedere.
- Interruttore per includere o no foto e note vocali: senza, il file e'
  minuscolo e contiene comunque tutti i dati.
- Ripristino in due passi. Prima si legge solo la carta d'identita' del
  file e si mostra cosa contiene (giornate, cantieri, foto, note,
  periodo coperto); solo dopo la conferma si sostituisce il diario.
- Il ripristino avviene in una sola transazione: o entra tutto, o non
  cambia niente.
- Note vocali: si registra tenendo premuto un pulsante grande in fondo
  alla schermata, con i secondi che scorrono. Si riascoltano, si
  cancellano e si puo' scrivere la trascrizione a fianco, senza
  perdere l'audio.
- Si arriva alle note vocali dalla schermata Oggi e da ogni giornata
  del calendario, a fianco del pulsante FOTO.
- Al backup si arriva dalle Impostazioni, con BACKUP E RIPRISTINO.

### Fase 13 - Mezzo aziendale
- Schermata MEZZO AZIENDALE, dalle Impostazioni. Si registra il furgone
  o l'auto con modello e targa. Se il mezzo cambia, il vecchio resta con
  tutti i suoi dati: serve se in sede chiedono i conti dell'anno scorso.
- Tre pulsanti grandi in cima: PIENO, SPESA, OFFICINA.
- Rifornimenti con litri, importo, contachilometri, pieno o rabbocco e
  distributore. Mentre si scrive compare il prezzo al litro calcolato: se
  esce diciassette euro al litro, una cifra e' storta e si vede subito.
- Consumo misurato da pieno a pieno. I rabbocchi in mezzo entrano nel
  conto dei litri. Se il contachilometri non e' stato segnato quel tratto
  si salta: un consumo inventato e' peggio di un consumo mancante, perche'
  sembra vero.
- Pedaggi, parcheggi, lavaggi, multe. Se quel giorno c'e' una giornata
  registrata la spesa ci si aggancia da sola.
- Interventi in officina con data, chilometri, costo, officina e la
  prossima scadenza, sia a calendario sia a chilometri.
- Scadenze: la revisione scade a tempo, il tagliando a chilometri, e se un
  intervento ha tutte e due vale quella che arriva prima. Compaiono in
  cima alla schermata, rosse se passate.
- Avviso a notifica delle scadenze, agganciato alla stessa sveglia del
  foglio settimanale: una sveglia sola per due avvisi, cosi' c'e' una cosa
  in meno che il risparmio energetico puo' spegnere di nascosto. Canale di
  notifica separato, quindi si puo' spegnere solo questo.
- Nessuna scadenza inventata: se l'officina non ha detto quando tornare,
  l'app tace. Un avviso finto dopo due volte si impara a ignorare, e
  allora non serve piu' nemmeno quando e' vero. La proposta di chilometri
  che compare nel modulo si puo' cambiare prima di salvare, e non tocca
  mai un campo gia' scritto dall'utente.
- Chilometri percorsi sulla giornata, nella schermata del giorno. Lasciato
  vuoto vuol dire "non l'ho segnato", non zero: nei totali del mese la
  giornata senza il dato non abbassa la media.
- Esportazione: quattro fogli nuovi (Mezzo, Rifornimenti, Pedaggi e
  parcheggi, Manutenzioni) e la colonna Km nel foglio Giornate. I fogli
  compaiono solo se c'e' qualcosa dentro: chi non ha il mezzo non si
  ritrova quattro pagine vuote. L'interruttore e' acceso di partenza, al
  contrario di quello delle comunicazioni, perche' rifornimenti e pedaggi
  sono spese di lavoro che la sede chiede.
- Backup: il formato passa alla versione 2 e comprende il mezzo. Un backup
  vecchio si legge ancora, con le tabelle nuove vuote; un backup nuovo su
  un'app vecchia viene rifiutato invece di perdere i dati per strada.

## 7. Decisioni tecniche e perche'
- JSON scritto e riletto da codice nostro, in Kotlin puro. Android ha
  org.json gia' dentro, ma e' codice che fuori da un telefono non si
  puo' provare: il ripristino e' l'unica funzione dove uno sbaglio non
  da' fastidio, cancella il lavoro di mesi. Scritto a mano si prova
  davvero, andata e ritorno.
- I numeri nel backup si conservano come erano scritti, non come
  Double: un istante in millisecondi riscritto come 1.774E12 sarebbe un
  orario sbagliato al ritorno.
- Il backup e' uno zip con dentro manifest.json, dati.json e le
  cartelle foto e note. Formato aperto: si apre con qualsiasi computer
  e i dati si leggono anche senza l'app. Un backup che si apre solo col
  programma che l'ha fatto non e' un backup, e' un ostaggio.
- In lettura non ci si fida di niente: file troncati, file di altre
  applicazioni, backup di versioni future e archivi con percorsi tipo
  "../../" vengono respinti. Le singole righe rovinate invece si
  saltano e si contano: su mille giornate meglio recuperarne 999.
- Note vocali in AAC dentro m4a, un canale a 22 kHz. E' una voce a
  mezzo metro in mezzo al rumore del cantiere: la qualita' da studio
  non serve e raddoppierebbe lo spazio. Un minuto sta sotto i 200 kB.
- L'audio delle note sta nello spazio privato dell'app e non in
  galleria, al contrario delle foto: non serve a nessun'altra app.
- XLSX scritto a mano con java.util.zip, senza librerie: un xlsx e' uno
  zip con dentro qualche file XML. L'alternativa era Apache POI, che su
  Android pesa decine di megabyte. Il file prodotto e' stato riaperto
  con un lettore vero per essere sicuri che si apra davvero.
- PDF con android.graphics.pdf.PdfDocument, gia' dentro Android: niente
  iText, niente PdfBox.
- CSV col punto e virgola e col BOM: Excel in italiano si aspetta il
  punto e virgola, e senza BOM le lettere accentate escono a scacchi.
  Sono due dettagli che decidono se il file si apre al primo colpo.
- Le giornate mai chiuse restano fuori dai totali esportati. Una
  giornata aperta il 5 marzo ed esportata ad aprile avrebbe accumulato
  ventisette giorni di ore: il foglio diceva 667 ore. Ora non entra nei
  totali e viene segnalata a parte.
- Ferie, permessi e malattia non mostrano ore nel foglio esportato,
  come gia' nelle Statistiche: due schermate della stessa app non
  possono dire cose diverse sullo stesso giorno.
- Niente libreria di grafici per le statistiche: le barre sono
  disegnate a mano con dei Box. Servono barre piene e ben contrastate,
  leggibili al sole col telefono in mano, e tirarsi dentro una
  dipendenza grafica per questo sarebbe sproporzionato.
- Iniezione manuale invece di Hilt: un solo modulo, evita un secondo
  processore di annotazioni oltre a Room. Una migrazione futura
  toccherebbe solo AppContainer e le factory.
- minSdk 26 per avere java.time nativo senza desugaring.
- Colore dinamico di Android 12+ disattivato: serve contrasto
  prevedibile sotto il sole.
- LocationManager di sistema invece del FusedLocationProvider:
  nessuna dipendenza dai servizi Google, funziona anche sui telefoni
  che non li hanno. Per riconoscere un cantiere entro cento metri
  basta. Se servira' piu' precisione si sostituisce solo
  AndroidLocationProvider.
- Il GPS non seleziona MAI il cantiere da solo. Propone sempre, e
  l'utente conferma con un tocco. Un fix impreciso non deve falsare
  il diario.
- Permessi di posizione solo in primo piano, nessun tracciamento
  in background.
- I sensori non hanno una fase dedicata: sono inglobati nella Fase 6
  perche' il riconoscimento del cantiere e' la loro applicazione utile.

Foto: prima in galleria, poi nell'app.
Scelta dell'utente, e ha una buona ragione: le foto sopravvivono alla
disinstallazione e al cambio di telefono, e si condividono con le app
normali. Si usa MediaStore, che da Android 10 non richiede alcun permesso
per le immagini create dall'app stessa; sotto quella versione serve
WRITE_EXTERNAL_STORAGE, dichiarato con maxSdkVersion 28. Durante lo
scatto la voce resta IS_PENDING, altrimenti in galleria comparirebbe per
un istante un file vuoto; se l'utente annulla, la voce viene rimossa.
Non serve il permesso CAMERA, perche' si usa l'app fotocamera di sistema
tramite intent e non l'anteprima dentro l'app.
Conseguenza da tenere presente: le foto di case di clienti finiscono nel
rullino e nei backup automatici di Google Foto. Se dovesse dare fastidio,
va previsto un interruttore nelle impostazioni.

Promemoria: AlarmManager e non WorkManager.
Qui l'orario conta davvero, il foglio va consegnato appena si arriva in
sede. WorkManager e' pensato per lavori differibili e potrebbe far
scattare l'avviso a cose fatte. Si usa setWindow con dieci minuti di
tolleranza invece di una sveglia esatta: quest'ultima da Android 12
richiederebbe SCHEDULE_EXACT_ALARM, permesso che il Play Store concede
solo ad allarmi e calendari. La sveglia e' singola e viene riarmata a
ogni scatto: quelle ripetute di sistema vengono spostate liberamente dal
risparmio energetico.

Comunicazioni: perche' non e' automatico.
WhatsApp non espone nessuna API per leggere i messaggi; gli unici modi
sarebbero leggere le notifiche o usare i servizi di accessibilita', cioe'
le tecniche degli spyware, che il Play Store rifiuta e che darebbero
comunque solo l'anteprima. Il registro chiamate e' riservato all'app
impostata come telefono predefinito. La registrazione audio delle chiamate
e' bloccata dal sistema da Android 10. La condivisione di sistema e' quindi
l'unica strada pulita, e in cambio da' il contenuto per intero.
Il permesso rubrica serve solo quando l'utente preme "scegli contatto":
nessuna lettura automatica.

### Perche' gli importi stanno in centesimi interi
Con i numeri in virgola mobile 0.1 + 0.2 non fa 0.3. Su un rifornimento
non si nota, su duecento rifornimenti il totale di fine anno non torna con
la somma degli scontrini, e non si sa piu' dove guardare. Tutti gli
importi sono Long in centesimi, e la virgola compare solo quando si
scrive a schermo o su un foglio.

### Perche' il consumo si misura solo da pieno a pieno
Il consumo vero e' litri diviso chilometri fatti con quei litri. Se il
serbatoio a fine tratto e' a meta' non si sa quanto ce n'era prima, e
qualsiasi numero uscirebbe inventato. Quindi serve un pieno all'inizio e
un pieno alla fine, tutti e due con il contachilometri segnato. I
rabbocchi in mezzo si sommano ai litri e non spezzano il tratto. Il primo
pieno di tutti non produce nessun consumo: si sa quanto e' entrato, non da
dove si partiva.

### Perche' il contachilometri si legge come massimo e non come ultimo
Il contachilometri non torna indietro, quindi il numero piu' alto e' per
forza quello arrivato dopo. Prendere invece l'ultimo per data vorrebbe
dire farsi ingannare da una data scritta storta - capita, si registra il
rifornimento la sera dopo - e ritrovarsi il contatore che cala.

### Perche' i chilometri della giornata stanno sulla giornata
Sono un dato della giornata: si segnano insieme agli orari e servono nel
conto delle trasferte. Una tabella a parte avrebbe voluto dire tenere
d'accordo due cose che parlano dello stesso giorno. Null e zero restano
diversi: null e' "non l'ho segnato", zero e' "non mi sono mosso".

### Perche' i chilometri delle giornate e quelli del contatore restano separati
Quasi mai coincidono: uno e' quello che uno si e' segnato, l'altro e'
quello che dice lo strumento. Mescolarli darebbe un numero che non e' ne'
uno ne' l'altro. Nei totali si preferisce il contatore, che e' misurato,
e si ripiega sulle giornate quando il contatore non c'e'.

### Perche' il mezzo e' una tabella e non un campo
L'auto aziendale ogni tanto la cambiano. Con i rifornimenti attaccati solo
alla giornata, cambiando furgone i consumi si mescolerebbero e la media
verrebbe fuori senza senso. Il mezzo vecchio resta, spento, con tutti i
suoi dati.

## 8. Problemi noti
- Il build Gradle completo non e' mai stato eseguito: nel container di
  sviluppo manca l'SDK Android. Verificato con il compilatore Kotlin:
  il layer domain compila senza errori e le asserzioni passano.
  Gli errori residui sui file che usano androidx sono soltanto
  riferimenti non risolti, attesi fuori da Android Studio.
- Una sola giornata per data: ferie e lavoro nello stesso giorno
  non convivono ancora.
- Icona dell'app provvisoria.
- Nessun avviso prima di mezzanotte se si dimentica TERMINA GIORNATA.
  C'e' solo il recupero il giorno dopo tramite banner.

Le foto di cantiere finiscono nella galleria del telefono e quindi nei
backup automatici delle app di fotografie: e' voluto, ma va ricordato.

Le comunicazioni sono escluse dall'esportazione di partenza: nel
documento che va in sede non devono finire i messaggi di altre persone.
L'interruttore c'e', ma va acceso a mano e con cognizione.

Il logo aziendale MBR e' nel rapportino PDF, in alto a sinistra come sul
modulo cartaceo. Non e' stato fornito a parte: e' stato ricavato dal PDF
del modulo, ripulito dall'antialiasing riportandolo a due colori pieni e
ingrandito a blocchi netti, perche' l'originale incorporato era di 136 per
53 pixel e stampato a quattro centimetri sarebbe uscito seghettato. Sta in
res/drawable/logo_mbr.png. Se un giorno arriva il file vero dell'azienda,
si sostituisce quel PNG e non cambia altro. Se la risorsa mancasse, il
foglio esce lo stesso senza logo invece di non uscire affatto: il
rapportino serve il lunedi' mattina.

La colonna COD / DOCUMENTI del rapportino esce vuota: nell'app non c'e'
un dato che le corrisponda e si compila a mano, come sul cartaceo. Se
serve riempirla in automatico, va aggiunto un campo alla giornata.

Il ripristino e' stato provato riga per riga fuori dal telefono, ma non
e' mai stato eseguito su un database Room vero: la prima volta conviene
farlo su un telefono senza dati importanti, o subito dopo aver salvato
un backup di quello che c'e'.

La registrazione audio non e' mai stata provata su un telefono: il
codice gestisce microfono occupato, permesso negato e registrazioni
troppo brevi, ma la prima prova va fatta con calma.

Il rapportino PDF e' stato verificato nella geometria (sta in pagina, le
colonne rispettano le proporzioni del modulo, la colonna H non sborda)
ma non e' mai stato stampato davvero: la prima stampa va guardata con
attenzione.

- Il mezzo aziendale non e' mai stato provato su un telefono: i conti sono
  verificati, l'inserimento no.
- L'avviso delle scadenze arriva insieme al promemoria settimanale, quindi
  una volta a settimana. Per un tagliando basta, ma se una scadenza cade
  di mercoledi' l'avviso e' arrivato il lunedi' prima.
- La data nei moduli del mezzo si scrive a mano nel formato gg/mm/aaaa:
  non c'e' il calendario a comparsa. Parte gia' compilata con oggi.
- Un mezzo cancellato porta via anche i suoi rifornimenti e interventi:
  e' voluto, ma non si torna indietro.

## 9. Test
- WorkTimeCalculatorTest: calcolo di lordo, netto e straordinario,
  incluso il caso 9h30 lordo meno 1h15 di pause = 8h15 netto.
- SiteLocationMatcherTest: 11 test su coordinate reali di Milano.
  Distanze verificate, esito certo, esito ambiguo ordinato per
  distanza, fuori raggio, precisione che allarga il raggio, cantieri
  senza coordinate ignorati, posizione non valida, deriva GPS,
  chilometri su percorso reale.
  Tutte le asserzioni eseguite e superate: 12 su 12.
- PeriodSummarizerTest: totali di periodo. Periodo vuoto, somma del
  netto su piu' giornate, pause sottratte, straordinario sommato solo
  dove eccede, ferie e malattia contate a parte e non come giorni
  lavorati, giornata aperta che porta tempo ma non conteggio, giornata
  mai iniziata ignorata, media calcolata solo sui giorni lavorati.
- DurationFormatTest: formattazione delle durate, comprese quelle
  oltre le 24 ore, quelle negative azzerate e quelle con segno.
  Fase 7: tutte le asserzioni eseguite e superate: 26 su 26.
- SharedTextParserTest: riconoscimento del testo condiviso. Mittente nel
  formato con parentesi quadre e in quello con trattino, righe vuote
  saltate, testo normale che non produce falsi mittenti, due punti dentro
  una frase che non ingannano il parser, testo vuoto, riconoscimento di
  WhatsApp, WhatsApp Business, Gmail e Outlook, pacchetto sconosciuto,
  contenuto su piu' righe conservato integro, oggetto della mail, anteprima
  troncata, valori predefiniti degli enum.
  Fase 8: tutte le asserzioni eseguite e superate: 28 su 28.
- WeeklyReportBuilderTest: sette righe sempre presenti, somma di una
  settimana da cinque giorni, giornata aperta che blocca la consegna,
  ferie senza ore ma visibili, lavorazioni unite con la quantita',
  ripiego sulla descrizione della giornata, filtro degli eventi da
  segnalare, data infrasettimanale riportata al lunedi'.
- ReminderScheduleCalculatorTest: prossimo scatto da meta' settimana,
  lunedi' prima e dopo l'orario, orario esatto che non si ripete,
  settimana riepilogata sempre quella conclusa, giorno e ora
  modificabili, cambio d'anno.
  Rapportino: tutte le asserzioni eseguite e superate: 42 su 42.
- PhotoNamingTest: nome del file con data e ora, suffisso per due scatti
  nello stesso secondo, fattore di riduzione sempre potenza di due,
  immagini gia' piccole non ridotte ne' ingrandite, dimensioni non valide
  che non fanno esplodere il calcolo, proporzioni mantenute, verticale e
  panoramica estrema, orientamento EXIF tradotto in gradi, orientamento
  sconosciuto che non ruota.
  Fase 9: tutte le asserzioni eseguite e superate: 24 su 24.
- StatisticsCalculatorTest: statistiche di periodo. Confini di
  settimana, mese e anno, febbraio bisestile del 2028, periodo
  precedente, marzo 2026 che tocca sei settimane (dal lunedi' 23
  febbraio alla domenica 5 aprile), sette barre nella settimana e
  dodici nell'anno, ripartizione a uno e due cantieri ordinata per
  ore, giornata senza cantiere che finisce sotto "Senza cantiere"
  invece di sparire, confronto positivo e negativo col periodo
  precedente, assenza di confronto quando manca il periodo prima,
  lavorazioni contate e ordinate, giornate con eventi e questioni
  aperte, ferie che non fanno ore, periodo vuoto che non divide per
  zero.
  Fase 10: 18 test JUnit eseguiti e superati, 18 su 18, piu' le 57
  asserzioni del banco di prova interno.
- ExportBuilderTest: esportazione. Confini dei periodi, "tutto" che
  parte dalla prima giornata registrata e che ripiega sull'anno quando
  non ce ne sono, nomi file ordinabili, etichette di mese e anno
  interi, comunicazioni escluse di default e presenti quando si
  accendono, giornata mai chiusa che non gonfia i totali, ferie senza
  ore ma contate nel riepilogo, BOM e punto e virgola del CSV, celle
  con punto e virgola e virgolette, lettere di colonna oltre la Z,
  caratteri riservati XML, limiti dei nomi scheda di Excel, xlsx che
  e' davvero un archivio zip.
  Fase 11: 19 test JUnit eseguiti e superati, 19 su 19, piu' le 57
  asserzioni del banco di prova e le 11 sul formato ore decimali.
  Il file xlsx generato e' stato riaperto con un lettore esterno e
  letto riga per riga.
- BackupSerializerTest: backup, ripristino e formato JSON. Andata e
  ritorno del contenuto completo confrontato campo per campo, istanti
  in millisecondi senza perdita di precisione, virgolette e a capo
  dentro le note, valori nulli, manifest, file troncati, file di altre
  applicazioni, backup di versione futura, righe rovinate saltate e
  contate, archivio zip con foto e note vocali identiche byte per
  byte, percorsi malevoli dentro l'archivio.
  Fase 12: 17 test JUnit eseguiti e superati, 17 su 17, piu' le 36
  asserzioni sul JSON, le 34 sul backup e le 19 sull'archivio.
  Fase 13: 43 test JUnit nuovi (VehicleCalculatorTest,
  MaintenanceSchedulerTest, MoneyFormatTest). In tutto 97 test JUnit
  eseguiti e superati, 97 su 97.
  Runner della fase 13: 76 asserzioni sui conti del mezzo, 42 sugli
  importi digitati a mano, 44 sul backup del mezzo andata e ritorno
  compresa la lettura di un backup vecchio, 32 sui fogli
  dell'esportazione. Rieseguiti anche tutti i runner precedenti dopo le
  modifiche: JSON 36, backup 34, archivio 19, esportazione 58,
  statistiche 57, rapportino 29, tutti superati.
  Due verifiche hanno trovato errori veri durante la fase 13: il
  contachilometri preso come ultimo per data invece che come massimo, e
  i parametri posizionali dei test del backup sfasati dalle colonne
  nuove. Corretti tutti e due.

## 10. Extra rimandati, da non dimenticare
- Contapassi con il sensore del telefono
- Pulizia automatica dei testi dettati: da fare come programma a parte sul
  computer, che legge il backup, sistema le trascrizioni con un modello
  locale e rimette il file a posto. Non dentro l'app: sul telefono di
  lavoro un modello locale sarebbe troppo lento, e una chiave a pagamento
  e' stata esclusa.
- Xiaomi Mi Band 5 tramite Mi Fitness e Google Health Connect
  (la banda non espone dati a terzi in modo diretto)

## 11. Prossima fase
Il rapportino e' impaginato sul modulo cartaceo dell'azienda, logo
compreso. Resta aperto solo il contenuto della colonna COD / DOCUMENTI,
che oggi esce vuota da compilare a mano.

Tutte le fasi previste sono scritte.

Resta da preparare il workflow GitHub Actions e produrre l'APK, una
volta sola, con l'app completa.
