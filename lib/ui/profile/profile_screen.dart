import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/data/images/image_store.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/logo_image.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Profil usaha (PRD 6.3.8): nama, logo, alamat, telepon, dan simbol mata uang.
/// Isinya dipakai pada struk dan layar Tentang.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final profile = ref.watch(businessProfileProvider);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.profileTitle)),
      body: AsyncBody<BusinessProfile>(
        value: profile,
        // Form dibangun sekali dari data tersimpan; aliran berikutnya tidak menimpa
        // isian yang sedang diketik.
        builder: (data) => _ProfileForm(initial: data),
      ),
    );
  }
}

class _ProfileForm extends ConsumerStatefulWidget {
  const _ProfileForm({required this.initial});

  final BusinessProfile initial;

  @override
  ConsumerState<_ProfileForm> createState() => _ProfileFormState();
}

class _ProfileFormState extends ConsumerState<_ProfileForm> {
  late final TextEditingController _name;
  late final TextEditingController _address;
  late final TextEditingController _phone;
  late final TextEditingController _currency;
  late String? _logoPath;
  late final String? _originalLogo;
  late final ImageStore _images;

  /// Logo baru yang dibuat pada sesi ini; dibuang bila tidak jadi dipakai.
  final List<String> _createdLogos = <String>[];
  bool _saved = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final p = widget.initial;
    _name = TextEditingController(text: p.name);
    _address = TextEditingController(text: p.address ?? '');
    _phone = TextEditingController(text: p.phone ?? '');
    _currency = TextEditingController(text: p.currency);
    _logoPath = p.logoPath;
    _originalLogo = p.logoPath;
    _images = ref.read(imageStoreProvider);
  }

  @override
  void dispose() {
    _name.dispose();
    _address.dispose();
    _phone.dispose();
    _currency.dispose();
    if (!_saved) {
      for (final created in _createdLogos) {
        _images.delete(created);
      }
    }
    super.dispose();
  }

  Future<void> _pickLogo() async {
    try {
      final picked = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        maxWidth: 1024,
      );
      if (picked == null) return;
      final name = await ref
          .read(imageStoreProvider)
          .saveFromFile(File(picked.path), prefix: 'logo');
      _createdLogos.add(name);
      if (mounted) setState(() => _logoPath = name);
    } on Object catch (e) {
      if (mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await ref.read(profileRepositoryProvider).save(
            BusinessProfile(
              name: _name.text,
              logoPath: _logoPath,
              address: _address.text,
              phone: _phone.text,
              currency: _currency.text,
            ),
          );
      _saved = true;
      // Logo lama dan logo sementara yang tidak dipakai dibersihkan setelah tersimpan.
      final old = _originalLogo;
      if (old != null && old != _logoPath) await _images.delete(old);
      for (final created in _createdLogos.where((n) => n != _logoPath)) {
        await _images.delete(created);
      }
      if (!mounted) return;
      showMessage(context, context.l10n.profileSaved);
      Navigator.of(context).pop();
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: <Widget>[
        Center(child: LogoImage(logoPath: _logoPath, size: 112)),
        const SizedBox(height: 12),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            OutlinedButton.icon(
              icon: const Icon(Icons.image_outlined),
              label: Text(l10n.profileLogoChoose),
              onPressed: _saving ? null : _pickLogo,
            ),
            if (_logoPath != null) ...<Widget>[
              const SizedBox(width: 8),
              TextButton(
                onPressed: _saving ? null : () => setState(() => _logoPath = null),
                child: Text(l10n.profileLogoRemove),
              ),
            ],
          ],
        ),
        const SizedBox(height: 20),
        TextField(
          controller: _name,
          textCapitalization: TextCapitalization.words,
          decoration: InputDecoration(labelText: l10n.profileName),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _address,
          minLines: 1,
          maxLines: 3,
          textCapitalization: TextCapitalization.sentences,
          decoration: InputDecoration(labelText: l10n.profileAddress),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _phone,
          keyboardType: TextInputType.phone,
          decoration: InputDecoration(labelText: l10n.profilePhone),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _currency,
          maxLength: 4,
          decoration: InputDecoration(
            labelText: l10n.profileCurrency,
            helperText: l10n.profileCurrencyHelp,
          ),
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: Text(l10n.actionSave),
        ),
      ],
    );
  }
}
