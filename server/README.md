# Strežniški del: ceniki za aplikacijo Tarifni semafor

Ta mapa vsebuje vse, kar prenese aplikacija s spletnega mesta `tarifnisemafor.si`. To je ena javna statična datoteka s cenami: `https://tarifnisemafor.si/api/ceniki.json`.

## Vsebina mape

```
server/
  public_html/api/ceniki.json     javna datoteka, ki jo prenese aplikacija
  public_html/api/.htaccess       iz mape se streže samo ceniki.json, brez piškotkov
  private/ceniki_vir.json         VIR: cene urejaš samo tukaj
  private/update_prices.php       skripta za cron (enkrat na dan)
  private/watch.json              uradne strani, ki jih skripta opazuje
  private/config.php.example      nastavitve (e-naslov za obvestila)
  private/.htaccess               mapa private ni dosegljiva s spleta
```

Kopija `ceniki.json` je vgrajena tudi v aplikacijo (`app/src/main/assets/ceniki_fallback.json`). Tako aplikacija deluje že ob prvem zagonu brez interneta. Ob novi verziji aplikacije jo prepiši z zadnjim objavljenim `ceniki.json`.

## Trenutna namestitev (18. 11. 2026, Spaceship, cPanel)

- Koren spletnega mesta je `~/tarifnisemafor.si` (ne `public_html`). Javna datoteka: `~/tarifnisemafor.si/api/ceniki.json` in `.htaccess`.
- Zasebna mapa: `~/tarifni-private/` (zunaj javne mape) s skripto, `ceniki_vir.json`, `watch.json`, `config.php` (obvestila na info@primush.online; `public_file` kaže na `../tarifnisemafor.si/api/ceniki.json`), `state.json`.
- Cron: vsak dan ob 6.15 `/usr/local/bin/php /home/puaumxjcoi/tarifni-private/update_prices.php > /dev/null 2>&1` (PHP 8.4 CLI).
- Dnevniki: arhiviranje dnevnikov dostopov je izklopljeno (Raw Access). Surovi dnevnik strežnika hrani ponudnik ~24 ur (podpora Spaceship: na skupnem gostovanju ga ni mogoče izklopiti ali anonimizirati, AWStats ostaja vklopljen).
- Cron vsakih 6 ur (`40 */6 * * *`): `purge_stats.php` izbriše podatkovne datoteke AWStats za tarifnisemafor.si (notranji imeni gostovanja `28afe26b…` in `e1d07f40…` v `~/tmp/awstats` in `~/tmp/awstats/ssl`). Če gostovanje imena spremeni, popravi seznam v skripti.
- Preverjeno: `ceniki.json` vrne 200, `application/json`, brez piškotkov; vse drugo v `/api/` vrne 403.

## Namestitev na gostovanje

1. Vsebino `public_html/api/` naloži v mapo `api` v korenu spletnega mesta (tam, kjer je WordPress). WordPress statičnih datotek, ki obstajajo, ne preusmerja, zato se `ceniki.json` streže neposredno, brez WordPressa in brez Google Analytics.
2. Mapo `private/` naloži **izven** javne mape, na primer `/home/<uporabnik>/tarifni-private/`.
   - Če gostovanje tega ne dovoli, jo daj v javno mapo pod nenavadnim imenom. Datoteka `.htaccess` v njej prepove ves dostop.
   - V tem primeru v `config.php` popravi pot `public_file`.
3. `config.php.example` kopiraj v `config.php` in vpiši e-naslov za obvestila.
4. V nadzorni plošči gostovanja (Cron Jobs) nastavi dnevno opravilo, na primer ob 6.00:
   ```
   php /home/<uporabnik>/tarifni-private/update_prices.php
   ```
5. Preveri v brskalniku, da se `https://tarifnisemafor.si/api/ceniki.json` odpre kot JSON.

## Zasebnost: dnevnik dostopov (pogoj 3)

Aplikacija pošlje samo zahtevo GET za to datoteko, brez parametrov, piškotkov in identifikatorjev. Kot User-Agent pošlje samo »TarifniSemafor«, brez modela telefona.

Spletni strežnik pa vsako zahtevo sam zapiše v dnevnik dostopov, skupaj z IP-naslovom. Dnevnika ni mogoče izklopiti v `.htaccess`. Uredi to v nadzorni plošči gostovanja:

- **cPanel:** v »Raw Access« izklopi »Archive logs«. Če gostitelj to omogoča, izklopi tudi beleženje dostopov ali nastavi njihovo čim krajšo hrambo.
- **Druge plošče:** poišči nastavitev »Access log« ali »Statistika obiskov« in jo izklopi za domeno ali vsaj za mapo `/api/`.
- Če nastavitve ni, prosi gostitelja, naj za `/api/` izklopi beleženje ali anonimizira IP-naslove (zadnji oktet zamenja z 0). Odgovor gostitelja shrani.

Pravilnik o zasebnosti mora ta prenos opisati (pogoj 4). Besedilo je dodano v izvor pravilnika (`../TarifniSemafor spletno mesto/pravilnik-o-zasebnosti.html`, poglavji 3.1, 3.2 in 3.4, SL in EN). Pravilnik objavi šele, ko je dnevnik dostopov urejen, ker pravilnik to trdi.

## Kako posodobiš cene

Skripta cen **ne prebira sama s spletnih strani**. Strani dobaviteljev nimajo stalne zgradbe, napačna številka pa bi bila hujša od zastarele. Delo poteka takole:

1. Skripta vsak dan preveri strani v `watch.json`. Ko se katera spremeni (na primer nov PDF cenika), dobiš e-pošto.
2. Ko se bliža konec veljavnosti katerega cenika (privzeto 21 dni prej), dobiš opomnik. Ko cenik poteče, dobiš še enega.
3. Cene prepišeš iz uradnega cenika v `private/ceniki_vir.json`.
4. Ob naslednjem zagonu (ali če skripto zaženeš ročno) skripta datoteko preveri po enakih pravilih kot aplikacija.
   - Če je datoteka veljavna, jo objavi z novim `updatedAt`. Prejšnjo shrani kot `ceniki.json.prev`.
   - Če ni veljavna, **ne objavi ničesar**. Spletna datoteka ostane nespremenjena, ti pa dobiš e-pošto s seznamom napak.

Aplikacija novo datoteko prenese takrat, ko je to potrebno:
- ob prvem zagonu,
- nekaj tednov pred koncem veljavnosti cenika in po njem enkrat na dan, dokler ne pride nov,
- sicer najpozneje vsakih 60 dni,
- ali ko uporabnik pritisne »Preveri zdaj«.

Uporabnik lahko vklopi obvestilo o spremembah cen.

## Zgradba `ceniki_vir.json`

Vsi zneski so **brez DDV**, v evrih. Vsaka postavka ima `validFrom`, neobvezni `validTo` in `sourceUrl` (samo `https://`).

| Polje | Pomen |
| :--- | :--- |
| `schemaVersion` | vedno 1 |
| `updatedAt` | zapiše ga skripta |
| `validFrom`, `validTo` | obdobje celotne datoteke (aplikacija po `validTo` začne preverjati vsak dan) |
| `tariffTimes` | `vtStartHour`, `vtEndHour` (VT ob delavnikih), `mtOnWeekendsAndHolidays` |
| `taxes.vatRate` | stopnja DDV (0.22) |
| `taxes.ureContribution` | prispevek za energetsko učinkovitost, `EUR/kWh` |
| `taxes.borzenContribution` | prispevek za delovanje operaterja trga, `EUR/kWh` |
| `taxes.exciseDuty` | trošarina (I. stopnja), `EUR/kWh` (1,53 EUR/MWh = 0.00153) |
| `taxes.oveSpteContribution` | prispevek OVE in SPTE: `EUR/kW/month` z `powerBlockHighSeason` in `powerBlockLowSeason`, ali `EUR/kWh` |
| `network` | ceniki omrežnine SODO, bloki 1-5: `powerEurPerKwMonth`, `energyEurPerKwh` |
| `suppliers` | dobavitelji, vsak s `packages`: `pricesWithoutVat` (`ET` in/ali `VT` + `MT`), `monthlyFeeWithoutVat`, neobvezno `monthlyExtrasWithoutVat` (ugodnosti in storitve na mesec, popust negativen, npr. -0.18) |

Vsaka dajatev je seznam obdobij. Ko se vrednost spremeni, staremu obdobju dodaj `validTo` in dodaj novo obdobje z novim `validFrom`. Tako aplikacija pravilno računa tudi za pretekle in prihodnje datume.

Primer paketa:

```json
{
  "id": "gen-i",
  "name": "GEN-I",
  "packages": [
    {
      "id": "gen-i-redni",
      "name": "Redni cenik",
      "validFrom": "2026-12-01",
      "validTo": null,
      "sourceUrl": "https://gen-i.si/...",
      "pricesWithoutVat": { "ET": 0.0, "VT": 0.0, "MT": 0.0 },
      "monthlyFeeWithoutVat": null
    }
  ]
}
```

Številke v primeru so ničle in **niso** cene. Prepiši jih iz uradnega cenika.

## Kaj je preverjeno v začetni datoteki (18. 11. 2026)

| Postavka | Vrednost | Vir | Opomba |
| :--- | :--- | :--- | :--- |
| Omrežnina 1.-5. blok | iz cenika SODO 1. 11. 2026 | sodo.si | enako kot v aplikaciji 2.2.3 |
| Prispevek URE | 0,0008 EUR/kWh | energetika-portal.si | od 2017 |
| Trošarina | 0,00153 EUR/kWh | Uredba o določitvi zneska trošarine, čistopis 16. 6. 2026 | sprememba z dne 22. 9. 2026 ni bila preverjena |
| Prispevek OVE in SPTE | 0,77562 EUR/kW na mesec | energetika-portal.si | osnova: blok 1 (višja sezona), blok 2 (nižja sezona), po STA/Žurnal24. Za 2027 je v javni obravnavi 0,0037 EUR/kWh |
| Operater trga | 0,00013 EUR/kWh | dokument Borzen | datum veljavnosti ni naveden; preveri v veljavni uredbi |
| VT/MT | VT ob delavnikih 6-22, MT ponoči, ob vikendih in praznikih | cenik GEN-I | |
| Dobavitelji | prazno | | cene vnesi iz uradnih cenikov |
