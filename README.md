# 8K TV – Aplikacion IPTV për Android TV

Dizajn në stilin e Nimi TV. Lidhet me Xtream Codes API dhe provon automatikisht të gjithë serverët:
8k.cms-only.ru, apps.dakord.xyz, not90904.matrix.dakord.xyz, not90904.matrix.perty.xyz,
tv.business-cloud-8.ru, pro.business-cloud-8.ru, pro.dakord.xyz, pro.perty.xyz, cf.dakord.xyz, cf.perty.xyz.
Përdoret i pari që përgjigjet; nëse ai bie më vonë, aplikacioni kalon vetë te një tjetër.
Përdoruesi shkruan vetëm **Username** dhe **Password** – hapen Live TV, Filmat dhe Serialet.

## Funksionet
- Hyrje me username/password, ruhet automatikisht (s'ka nevojë të hysh sërish)
- **Live TV**: kategoritë majtas, kanalet me logo djathtas
- Në player: ▲/▼ ose CH+/CH− ndërron kanalin, numrat 0-9 për të shkuar te kanali, OK tregon emrin
- Nëse kanali nuk hapet në HLS (.m3u8), provon automatikisht .ts
- **Filma**: postera sipas kategorive, player me kontroll (pauzë, përpara/mbrapa)
- **Seriale**: sezonet dhe episodet, episodi tjetër luhet automatikisht
- **Llogaria**: statusi, data e skadimit, lidhjet, butoni Dil

## Ndryshimi i serverëve
Te `app/src/main/java/com/eightk/tv/data/Xtream.kt`, lista `SERVERS` (shto port nëse duhet, p.sh. `http://pro.dakord.xyz:8080`).

## Si ta ndërtosh APK-në

### Mënyra 1 – GitHub (pa instaluar asgjë)
1. Krijo një repo të re në github.com dhe ngarko gjithë këtë dosje.
2. Shko te **Actions** → "Build APK" (fillon vetë pas ngarkimit).
3. Pas ~5 min, shkarko **8KTV-apk** nga faqja e build-it → brenda është `app-release.apk`.

### Mënyra 2 – Android Studio
1. Hap dosjen me Android Studio → prit Gradle Sync.
2. **Build → Build APK(s)**. APK del te `app/build/outputs/apk/`.

## Instalimi në Android TV
- Ngarko APK-në në Google Drive/link dhe hap me app-in **Downloader** në TV, ose
- Me ADB: `adb connect IP_E_TV:5555` dhe `adb install app-release.apk`
- Lejo "Instalo nga burime të panjohura" për Downloader.
