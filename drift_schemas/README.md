# Skema Drift

Berkas ekspor skema per versi (`drift_schema_vN.json`) disimpan di sini dan di-*commit*, dipakai uji migrasi.

Ekspor setelah `build_runner` berhasil, dan ulangi setiap `schemaVersion` naik:

```
dart run drift_dev schema dump lib/data/database/app_database.dart drift_schemas/
```

Status: belum diekspor (kode belum pernah dikompilasi, lihat `docs/STATUS.md`).
