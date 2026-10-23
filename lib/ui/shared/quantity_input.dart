import 'package:flutter/material.dart';

import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Isian jumlah dengan pilihan satuan milik sebuah bahan (satuan baku sedimensi dan
/// takaran kustom). Hanya tampilan: konversi ke satuan dasar memakai [UnitConverter].
class QuantityInput extends StatefulWidget {
  const QuantityInput({
    required this.catalog,
    required this.label,
    required this.onChanged,
    this.initialUnit,
    this.initialText = '',
    this.autofocus = false,
    this.allowedUnits,
    super.key,
  });

  final UnitCatalog catalog;
  final String label;
  final String? initialUnit;
  final String initialText;
  final bool autofocus;

  /// Batasi pilihan satuan (misalnya hanya satuan beli dan satuan dasar).
  final List<MeasureUnit>? allowedUnits;

  /// Dipanggil setiap kali jumlah atau satuan berubah.
  final void Function(String unitName, String text) onChanged;

  @override
  State<QuantityInput> createState() => _QuantityInputState();
}

class _QuantityInputState extends State<QuantityInput> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initialText);
  late String _unit = widget.initialUnit ?? widget.catalog.baseUnit.name;

  List<MeasureUnit> get _units => widget.allowedUnits ?? widget.catalog.selectable;

  @override
  void initState() {
    super.initState();
    // Satuan awal yang tidak lagi tersedia (mis. takaran dihapus) kembali ke satuan dasar.
    if (!_units.any((u) => u.name == _unit)) _unit = widget.catalog.baseUnit.name;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.onChanged(_unit, _controller.text);
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(
          flex: 3,
          child: TextField(
            controller: _controller,
            autofocus: widget.autofocus,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            inputFormatters: decimalInput,
            decoration: InputDecoration(labelText: widget.label),
            onChanged: (text) => widget.onChanged(_unit, text),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          flex: 2,
          child: DropdownButtonFormField<String>(
            initialValue: _unit,
            isExpanded: true,
            items: <DropdownMenuItem<String>>[
              for (final unit in _units)
                DropdownMenuItem<String>(value: unit.name, child: Text(unit.name)),
            ],
            onChanged: (value) {
              if (value == null) return;
              setState(() => _unit = value);
              widget.onChanged(value, _controller.text);
            },
          ),
        ),
      ],
    );
  }
}

/// Hasil konversi isian ke satuan dasar untuk pratinjau, atau `null` bila belum valid.
int? previewBase(
  Ingredient ingredient,
  String unitName,
  String text, {
  bool allowZero = false,
}) {
  if (text.trim().isEmpty) return null;
  try {
    return UnitConverter(ingredient.catalog).toBaseFromText(
      unitName,
      text,
      allowZero: allowZero,
    );
  } on DomainFailure {
    return null;
  }
}
