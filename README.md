# Maxdis Assistant – Build APK via GitHub Actions
## Tanpa PC, Tanpa Android Studio – Gratis!

---

## Langkah 1 — Persiapan (HP/PC apa saja)

### Download gradle-wrapper.jar (WAJIB)
File ini tidak bisa disertakan langsung. Download manual:

👉 Buka link ini di browser:
```
https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar
```
Simpan file sebagai `gradle-wrapper.jar` — nanti diupload ke folder `gradle/wrapper/`

---

## Langkah 2 — Buat Repository GitHub

1. Buka **github.com** → Login / Daftar (gratis)
2. Klik tombol **"+"** → **"New repository"**
3. Isi nama repo: `maxdis-assistant`
4. Pilih: **Public** (agar Actions gratis unlimited)
5. Klik **"Create repository"**

---

## Langkah 3 — Upload File ke GitHub

Di halaman repo baru, klik **"uploading an existing file"** atau **"Add file → Upload files"**

Upload file-file berikut dengan struktur folder yang TEPAT:

```
📁 Upload semua ini ke GitHub:

.github/
  workflows/
    build-apk.yml          ← workflow otomatis build

app/
  build.gradle
  proguard-rules.pro
  src/
    main/
      AndroidManifest.xml
      assets/
        index.html         ← UI aplikasi
      java/com/alfamart/maxdis/
        MainActivity.java
      res/
        layout/
          activity_main.xml
        values/
          colors.xml
          strings.xml
          themes.xml
        mipmap-hdpi/
          ic_launcher.png
          ic_launcher_round.png
        mipmap-mdpi/       (isi semua mipmap sama)
        mipmap-xhdpi/
        mipmap-xxhdpi/
        mipmap-xxxhdpi/

gradle/
  wrapper/
    gradle-wrapper.jar     ← DOWNLOAD MANUAL (lihat Langkah 1)
    gradle-wrapper.properties

build.gradle
gradle.properties
gradlew                    ← PENTING: jangan lupa ini
gradlew.bat
settings.gradle
```

### Tips Upload di GitHub (via HP):
- GitHub web support upload folder via drag & drop
- Atau upload file satu per satu, pastikan path/struktur benar
- Setelah upload semua, klik **"Commit changes"**

---

## Langkah 4 — Jalankan Build

### Otomatis:
Setiap kali ada file yang di-commit/push → build otomatis jalan

### Manual:
1. Buka tab **"Actions"** di repo GitHub
2. Klik **"Build Maxdis APK"** di sidebar kiri
3. Klik tombol **"Run workflow"** → **"Run workflow"**
4. Tunggu ~3-5 menit (indikator kuning = sedang build)

---

## Langkah 5 — Download APK

1. Setelah build selesai (centang hijau ✅)
2. Klik nama workflow yang sudah selesai
3. Scroll ke bawah → bagian **"Artifacts"**
4. Klik **"MaxdisAssistant-APK"** → file ZIP terdownload
5. Ekstrak ZIP → dapat file **`app-debug.apk`**

---

## Langkah 6 — Install APK ke HP

1. Pindahkan `app-debug.apk` ke HP Android
2. Buka **Pengaturan → Keamanan → Izinkan sumber tidak dikenal**
   (atau saat buka APK akan muncul permintaan izin)
3. Buka file APK → **Install**
4. Buka app **"Maxdis Assistant"** 🎉

---

## Kompatibilitas
| Android | API | ✅ |
|---------|-----|---|
| Android 6.0 | API 23 | ✅ |
| Android 7–8 | API 24–27 | ✅ |
| Android 9–10 | API 28–29 | ✅ |
| Android 11–12 | API 30–32 | ✅ |
| Android 13–14 | API 33–34 | ✅ |
| Android 15–17 | API 35–37 | ✅ |

---

## Troubleshooting

**Build gagal / merah ❌**
→ Klik nama workflow → lihat log error di step mana yang gagal
→ Paling sering: `gradle-wrapper.jar` tidak ada atau struktur folder salah

**APK tidak mau install**
→ Pastikan aktifkan "Install dari sumber tidak dikenal"
→ Coba restart HP lalu install ulang

**Alarm tidak bunyi**
→ Pastikan volume Alarm HP tidak di-mute
→ Cek izin app di Pengaturan → App → Maxdis Assistant
