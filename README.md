#  Tarifni semafor — Optimizirajte porabo električne energije

**Tarifni semafor** je vaš osebni žepni pripomoček za enostavno navigacijo skozi novo metodologijo obračuna omrežnine v Sloveniji. Aplikacija deluje kot vizualni koledar časovnih blokov, ki vam pomaga ujeti pravi trenutek za vklop večjih porabnikov ter s tem poskrbi za učinkovitejšo rabo energije in nižji račun.

---

## TESTNA verzija, dostopna na Google Play https://play.google.com/apps/testing/si.semafor.app, preden si jo lahko preneseš, pa se pridruži Google groups https://groups.google.com/g/bsky_testira. 
Vabljen k testiranju.

---

##  Ključne funkcionalnosti

- **Sprotni vpogled** — Takojšen pregled nad trenutno veljavnim časovnim blokom za obračun omrežnine.
- **Pametno signaliziranje** — Jasni vizualni semaforski namigi, ki napovedujejo prihod naslednjega časovnih bloka.
- **Pripomočki za začetni zaslon** — Dva priročna gradnika (widgeta) za takojšen dostop do informacij neposredno z namizja telefona.
- **Primerjalnik stroškov** — Integriran dostop do kalkulatorja za objektivno primerjavo stroškov oskrbe z elektriko in zemeljskim plinom.
- **Izobraževalni nasveti** — Kompaktni namigi za ozaveščeno rabo energije in optimalno upravljanje domačih naprav.

---

##  Tehnične specifikacije & Tehnologije

Aplikacija je zgrajena z uporabo sodobnih smernic za razvoj mobilnih aplikacij:

| Komponenta | Tehnologija / Vir |
| :--- | :--- |
| **Jezik** | Kotlin 🚀 |
| **Platforma** | Android SDK |
| **Omrežna opravila** | REST-API (Integracija javnih energetskih podatkov) |
| **Lokalna hramba** | Šifriran zasebni prostor aplikacije (Android Storage) |
| **Uradni viri podatkov** | Agencija za energijo ([uro.si](https://uro.si)), SODO, MojElektro |

---

## 🔒 Popolna zasebnost (No-Log & Brez registracije)

Zasebnost uporabnikov je na prvem mestu. Aplikacija deluje popolnoma anonimno:
- **Brez registracije** ali prijave.
- **Ne zbira** nikakršnih osebnih podatkov. Neobvezna povezava Moj Elektro jih obdeluje izključno v telefonu (glej razdelek Moj Elektro).
- Uporablja izključno javno dostopne podatke (REST-API) Agencije za energijo.

---

## Neobvezna povezava Moj Elektro

V Nastavitvah lahko uporabnik poveže svoje merilno mesto z žetonom API portala Moj Elektro (samo branje) in EIMM. Aplikacija nato na zaslonu **Moja poraba** pokaže:

- dogovorjeno moč za trenutni časovni blok in najvišjo 15-minutno moč v tem bloku,
- 15-minutne konice zadnjega dne s podatki (opozorilo nad 90 % in nad dogovorjeno močjo),
- porabo po dnevih in časovnih blokih (7 ali 30 dni),
- prejeto in oddano energijo (samo, če merilno mesto oddaja energijo).

Podatki niso v realnem času: Moj Elektro jih objavi z zamikom, običajno približno en dan.

**Zasebnost:** žeton, EIMM in meritve so shranjeni samo v telefonu, šifrirani s ključem v Android Keystore (AES-256-GCM), in niso del varnostnih kopij. Aplikacija jih pošilja samo uradnemu API-ju `api.informatika.si`, nikoli razvijalcu ali tretjim. Prekinitev povezave izbriše žeton, EIMM in shranjene meritve.

**Tehnično** (paket `si.semafor.app.mojelektro`):

| Del | Datoteka |
| :--- | :--- |
| HTTP odjemalec (HttpURLConnection, glava `X-API-TOKEN`, brez preusmeritev) | `MojElektroClient.kt` |
| Razčlenjevanje JSON (org.json) po uradni specifikaciji OpenAPI | `MojElektroParser.kt` |
| Šifriranje (Keystore) in shramba povezave | `MojElektroCrypto.kt`, `MojElektroCredentialStore.kt` |
| Repozitorij in preverjanje povezave | `MojElektroRepository.kt` |
| Šifriran predpomnilnik meritev (6 h), urnik blokov z uro.si | `UsageCache.kt`, `BlockSchedules.kt` |
| Analiza (čisti Kotlin, enotni testi) | `UsageAnalysis.kt` |
| Stanje zaslonov | `MojElektroViewModel.kt`, `UsageViewModel.kt`, `UsageRepository.kt` |
| Grafi (Compose Canvas, brez knjižnice) | `ui/compose/charts/UsageCharts.kt` |

Kode registrov (A+, A-, A+_T0, A-_T0) se preberejo iz uradnega kataloga `GET /reading-type`. Časovni blok vsake ure preteklih dni se pridobi z uradnega API-ja uro.si; pravila blokov niso zapisana v aplikaciji. Ali časovna oznaka 15-minutne meritve pomeni začetek ali konec intervala, aplikacija ugotovi iz podatkov (primerjava z dnevnim stanjem števca).

---

## ⚠️ Pravno obvestilo (Disclaimer)

**Tarifni semafor je neodvisen in NEURADEN projekt.** 
Aplikacija ne predstavlja nobenega državnega organa ali operaterja ter ni uradni izdelek Agencije za energijo, družbe SODO d.o.o. ali platforme MojElektro. Podatki so informativne narave, pravno zavezujoče informacije pa objavljajo izključno uradni izvirni viri.
