import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

Map<String, dynamic> _load(String locale) =>
    jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync()) as Map<String, dynamic>;

Set<String> _messageKeys(Map<String, dynamic> arb) =>
    arb.keys.where((k) => !k.startsWith('@')).toSet();

Set<String> _placeholders(String message) => RegExp(r'\{(\w+)(?:,|\})')
    .allMatches(message)
    .map((m) => m.group(1)!)
    .toSet();

void main() {
  final id = _load('id');
  final en = _load('en');

  group('Berkas ARB', () {
    test('Indonesia dan Inggris memiliki kunci yang sama', () {
      final onlyId = _messageKeys(id).difference(_messageKeys(en));
      final onlyEn = _messageKeys(en).difference(_messageKeys(id));
      expect(onlyId, isEmpty, reason: 'Belum diterjemahkan ke Inggris');
      expect(onlyEn, isEmpty, reason: 'Tidak ada di templat Indonesia');
    });

    test('placeholder tiap pesan sama di kedua bahasa dan terdeklarasi di templat', () {
      for (final key in _messageKeys(id)) {
        final inId = _placeholders(id[key] as String);
        final inEn = _placeholders(en[key] as String);
        expect(inEn, inId, reason: 'Placeholder berbeda pada "$key"');

        final meta = id['@$key'] as Map<String, dynamic>?;
        final declared = (meta?['placeholders'] as Map<String, dynamic>?)?.keys.toSet() ??
            <String>{};
        expect(declared, inId, reason: 'Deklarasi placeholder "$key" tidak cocok');
      }
    });

    test('tidak ada pesan kosong dan tidak memakai tanda pisah em-dash', () {
      for (final arb in <Map<String, dynamic>>[id, en]) {
        for (final key in _messageKeys(arb)) {
          final text = arb[key] as String;
          expect(text.trim(), isNotEmpty, reason: 'Pesan kosong pada "$key"');
          expect(text.contains('—'), isFalse, reason: 'Em-dash pada "$key"');
        }
      }
    });
  });
}
