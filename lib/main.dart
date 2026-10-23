import 'package:flutter/material.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/app.dart';
import 'package:arn_kasir/app/bootstrap/app_dependencies.dart';
import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/restart_scope.dart';

Future<void> main() async {
  final binding = WidgetsFlutterBinding.ensureInitialized();
  FlutterNativeSplash.preserve(widgetsBinding: binding);
  runApp(const RestartScope(child: ArnRoot()));
}

/// Menyiapkan dependensi (jalur berkas, database, pengaturan) lalu menampilkan [ArnApp].
/// Dibangun ulang oleh [RestartScope] setelah pemulihan data.
class ArnRoot extends StatefulWidget {
  const ArnRoot({super.key});

  @override
  State<ArnRoot> createState() => _ArnRootState();
}

class _ArnRootState extends State<ArnRoot> {
  late final Future<AppDependencies> _dependencies = AppDependencies.load();
  AppDependencies? _loaded;

  @override
  void dispose() {
    // Menutup koneksi yang masih terbuka. Aman bila sudah ditutup oleh pemulihan.
    _loaded?.database.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AppDependencies>(
      future: _dependencies,
      builder: (context, snapshot) {
        final deps = snapshot.data;
        if (deps != null) {
          _loaded = deps;
          return ProviderScope(
            overrides: [
              appPathsProvider.overrideWithValue(deps.paths),
              databaseProvider.overrideWithValue(deps.database),
              sharedPreferencesProvider.overrideWithValue(deps.preferences),
              systemNotifierProvider.overrideWithValue(deps.systemNotifier),
              appVersionProvider.overrideWithValue(deps.appVersion),
              buildNumberProvider.overrideWithValue(deps.buildNumber),
            ],
            child: const ArnApp(),
          );
        }
        if (snapshot.hasError) {
          return _StartupError(error: snapshot.error!);
        }
        // Splash native masih tampil; tidak ada yang digambar di sini.
        return const SizedBox.shrink();
      },
    );
  }
}

/// Galat saat menyiapkan aplikasi (misalnya penyimpanan penuh). Berbahasa Indonesia
/// dan Inggris sekaligus karena paket bahasa belum tersedia pada tahap ini.
class _StartupError extends StatelessWidget {
  const _StartupError({required this.error});

  final Object error;

  @override
  Widget build(BuildContext context) {
    FlutterNativeSplash.remove();
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                const Icon(Icons.error_outline, size: 48),
                const SizedBox(height: 16),
                const Text(
                  'Aplikasi tidak dapat dimulai / The app could not start',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 12),
                Text('$error'),
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: () => RestartScope.restart(context),
                  child: const Text('Coba lagi / Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
