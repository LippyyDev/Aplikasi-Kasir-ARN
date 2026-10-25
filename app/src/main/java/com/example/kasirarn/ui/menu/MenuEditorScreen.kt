package com.example.kasirarn.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.CategoryEntity
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.MeasureEntity
import com.example.kasirarn.data.repo.MenuDraft
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.RecipeDraftLine
import com.example.kasirarn.data.repo.toState
import com.example.kasirarn.data.repo.unitsFor
import com.example.kasirarn.domain.CostCalculator
import com.example.kasirarn.domain.MoneyParser
import com.example.kasirarn.domain.RecipeLine
import com.example.kasirarn.domain.UnitConverter
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.QuantityInput
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.components.errorMessage
import com.example.kasirarn.ui.components.parseMoney
import com.example.kasirarn.ui.components.quantityToBase
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.ingredient.bestUnit
import com.example.kasirarn.ui.ingredient.dim
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeField(val ingredientId: Long, val qtyBase: Long)

class MenuEditorViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null)
        private set
    var name by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var priceText by mutableStateOf("")
    var photo by mutableStateOf<String?>(null)
        private set
    var active by mutableStateOf(true)
    var notes by mutableStateOf("")
    val recipe = mutableStateListOf<RecipeField>()
    var error by mutableStateOf<Op.Fail?>(null)
        private set
    var nameError by mutableStateOf(false)
        private set
    var priceError by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    private var originalPhoto: String? = null
    private val sessionPhotos = mutableSetOf<String>()
    private var saved = false

    val ingredients: StateFlow<List<IngredientEntity>> =
        c.ingredients.observeIngredients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val measures: StateFlow<Map<Long, List<MeasureEntity>>> =
        c.ingredients.observeMeasuresByIngredient().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val categories: StateFlow<List<CategoryEntity>> =
        c.menus.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (id != null) {
            viewModelScope.launch {
                val m = c.menus.getMenu(id)
                if (m != null) {
                    name = m.nama
                    categoryId = m.kategoriId
                    priceText = com.example.kasirarn.ui.format.Fmt(c.settings.current().language).number(m.hargaJual)
                    photo = m.fotoPath
                    originalPhoto = m.fotoPath
                    active = m.aktif
                    notes = m.catatan
                    recipe.addAll(c.menus.getRecipe(id).map { RecipeField(it.bahanId, it.jumlahDasar) })
                }
                loaded = true
            }
        }
    }

    fun pickPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            val saved = c.images.saveFromUri(uri, "menu")
            if (saved != null) {
                photo?.takeIf { it in sessionPhotos }?.let { c.images.delete(it); sessionPhotos -= it }
                sessionPhotos += saved
                photo = saved
            }
        }
    }

    fun removePhoto() {
        photo?.takeIf { it in sessionPhotos }?.let { c.images.delete(it); sessionPhotos -= it }
        photo = null
    }

    fun setRecipeLine(line: RecipeField) {
        val index = recipe.indexOfFirst { it.ingredientId == line.ingredientId }
        if (index >= 0) recipe[index] = line else recipe += line
    }

    fun removeRecipeLine(ingredientId: Long) { recipe.removeAll { it.ingredientId == ingredientId } }

    fun clearError() { error = null }

    fun save(onDone: () -> Unit) {
        val price = MoneyParser.parse(priceText)
        nameError = name.isBlank()
        priceError = price == null || price <= 0
        if (nameError || priceError) return
        saving = true
        viewModelScope.launch {
            val r = c.menus.save(
                MenuDraft(
                    id = id,
                    name = name,
                    categoryId = categoryId,
                    price = price ?: 0,
                    photo = photo,
                    active = active,
                    notes = notes,
                    recipe = recipe.map { RecipeDraftLine(it.ingredientId, it.qtyBase) },
                ),
            )
            saving = false
            when (r) {
                is Op.Ok -> {
                    saved = true
                    // Foto lama dibuang hanya setelah menu berhasil disimpan dengan foto baru.
                    if (originalPhoto != null && originalPhoto != photo) c.images.delete(originalPhoto)
                    sessionPhotos.filter { it != photo }.forEach { c.images.delete(it) }
                    onDone()
                }
                is Op.Fail -> error = r
            }
        }
    }

    override fun onCleared() {
        // Keluar tanpa menyimpan: buang foto yang dipilih selama sesi ini.
        if (!saved) sessionPhotos.forEach { c.images.delete(it) }
    }
}

@Composable
fun MenuEditorScreen(id: Long?, onBack: () -> Unit, onAddIngredient: () -> Unit) {
    val vm = containerViewModel(key = "menu-edit-$id") { MenuEditorViewModel(it, id) }
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    val measures by vm.measures.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fmt = LocalFmt.current
    var sheetFor by remember { mutableStateOf<RecipeSheetTarget?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri)
    }
    val failure = vm.error
    val failureText = failure?.let { errorMessage(it) }
    LaunchedEffect(failure) {
        if (failureText != null) {
            snackbar.showSnackbar(failureText)
            vm.clearError()
        }
    }

    val byId = ingredients.associateBy { it.id }
    val lines = vm.recipe.map { RecipeLine(it.ingredientId, it.qtyBase) }
    val cost = CostCalculator.perPortion(lines, ingredients.associate { it.id to it.toState() })
    val price = parseMoney(vm.priceText) ?: 0L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ArnTopBar(
                title = stringResource(if (id == null) R.string.menu_new else R.string.menu_edit),
                onBack = onBack,
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { vm.save(onBack) },
                    enabled = vm.loaded && !vm.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg),
                )
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                PhotoPicker(
                    photo = vm.photo,
                    name = vm.name,
                    onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = vm::removePhoto,
                )
            }
            item {
                ArnField(
                    value = vm.name,
                    onValueChange = { vm.name = it.take(60) },
                    label = stringResource(R.string.menu_name),
                    error = if (vm.nameError && vm.name.isBlank()) stringResource(R.string.err_name_empty) else null,
                )
            }
            item {
                MoneyField(
                    value = vm.priceText,
                    onValueChange = { vm.priceText = it },
                    label = stringResource(R.string.menu_price),
                    error = if (vm.priceError && (parseMoney(vm.priceText) ?: 0) <= 0) stringResource(R.string.err_price_invalid) else null,
                )
            }
            item {
                SectionLabel(stringResource(R.string.menu_category))
                if (categories.isEmpty()) {
                    Text(
                        stringResource(R.string.menu_category_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ChoiceChips(
                        options = listOf<Pair<Long?, String>>(null to stringResource(R.string.menu_no_category)) +
                            categories.map { it.id to it.nama },
                        selected = vm.categoryId,
                        onSelect = { vm.categoryId = it },
                    )
                    if (vm.categoryId == null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.menu_category_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.minTouch + 8.dp)
                        .toggleable(value = vm.active, role = Role.Switch, onValueChange = { vm.active = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.menu_active), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.menu_active_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.active, onCheckedChange = null)
                }
            }
            item {
                ArnField(
                    value = vm.notes,
                    onValueChange = { vm.notes = it.take(200) },
                    label = stringResource(R.string.menu_notes),
                    singleLine = false,
                    minLines = 2,
                )
            }
            item {
                SectionLabel(stringResource(R.string.recipe_title)) {
                    TextButton(onClick = { sheetFor = RecipeSheetTarget(null) }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.recipe_add))
                    }
                }
                Text(
                    stringResource(R.string.recipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.recipe.isEmpty()) {
                item {
                    ArnCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.recipe_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(vm.recipe.toList(), key = { it.ingredientId }) { line ->
                val ing = byId[line.ingredientId]
                if (ing != null) {
                    ArnCard(onClick = { sheetFor = RecipeSheetTarget(line) }, contentPadding = PaddingValues(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ing.nama, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    fmt.quantity(line.qtyBase, ing.dim()),
                                    style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val lineCost = CostCalculator.perPortion(listOf(RecipeLine(ing.id, line.qtyBase)), mapOf(ing.id to ing.toState()))
                            if (lineCost.complete) MoneyText(lineCost.total, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { vm.removeRecipeLine(ing.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recipe_remove, ing.nama))
                            }
                        }
                    }
                }
            }
            item {
                CostSummary(price = price, cost = cost.total, complete = cost.complete, hasRecipe = vm.recipe.isNotEmpty())
            }
        }
    }

    sheetFor?.let { target ->
        RecipeLineSheet(
            ingredients = ingredients,
            measures = measures,
            taken = vm.recipe.map { it.ingredientId }.toSet(),
            editing = target.line,
            onDismiss = { sheetFor = null },
            onSave = {
                vm.setRecipeLine(it)
                sheetFor = null
            },
            onAddIngredient = {
                sheetFor = null
                onAddIngredient()
            },
        )
    }
}

private data class RecipeSheetTarget(val line: RecipeField?)

@Composable
private fun PhotoPicker(photo: String?, name: String, onPick: () -> Unit, onRemove: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(role = Role.Button, onClick = onPick),
        ) {
            if (photo != null) {
                MenuImage(photo = photo, name = name, modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium)
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                ) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.menu_photo_remove), modifier = Modifier.padding(6.dp).size(20.dp))
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(Spacing.sm))
                        Text(stringResource(R.string.menu_photo_add), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.menu_photo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (photo != null) {
            TextButton(onClick = onPick) { Text(stringResource(R.string.menu_photo_change)) }
        }
    }
}

@Composable
private fun CostSummary(price: Long, cost: Long, complete: Boolean, hasRecipe: Boolean) {
    val margin = price - cost
    val pct = if (price > 0) (margin * 100 + price / 2) / price else 0
    ArnCard(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.cost_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.cost_hpp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(cost, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.cost_margin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(
                        margin,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (margin < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    )
                    Text("$pct%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasRecipe && !complete) {
                Spacer(Modifier.height(Spacing.md))
                StatusPill(stringResource(R.string.menu_cost_incomplete), Tone.WARN)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.cost_incomplete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!hasRecipe) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.cost_no_recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeLineSheet(
    ingredients: List<IngredientEntity>,
    measures: Map<Long, List<MeasureEntity>>,
    taken: Set<Long>,
    editing: RecipeField?,
    onDismiss: () -> Unit,
    onSave: (RecipeField) -> Unit,
    onAddIngredient: () -> Unit,
) {
    var selected by remember { mutableStateOf(editing?.let { e -> ingredients.firstOrNull { it.id == e.ingredientId } }) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val ing = selected
        if (ing == null) {
            val candidates = ingredients.filter { it.id !in taken && (query.isBlank() || it.nama.contains(query.trim(), ignoreCase = true)) }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg)) {
                Text(stringResource(R.string.recipe_pick_ingredient), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.md))
                if (ingredients.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.ingredient_empty_title),
                        body = stringResource(R.string.recipe_no_ingredients),
                        actionLabel = stringResource(R.string.ingredient_add_first),
                        onAction = onAddIngredient,
                    )
                } else {
                    ArnField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.ingredient_search),
                        trailing = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )
                    LazyColumn(Modifier.height(360.dp)) {
                        if (candidates.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.recipe_all_used),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Spacing.lg),
                                )
                            }
                        }
                        items(candidates, key = { it.id }) { candidate ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { selected = candidate },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(candidate.nama, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(candidate.dim().baseUnit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        } else {
            val units = unitsFor(ing.dim(), measures[ing.id].orEmpty())
            var unit by remember(ing.id) { mutableStateOf(editing?.let { bestUnit(it.qtyBase, ing.dim(), units) } ?: units.first()) }
            var text by remember(ing.id) {
                mutableStateOf(
                    editing?.let { UnitConverter.fromBase(it.qtyBase, unit).toDecimalString(3, ',') }.orEmpty(),
                )
            }
            var submitted by remember { mutableStateOf(false) }
            val qty = quantityToBase(text, unit)
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(ing.nama, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.recipe_qty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuantityInput(
                    text = text,
                    onText = { text = it },
                    units = units,
                    unit = unit,
                    onUnit = { unit = it },
                    baseUnitName = ing.satuanDasar,
                    label = stringResource(R.string.recipe_qty),
                    error = if (submitted && (qty == null || qty <= 0)) stringResource(R.string.err_invalid_number) else null,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        submitted = true
                        if (qty != null && qty > 0) onSave(RecipeField(ing.id, qty))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editing == null) {
                    SecondaryButton(
                        text = stringResource(R.string.recipe_change_ingredient),
                        onClick = { selected = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}
