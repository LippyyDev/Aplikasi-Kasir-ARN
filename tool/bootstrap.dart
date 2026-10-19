// Menyiapkan folder `android/` dan berkas hasil generator sekali saja setelah clone.
//
// Pemakaian (dari akar repo, Flutter sudah terpasang):
//   dart tool/bootstrap.dart
//
// Langkah:
//   1. `flutter create --platforms=android` untuk membuat kerangka Gradle (berkas yang sudah
//      ada, termasuk AndroidManifest.xml milik repo, tidak ditimpa).
//   2. Menambal android/app/build.gradle(.kts): minSdk 26 dan core library desugaring
//      (dibutuhkan flutter_local_notifications).
//   3. `flutter pub get`, `build_runner` (kode Drift), ikon peluncur, dan splash native.
//
// Skrip ini idempoten. Bila penambalan gagal, skrip berhenti dengan petunjuk manual.
import 'dart:io';

const String _org = String.fromEnvironment('ARN_ORG', defaultValue: 'id.arn');
const String _desugarVersion = '2.1.5';

Future<void> main() async {
  if (!File('pubspec.yaml').existsSync()) {
    _fail('Jalankan dari akar repo (folder yang berisi pubspec.yaml).');
  }

  await _run('flutter', [
    'create',
    '--platforms=android',
    '--org',
    _org,
    '--project-name',
    'arn_kasir',
    '.',
  ]);

  // Uji bawaan flutter create (aplikasi penghitung) tidak berlaku untuk proyek ini.
  final defaultTest = File('test/widget_test.dart');
  if (defaultTest.existsSync() &&
      defaultTest.readAsStringSync().contains('MyApp')) {
    defaultTest.deleteSync();
    stdout.writeln('Menghapus test/widget_test.dart bawaan template.');
  }

  _patchGradle();

  await _run('flutter', ['pub', 'get']);
  await _run('dart', [
    'run',
    'build_runner',
    'build',
    '--delete-conflicting-outputs',
  ]);
  await _run('dart', ['run', 'flutter_launcher_icons']);
  await _run('dart', ['run', 'flutter_native_splash:create']);

  stdout.writeln('\nSelesai. Berikutnya: flutter analyze && flutter test');
}

void _patchGradle() {
  final kts = File('android/app/build.gradle.kts');
  final groovy = File('android/app/build.gradle');
  if (kts.existsSync()) {
    _patchKts(kts);
  } else if (groovy.existsSync()) {
    _patchGroovy(groovy);
  } else {
    _fail('android/app/build.gradle(.kts) tidak ditemukan setelah flutter create.');
  }
}

void _patchKts(File file) {
  var text = file.readAsStringSync();

  text = text.replaceAllMapped(
    RegExp(r'minSdk\s*=\s*flutter\.minSdkVersion'),
    (_) => 'minSdk = 26',
  );
  if (!RegExp(r'minSdk\s*=\s*26').hasMatch(text)) {
    _fail(
      'Gagal mengatur minSdk 26 pada ${file.path}. Ubah manual: minSdk = 26.',
    );
  }

  if (!text.contains('isCoreLibraryDesugaringEnabled')) {
    final patched = text.replaceFirstMapped(
      RegExp(r'compileOptions\s*\{'),
      (m) => '${m[0]}\n        isCoreLibraryDesugaringEnabled = true',
    );
    if (patched == text) {
      _fail(
        'Blok compileOptions tidak ditemukan pada ${file.path}. Tambahkan manual: '
        'isCoreLibraryDesugaringEnabled = true.',
      );
    }
    text = patched;
  }

  if (!text.contains('coreLibraryDesugaring(')) {
    text = '$text\n'
        'dependencies {\n'
        '    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:$_desugarVersion")\n'
        '}\n';
  }

  file.writeAsStringSync(text);
  stdout.writeln('Menambal ${file.path}: minSdk 26 dan desugaring.');
}

void _patchGroovy(File file) {
  var text = file.readAsStringSync();

  text = text.replaceAllMapped(
    RegExp(r'minSdkVersion\s+flutter\.minSdkVersion'),
    (_) => 'minSdkVersion 26',
  );
  if (!RegExp(r'minSdkVersion\s+26').hasMatch(text)) {
    _fail(
      'Gagal mengatur minSdkVersion 26 pada ${file.path}. Ubah manual.',
    );
  }

  if (!text.contains('coreLibraryDesugaringEnabled')) {
    final patched = text.replaceFirstMapped(
      RegExp(r'compileOptions\s*\{'),
      (m) => '${m[0]}\n        coreLibraryDesugaringEnabled true',
    );
    if (patched == text) {
      _fail(
        'Blok compileOptions tidak ditemukan pada ${file.path}. Tambahkan manual: '
        'coreLibraryDesugaringEnabled true.',
      );
    }
    text = patched;
  }

  if (!text.contains('coreLibraryDesugaring ')) {
    text = '$text\n'
        'dependencies {\n'
        "    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:$_desugarVersion'\n"
        '}\n';
  }

  file.writeAsStringSync(text);
  stdout.writeln('Menambal ${file.path}: minSdk 26 dan desugaring.');
}

Future<void> _run(String executable, List<String> args) async {
  stdout.writeln('\n\$ $executable ${args.join(' ')}');
  final process = await Process.start(
    executable,
    args,
    mode: ProcessStartMode.inheritStdio,
    runInShell: Platform.isWindows,
  );
  final code = await process.exitCode;
  if (code != 0) {
    _fail('Perintah gagal (kode $code): $executable ${args.join(' ')}');
  }
}

Never _fail(String message) {
  stderr.writeln('GALAT: $message');
  exit(1);
}
