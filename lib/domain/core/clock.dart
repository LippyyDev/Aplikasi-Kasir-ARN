/// Sumber waktu yang dapat diganti pada uji. Selalu mengembalikan waktu UTC.
typedef Clock = DateTime Function();

DateTime systemClock() => DateTime.now().toUtc();
