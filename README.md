# Aplikasi Kasir ARN

Aplikasi kasir Android (Flutter) dengan database lokal untuk manajemen bahan baku berbasis takaran. Seluruh data tersimpan di memori ponsel dan aplikasi berjalan sepenuhnya *offline*.

## Status

Seluruh fitur MVP sudah ditulis, tetapi **belum pernah dikompilasi atau dijalankan** karena ditulis tanpa SDK. Baca [docs/STATUS.md](docs/STATUS.md) sebelum memakainya: berisi langkah pertama, deviasi dari PRD, dan risiko yang tersisa. Spesifikasi lengkap ada di [docs/PRD.md](docs/PRD.md).

## Memulai

Prasyarat: Flutter stabil 3.35 atau lebih baru.

```
dart tool/bootstrap.dart   # membuat android/, menambal Gradle, pub get, generator, ikon, splash
flutter gen-l10n
flutter analyze
flutter test
flutter run
```

## Ringkasan Fitur

* *Splash screen* logo ARN, lalu Halaman Utama (*top bar*, kategori menu, daftar menu).
* Burger menu: notifikasi, tema, bahasa, penyetelan menu, bahan baku, info pemasukan, info pengeluaran, cadangan dan pulihkan, tentang aplikasi.
* Resep per menu dengan takaran; stok bahan berkurang otomatis saat penjualan.
* Perhitungan HPP dan laba kotor.
* *Backup* dan *restore* data lewat berkas `.arnbak`.

## Stack

Flutter (Dart, Material 3), Drift/SQLite, Riverpod, go_router, minSdk 26. Android saja pada v1.

## Struktur Repo

```
CLAUDE.md                     Panduan dan aturan domain untuk Claude Code
docs/PRD.md                   Product Requirements Document
docs/TEST_CASES_TAKARAN.md    Kasus uji logika takaran, stok, dan biaya
docs/BACKLOG.md               Daftar pekerjaan berurutan (siap jadi GitHub Issues)
docs/STATUS.md                Keadaan implementasi, deviasi, dan risiko

lib/main.dart                 Titik masuk
lib/app/                      Bootstrap, provider, router, tema, layanan lintas fitur
lib/domain/                   Dart murni: aturan takaran, biaya, penjualan, laporan
lib/data/                     Drift, repositori, pengaturan, gambar, cadangan, notifikasi
lib/ui/                       Layar per fitur
lib/l10n/                     ARB Indonesia dan Inggris
test/                         Uji unit dan data (domain, Drift dalam memori, cadangan)
tool/bootstrap.dart           Penyiapan sekali setelah clone
```
