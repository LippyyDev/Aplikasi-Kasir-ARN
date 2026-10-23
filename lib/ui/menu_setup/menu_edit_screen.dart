import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/data/repositories/menu_repository.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_costing.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/models/menu_category.dart';
import 'package:arn_kasir/ui/home/menu_card.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/quantity_input.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Form menu beserta editor resep, dengan HPP dan margin yang dihitung langsung saat
/// resep berubah (PRD 6.3.4b).
class MenuEditScreen extends ConsumerStatefulWidget {
  const MenuEditScreen({this.menuId, super.key});

  /// `null` untuk menu baru.
  final int? menuId;

  @override
  ConsumerState<MenuEditScreen> createState() => _MenuEditScreenState();
}

class _MenuEditScreenState extends ConsumerState<MenuEditScreen> {
  final TextEditingController _name = TextEditingController();
  final TextEditingController _price = TextEditingController();
  final TextEditingController _note = TextEditingController();
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();

  int? _categoryId;
  bool _active = true;
  String? _photoPath;
  List<RecipeLine> _recipe = <RecipeLine>[];

  /// Foto yang dibuat selama sesi ini; yang tidak dipakai dihapus saat selesai.
  final List<String> _createdPhotos = <String>[];
  bool _loaded = false;
  bool _saving = false;
  bool _saved = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final id = widget.menuId;
    if (id != null) {
      final menu = await ref.read(menuRepositoryProvider).find(id);
      if (menu != null) {
        _name.text = menu.name;
        _price.text = '${menu.price}';
        _note.text = menu.note ?? '';
        _categoryId = menu.categoryId;
        _active = menu.active;
        _photoPath = menu.photoPath;
        _recipe = List<RecipeLine>.of(menu.recipe);
      }
    }
    if (mounted) setState(() => _loaded = true);
  }

  @override
  void dispose() {
    _name.dispose();
    _price.dispose();
    _note.dispose();
    if (!_saved) {
      // Dibatalkan: buang foto baru yang belum dipakai menu mana pun.
      final images = ref.read(imageStoreProvider);
      for (final created in _createdPhotos) {
        images.delete(created);
      }
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final categories = ref.watch(categoriesProvider).value ?? const <MenuCategory>[];
    final ingredients = ref.watch(allIngredientsProvider).value ?? const <Ingredient>[];
    final byId = <int, Ingredient>{for (final i in ingredients) i.id: i};
    final fmt = ref.watch(formattersProvider);

    if (!_loaded) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.menuId == null ? l10n.menuAdd : l10n.menuEditTitle),
        actions: <Widget>[
          TextButton(
            onPressed: _saving ? null : _save,
            child: Text(l10n.actionSave),
          ),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            _PhotoPicker(
              photoPath: _photoPath,
              onPick: _pickPhoto,
              onRemove: () => setState(() => _photoPath = null),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _name,
              maxLength: 60,
              textCapitalization: TextCapitalization.words,
              decoration: InputDecoration(labelText: l10n.menuNameLabel),
              validator: (value) =>
                  (value ?? '').trim().isEmpty ? l10n.errEmptyName : null,
            ),
            const SizedBox(height: 8),
            TextFormField(
              controller: _price,
              keyboardType: TextInputType.number,
              inputFormatters: digitsOnly,
              decoration: InputDecoration(
                labelText: l10n.menuPriceLabel,
                prefixText: '${fmt.currency} ',
              ),
              onChanged: (_) => setState(() {}),
              validator: (value) =>
                  parseMoney(value ?? '') == null ? l10n.errInvalidMoney : null,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<int?>(
              initialValue: categories.any((c) => c.id == _categoryId) ? _categoryId : null,
              isExpanded: true,
              decoration: InputDecoration(
                labelText: l10n.menuCategoryLabel,
                helperText: l10n.menuCategoryHelper,
              ),
              items: <DropdownMenuItem<int?>>[
                DropdownMenuItem<int?>(value: null, child: Text(l10n.categoryNone)),
                for (final c in categories)
                  DropdownMenuItem<int?>(value: c.id, child: Text(c.name)),
              ],
              onChanged: (value) => setState(() => _categoryId = value),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.menuActiveLabel),
              value: _active,
              onChanged: (value) => setState(() => _active = value),
            ),
            TextFormField(
              controller: _note,
              maxLength: 160,
              maxLines: 2,
              decoration: InputDecoration(labelText: l10n.menuNoteLabel),
            ),
            const SizedBox(height: 16),
            _RecipeSection(
              recipe: _recipe,
              ingredients: byId,
              allIngredients: ingredients.where((i) => !i.archived).toList(),
              price: parseMoney(_price.text) ?? 0,
              onChanged: (recipe) => setState(() => _recipe = recipe),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickPhoto() async {
    final l10n = context.l10n;
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: Text(l10n.photoGallery),
              onTap: () => Navigator.of(context).pop(ImageSource.gallery),
            ),
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: Text(l10n.photoCamera),
              onTap: () => Navigator.of(context).pop(ImageSource.camera),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    try {
      final picked = await ImagePicker().pickImage(source: source, maxWidth: 2048);
      if (picked == null) return;
      final name = await ref
          .read(imageStoreProvider)
          .saveFromFile(File(picked.path), prefix: 'menu');
      _createdPhotos.add(name);
      if (mounted) setState(() => _photoPath = name);
    } on Object catch (e) {
      if (mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _save() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() => _saving = true);
    try {
      await ref.read(menuRepositoryProvider).save(
            MenuDraft(
              id: widget.menuId,
              name: _name.text,
              price: parseMoney(_price.text)!,
              categoryId: _categoryId,
              active: _active,
              note: _note.text,
              photoPath: _photoPath,
              recipe: _recipe,
            ),
          );
      _saved = true;
      // Foto baru yang tidak jadi dipakai dibuang; foto lama dibersihkan oleh repositori.
      final images = ref.read(imageStoreProvider);
      for (final created in _createdPhotos.where((p) => p != _photoPath)) {
        await images.delete(created);
      }
      if (mounted) Navigator.of(context).pop();
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }
}

class _PhotoPicker extends StatelessWidget {
  const _PhotoPicker({
    required this.photoPath,
    required this.onPick,
    required this.onRemove,
  });

  final String? photoPath;
  final VoidCallback onPick;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Row(
      children: <Widget>[
        ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: MenuPhoto(photoPath: photoPath, size: 96),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Wrap(
            spacing: 8,
            children: <Widget>[
              OutlinedButton.icon(
                icon: const Icon(Icons.add_a_photo_outlined),
                label: Text(photoPath == null ? l10n.photoAdd : l10n.photoChange),
                onPressed: onPick,
              ),
              if (photoPath != null)
                TextButton(onPressed: onRemove, child: Text(l10n.photoRemove)),
            ],
          ),
        ),
      ],
    );
  }
}

class _RecipeSection extends ConsumerWidget {
  const _RecipeSection({
    required this.recipe,
    required this.ingredients,
    required this.allIngredients,
    required this.price,
    required this.onChanged,
  });

  final List<RecipeLine> recipe;
  final Map<int, Ingredient> ingredients;
  final List<Ingredient> allIngredients;
  final int price;
  final ValueChanged<List<RecipeLine>> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final draft = MenuItem(id: 0, name: '', price: price, recipe: recipe);
    final costing = MenuCosting.compute(draft, ingredients);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          children: <Widget>[
            Text(l10n.recipeTitle, style: theme.textTheme.titleMedium),
            const Spacer(),
            FilledButton.tonalIcon(
              icon: const Icon(Icons.add),
              label: Text(l10n.recipeAddIngredient),
              onPressed: () => _edit(context, null),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (recipe.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(l10n.recipeEmptyHint, style: theme.textTheme.bodyMedium),
          )
        else ...<Widget>[
          Card(
            child: Column(
              children: <Widget>[
                for (var i = 0; i < recipe.length; i++)
                  _RecipeLineTile(
                    line: recipe[i],
                    ingredient: ingredients[recipe[i].ingredientId],
                    onTap: () => _edit(context, i),
                    onDelete: () => onChanged(<RecipeLine>[
                      for (var j = 0; j < recipe.length; j++)
                        if (j != i) recipe[j],
                    ]),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Card(
            color: theme.colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: <Widget>[
                  _SummaryRow(label: l10n.recipeCostPerPortion, value: fmt.money(costing.unitCost)),
                  _SummaryRow(label: l10n.recipeMargin, value: fmt.money(costing.margin)),
                  _SummaryRow(
                    label: l10n.recipeMarginPercent,
                    value: costing.marginPermille == null
                        ? '-'
                        : fmt.permille(costing.marginPermille!),
                  ),
                  if (costing.incomplete)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Row(
                        children: <Widget>[
                          Icon(Icons.info_outline, size: 18, color: theme.colorScheme.error),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              l10n.menuCostIncompleteHint,
                              style: theme.textTheme.bodySmall,
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }

  Future<void> _edit(BuildContext context, int? index) async {
    final used = <int>{
      for (var i = 0; i < recipe.length; i++)
        if (i != index) recipe[i].ingredientId,
    };
    final candidates = <Ingredient>[
      for (final ingredient in allIngredients)
        if (!used.contains(ingredient.id)) ingredient,
    ];
    final result = await showModalBottomSheet<RecipeLine>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => _RecipeLineSheet(
        candidates: candidates,
        initial: index == null ? null : recipe[index],
        ingredients: ingredients,
      ),
    );
    if (result == null) return;
    final next = List<RecipeLine>.of(recipe);
    if (index == null) {
      next.add(result);
    } else {
      next[index] = result;
    }
    onChanged(next);
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: <Widget>[
          Text(label),
          const Spacer(),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}

class _RecipeLineTile extends ConsumerWidget {
  const _RecipeLineTile({
    required this.line,
    required this.ingredient,
    required this.onTap,
    required this.onDelete,
  });

  final RecipeLine line;
  final Ingredient? ingredient;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final item = ingredient;
    final cost = item != null && item.hasAverageCost
        ? StockLedger.consume(item.stock, line.quantity).cost
        : null;
    return ListTile(
      title: Text(item?.name ?? l10n.recipeMissingIngredient),
      subtitle: Text(
        <String>[
          if (item != null) fmt.baseQuantity(item, line.quantity),
          cost == null ? l10n.recipeNoCostYet : fmt.money(cost),
        ].join('  |  '),
      ),
      onTap: onTap,
      trailing: IconButton(
        icon: const Icon(Icons.close),
        tooltip: l10n.actionDelete,
        onPressed: onDelete,
      ),
    );
  }
}

class _RecipeLineSheet extends ConsumerStatefulWidget {
  const _RecipeLineSheet({
    required this.candidates,
    required this.ingredients,
    this.initial,
  });

  final List<Ingredient> candidates;
  final Map<int, Ingredient> ingredients;
  final RecipeLine? initial;

  @override
  ConsumerState<_RecipeLineSheet> createState() => _RecipeLineSheetState();
}

class _RecipeLineSheetState extends ConsumerState<_RecipeLineSheet> {
  Ingredient? _ingredient;
  String _unit = '';
  String _text = '';

  @override
  void initState() {
    super.initState();
    final initial = widget.initial;
    if (initial != null) {
      _ingredient = widget.ingredients[initial.ingredientId];
      _text = '${initial.quantity}';
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final ingredient = _ingredient;
    final base = ingredient == null ? null : previewBase(ingredient, _unit, _text);
    // Bahan yang sedang diedit tetap muncul walau sudah diarsipkan.
    final options = <Ingredient>[
      if (ingredient != null && !widget.candidates.any((c) => c.id == ingredient.id))
        ingredient,
      ...widget.candidates,
    ];

    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        16 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(l10n.recipeLineTitle, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          DropdownButtonFormField<int>(
            initialValue: ingredient?.id,
            isExpanded: true,
            decoration: InputDecoration(labelText: l10n.recipeIngredientLabel),
            items: <DropdownMenuItem<int>>[
              for (final option in options)
                DropdownMenuItem<int>(value: option.id, child: Text(option.name)),
            ],
            onChanged: widget.initial != null
                ? null
                : (id) => setState(() {
                      _ingredient = options.firstWhere((o) => o.id == id);
                      _text = '';
                    }),
          ),
          const SizedBox(height: 12),
          if (ingredient != null) ...<Widget>[
            QuantityInput(
              key: ValueKey<int>(ingredient.id),
              catalog: ingredient.catalog,
              label: l10n.recipeAmountLabel,
              initialText: _text,
              autofocus: true,
              onChanged: (unit, text) => setState(() {
                _unit = unit;
                _text = text;
              }),
            ),
            if (base != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  l10n.recipeStoredAs(fmt.baseQuantity(ingredient, base)),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: ingredient != null && base != null && base > 0
                ? () => Navigator.of(context).pop(
                      RecipeLine(ingredientId: ingredient.id, quantity: base),
                    )
                : null,
            child: Text(l10n.actionSave),
          ),
        ],
      ),
    );
  }
}
