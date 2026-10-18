# Aplikasi Kasir ARN

Aplikasi kasir Android (Kotlin) dengan database lokal untuk manajemen bahan baku berbasis takaran. Seluruh data tersimpan di memori ponsel dan aplikasi berjalan sepenuhnya *offline*.

## Status

Tahap perencanaan. Spesifikasi lengkap ada di [docs/PRD.md](docs/PRD.md).

## Ringkasan Fitur

* *Splash screen* logo ARN, lalu Halaman Utama (*top bar*, kategori menu, daftar menu).
* Burger menu: notifikasi, tema, bahasa, penyetelan menu, bahan baku, info pemasukan, info pengeluaran, cadangan dan pulihkan, tentang aplikasi.
* Resep per menu dengan takaran; stok bahan berkurang otomatis saat penjualan.
* Perhitungan HPP dan laba kotor.
* *Backup* dan *restore* data lewat berkas `.arnbak`.

## Stack (Usulan)

Kotlin, Jetpack Compose (Material 3), Room, DataStore, Hilt, Coroutines/Flow, WorkManager, minSdk 26.

## Struktur Repo

```
docs/PRD.md   Product Requirements Document
```
