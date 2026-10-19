import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';

/// Profil usaha (baris tunggal id = 1; PRD 6.3.8).
final class ProfileRepository {
  ProfileRepository(this._db);

  static const int rowId = 1;

  final AppDatabase _db;

  Stream<BusinessProfile> watch() {
    final query = _db.select(_db.businessProfiles)
      ..where((t) => t.id.equals(rowId));
    return query.watchSingleOrNull().map(_map);
  }

  Future<BusinessProfile> load() async => _map(
        await (_db.select(_db.businessProfiles)..where((t) => t.id.equals(rowId)))
            .getSingleOrNull(),
      );

  Future<void> save(BusinessProfile profile) {
    return _db.into(_db.businessProfiles).insertOnConflictUpdate(
          BusinessProfilesCompanion(
            id: const Value(rowId),
            name: Value(profile.name.trim()),
            logoPath: Value(profile.logoPath),
            address: Value(_blankToNull(profile.address)),
            phone: Value(_blankToNull(profile.phone)),
            currency: Value(profile.currency.trim().isEmpty ? 'Rp' : profile.currency.trim()),
          ),
        );
  }

  BusinessProfile _map(BusinessProfileRow? row) => row == null
      ? const BusinessProfile()
      : BusinessProfile(
          name: row.name,
          logoPath: row.logoPath,
          address: row.address,
          phone: row.phone,
          currency: row.currency,
        );

  static String? _blankToNull(String? text) {
    final trimmed = text?.trim();
    return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
  }
}
