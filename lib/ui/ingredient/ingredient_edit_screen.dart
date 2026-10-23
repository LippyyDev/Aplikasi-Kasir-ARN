import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/data/repositories/ingredient_repository.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/quantity_input.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Satu baris takaran kustom pada formulir.
final class _UnitRow {
  _UnitRow({String name = '', String factor = '', this.original})
      : name = TextEditingController(text: name),
        factor = TextEditingController(text: factor),
        initialFactorText = factor;

  final TextEditingController name;
  final TextEditingController factor;

  /// Faktor tersimpan; dipakai apa adanya bila teksnya tidak diubah, supaya pecahan
  /// seperti 1/3 tidak terpotong oleh tampilan desimal.
  final Fraction? original;
  final String initialFactorText;

  void dispose() {
    name.dispose();
    factor.dispose();
  }

  Fraction? parsedFactor() {
    if (original != null && factor.text == initialFactorText) return original;
    return Fraction.tryParse(factor.text.trim());
  }
}

/// Form tambah dan ubah bahan baku (PRD 6.3.5).
class IngredientEditScreen extends ConsumerStatefulWidget {
  const IngredientEditScreen({this.ingredientId, super.key});

  final int? ingredientId;

  @override
  ConsumerState<IngredientEditScreen> createState() => _IngredientEditScreenState();
}

class _IngredientEditScreenState extends ConsumerState<IngredientEditScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _name = TextEditingController();
  final TextEditingController _openingValue = TextEditingController();
  final List<_UnitRow> _units = <_UnitRow>[];

  Dimension _dimension = Dimension.mass;
  String? _purchaseUnit;
  String _minUnit = '';
  String _minText = '';
  String _openingUnit = '';
  String _openingText = '';
  bool _loaded = false;
  bool _saving = false;

  bool get _isNew => widget.ingredientId == null;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final id = widget.ingredientId;
    if (id != null) {
      final ingredient = await ref.read(ingredientRepositoryProvider).find(id);
      if (ingredient != null) {
        _name.text = ingredient.name;
        _dimension = ingredient.dimension;
        _purchaseUnit = ingredient.purchaseUnitName;
        _minText = '${ingredient.minStock}';
        _minUnit = ingredient.dimension.baseUnitName;
        for (final unit in ingredient.customUnits) {
          _units.add(
            _UnitRow(
              name: unit.name,
              factor: unit.factor.toDecimalString(maxFractionDigits: 6),
              original: unit.factor,
            ),
          );
        }
      }
    }
    if (mounted) setState(() => _loaded = true);
  }

  @override
  void dispose() {
    _name.dispose();
    _openingValue.dispose();
    for (final unit in _units) {
      unit.dispose();
    }
    super.dispose();
  }

  /// Katalog satuan dari baris yang saat ini valid; baris rusak dilewati untuk pratinjau.
  UnitCatalog _previewCatalog() {
    final customs = <CustomUnit>[];
    for (final row in _units) {
      final name = row.name.text.trim();
      final factor = row.parsedFactor();
      if (name.isNotEmpty && factor != null && factor.isPositive) {
        customs.add(CustomUnit(name: name, factor: factor));
      }
    }
    try {
      return UnitCatalog(_dimension, customs);
    } on DomainFailure {
      return UnitCatalog(_dimension);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);

    if (!_loaded) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    final catalog = _previewCatalog();
    final purchaseOptions = catalog.selectable;
    final purchaseValue =
        purchaseOptions.any((u) => u.name == _purchaseUnit) ? _purchaseUnit : null;

    return Scaffold(
      appBar: AppBar(
        title: Text(_isNew ? l10n.ingredientAdd : l10n.ingredientEditTitle),
        actions: <Widget>[
          TextButton(onPressed: _saving ? null : _save, child: Text(l10n.actionSave)),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            TextFormField(
              controller: _name,
              maxLength: 60,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(labelText: l10n.ingredientNameLabel),
              validator: (value) =>
                  (value ?? '').trim().isEmpty ? l10n.errEmptyName : null,
            ),
            const SizedBox(height: 8),
            Text(l10n.ingredientDimensionLabel, style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 8),
            SegmentedButton<Dimension>(
              segments: <ButtonSegment<Dimension>>[
                ButtonSegment<Dimension>(
                  value: Dimension.mass,
                  label: Text(l10n.dimensionMass),
                ),
                ButtonSegment<Dimension>(
                  value: Dimension.volume,
                  label: Text(l10n.dimensionVolume),
                ),
                ButtonSegment<Dimension>(
                  value: Dimension.count,
                  label: Text(l10n.dimensionCount),
                ),
              ],
              selected: <Dimension>{_dimension},
              onSelectionChanged: (value) => setState(() {
                _dimension = value.first;
                _purchaseUnit = null;
                _minUnit = _dimension.baseUnitName;
              }),
            ),
            const SizedBox(height: 4),
            Text(
              l10n.ingredientBaseUnitHint(_dimension.baseUnitName),
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String?>(
              key: ValueKey<Object>((_dimension, _units.length, purchaseOptions.length)),
              initialValue: purchaseValue,
              isExpanded: true,
              decoration: InputDecoration(labelText: l10n.ingredientPurchaseUnitLabel),
              items: <DropdownMenuItem<String?>>[
                DropdownMenuItem<String?>(value: null, child: Text(l10n.categoryNone)),
                for (final unit in purchaseOptions)
                  DropdownMenuItem<String?>(value: unit.name, child: Text(unit.name)),
              ],
              onChanged: (value) => setState(() => _purchaseUnit = value),
            ),
            const SizedBox(height: 16),
            QuantityInput(
              key: ValueKey<Object>(('min', _dimension, _units.length)),
              catalog: catalog,
              label: l10n.ingredientMinStockLabel,
              initialText: _minText,
              initialUnit: _minUnit.isEmpty ? null : _minUnit,
              onChanged: (unit, text) {
                _minUnit = unit;
                _minText = text;
              },
            ),
            const SizedBox(height: 24),
            _CustomUnitsEditor(
              rows: _units,
              baseUnit: _dimension.baseUnitName,
              onChanged: () => setState(() {}),
            ),
            if (_isNew) ...<Widget>[
              const SizedBox(height: 24),
              Text(l10n.ingredientOpeningStock, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(l10n.ingredientOpeningHint, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 12),
              QuantityInput(
                key: ValueKey<Object>(('open', _dimension, _units.length)),
                catalog: catalog,
                label: l10n.ingredientOpeningQuantity,
                initialText: _openingText,
                initialUnit: _openingUnit.isEmpty ? null : _openingUnit,
                onChanged: (unit, text) {
                  _openingUnit = unit;
                  _openingText = text;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _openingValue,
                keyboardType: TextInputType.number,
                inputFormatters: digitsOnly,
                decoration: InputDecoration(
                  labelText: l10n.ingredientOpeningValue,
                  prefixText: '${fmt.currency} ',
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() => _saving = true);
    try {
      final customs = <CustomUnit>[];
      for (final row in _units) {
        final name = row.name.text.trim();
        if (name.isEmpty && row.factor.text.trim().isEmpty) continue;
        final factor = row.parsedFactor();
        if (factor == null) throw InvalidNumberFormat(row.factor.text);
        customs.add(CustomUnit(name: name, factor: factor));
      }
      final catalog = UnitCatalog(_dimension, customs);
      final converter = UnitConverter(catalog);

      var minBase = 0;
      if (_minText.trim().isNotEmpty) {
        minBase = converter.toBaseFromText(
          _minUnit.isEmpty ? _dimension.baseUnitName : _minUnit,
          _minText,
          allowZero: true,
        );
      }
      var openingBase = 0;
      if (_isNew && _openingText.trim().isNotEmpty) {
        openingBase = converter.toBaseFromText(
          _openingUnit.isEmpty ? _dimension.baseUnitName : _openingUnit,
          _openingText,
          allowZero: true,
        );
      }

      await ref.read(ingredientRepositoryProvider).save(
            IngredientDraft(
              id: widget.ingredientId,
              name: _name.text,
              dimension: _dimension,
              purchaseUnitName: _purchaseUnit,
              minStock: minBase,
              customUnits: customs,
              openingQuantity: openingBase,
              openingValue: parseMoney(_openingValue.text) ?? 0,
            ),
          );
      if (mounted) Navigator.of(context).pop();
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }
}

class _CustomUnitsEditor extends StatelessWidget {
  const _CustomUnitsEditor({
    required this.rows,
    required this.baseUnit,
    required this.onChanged,
  });

  final List<_UnitRow> rows;
  final String baseUnit;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          children: <Widget>[
            Text(l10n.customUnitsTitle, style: theme.textTheme.titleMedium),
            const Spacer(),
            TextButton.icon(
              icon: const Icon(Icons.add),
              label: Text(l10n.customUnitsAdd),
              onPressed: () {
                rows.add(_UnitRow());
                onChanged();
              },
            ),
          ],
        ),
        Text(l10n.customUnitsHint(baseUnit), style: theme.textTheme.bodySmall),
        const SizedBox(height: 8),
        for (var i = 0; i < rows.length; i++)
          Padding(
            key: ObjectKey(rows[i]),
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  flex: 3,
                  child: TextField(
                    controller: rows[i].name,
                    maxLength: 24,
                    decoration: InputDecoration(
                      labelText: l10n.customUnitName,
                      counterText: '',
                    ),
                    onChanged: (_) => onChanged(),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.fromLTRB(8, 16, 8, 0),
                  child: Text('='),
                ),
                Expanded(
                  flex: 3,
                  child: TextField(
                    controller: rows[i].factor,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    inputFormatters: decimalInput,
                    decoration: InputDecoration(
                      labelText: l10n.customUnitFactor,
                      suffixText: baseUnit,
                    ),
                    onChanged: (_) => onChanged(),
                  ),
                ),
                IconButton(
                  tooltip: l10n.actionDelete,
                  icon: const Icon(Icons.close),
                  onPressed: () {
                    rows.removeAt(i).dispose();
                    onChanged();
                  },
                ),
              ],
            ),
          ),
      ],
    );
  }
}
