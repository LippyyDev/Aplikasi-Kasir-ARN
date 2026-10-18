# Aplikasi Kasir ARN

Aplikasi kasir Android (Flutter) dengan database lokal untuk manajemen bahan baku berbasis takaran. Seluruh data tersimpan di memori ponsel dan aplikasi berjalan sepenuhnya *offline*.

## Status

Tahap perencanaan. Spesifikasi lengkap ada di [docs/PRD.md](docs/PRD.md).

## Ringkasan Fitur

* *Splash screen* logo ARN, lalu Halaman Utama (*top bar*, kategori menu, daftar menu).
* Burger menu: notifikasi, tema, bahasa, penyetelan menu, bahan baku, info pemasukan, info pengeluaran, cadangan dan pulihkan, tentang aplikasi.
* Resep per menu dengan takaran; stok bahan berkurang otomatis saat penjualan.
* Perhitungan HPP dan laba kotor.
* *Backup* dan *restore* data lewat berkas `.arnbak`.

## Stack (Usulan)

Flutter (Dart, Material 3), Drift/SQLite, Riverpod, go_router, `workmanager`, minSdk 26. Android saja pada v1.

## Struktur Repo

```
CLAUDE.md                     Panduan dan aturan domain untuk Claude Code
docs/PRD.md                   Product Requirements Document
docs/TEST_CASES_TAKARAN.md    Kasus uji logika takaran, stok, dan biaya
docs/BACKLOG.md               Daftar pekerjaan berurutan (siap jadi GitHub Issues)
```
