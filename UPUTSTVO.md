# RAV Asistent – glasovni asistent za Ainavi X6 (RAV4 XA50)

## A) Pravljenje APK-a na GitHub-u (jednom, ~10 min)
1. Na github.com klikni **+** (gore desno) → **New repository**.
2. Ime: `rav-asistent`, ostavi **Private**, uključi **Add a README file** → **Create repository**.
3. Klikni **Add file → Upload files**.
4. Raspakuj `RavAsistent.zip` na računaru, uđi u folder `RavAsistent`, označi SVE unutra
   (`.github`, `app`, i ostale fajlove; Ctrl+A) i prevuci ih na GitHub stranicu.
5. Dole klikni zeleno **Commit changes**.
6. Otvori karticu **Actions**. Pojaviće se „Napravi APK“ (žuti krug = radi, ~5 min).
   - Ako je zeleno ✅: klikni na nju → dole pod **Artifacts** klikni **RavAsistent-APK** → skine se zip sa `app-debug.apk`.
   - Ako je crveno ❌: klikni na nju, pa na crveni korak, i pošalji screenshot greške.
   - Ako se ništa ne pojavi u Actions: `.github` folder se nije uploadovao. Tada:
     **Add file → Create new file**, u ime upiši `.github/workflows/build.yml`,
     nalepi sadržaj tog fajla iz zipa i klikni **Commit changes**.

## B) Instalacija na uređaj
1. Raspakuj skinuti zip, `app-debug.apk` prebaci na USB.
2. USB u auto. Na uređaju otvori fajl menadžer i dodirni `app-debug.apk`.
3. Ako traži dozvolu za „nepoznate izvore“, dozvoli je za fajl menadžer, pa **Instaliraj**.

## C) Podešavanje u aplikaciji RAV Asistent
1. **Dozvoli mikrofon**
2. **Dozvoli prikaz preko drugih aplikacija** (pronađi RAV Asistent i uključi)
3. **Uključi asistenta**: pojaviće se plavo dugme sa mikrofonom (može da se prevuče gde hoćeš)
4. Upiši kućnu adresu i adresu posla → **Sačuvaj adrese**

Dodir na plavo dugme: ono pocrveni, ti kažeš komandu. Ikonica „Govori“ radi isto.
Neki uređaji dozvoljavaju da se dugme na volanu veže za aplikaciju, pa ga veži za „Govori“.

## Komande
- Vodi me kući / Idemo na posao / Vodi me do Kragujevca
- Proveri rutu ponovo / Najbrža ruta
- Najbliža pumpa / Nađi apoteku / Najbliži parking
- Pojačaj / Smanji / Pojačaj malo / Smanji dosta / Jačina 10 / Zvuk na pola
- Isključi zvuk / Vrati zvuk
- Pusti muziku / Pauza / Sledeća pesma / Prethodna
- Pusti <izvođač ili pesma>

## Kad nešto ne radi
Na dnu aplikacije, pod „Poslednje što sam čuo“, piše šta je asistent čuo i šta je uradio.
Pošalji to i dobićeš ispravku.
