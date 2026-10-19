/// Profil usaha (PRD 6.2 A): dipakai pada struk dan layar Tentang.
final class BusinessProfile {
  const BusinessProfile({
    this.name = '',
    this.logoPath,
    this.address,
    this.phone,
    this.currency = 'Rp',
  });

  final String name;

  /// Nama berkas relatif terhadap folder gambar aplikasi.
  final String? logoPath;
  final String? address;
  final String? phone;

  /// Simbol mata uang yang ditampilkan di depan nominal.
  final String currency;
}
