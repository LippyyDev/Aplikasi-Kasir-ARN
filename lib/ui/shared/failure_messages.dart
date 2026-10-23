import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/l10n/generated/app_localizations.dart';
import 'package:arn_kasir/ui/shared/formatters.dart';

/// Memetakan galat domain dan data menjadi pesan untuk pengguna. Galat yang tidak
/// dikenal tidak membocorkan detail teknis.
String failureMessage(AppLocalizations l10n, AppFormatters fmt, Object error) {
  switch (error) {
    case InvalidNumberFormat():
      return l10n.errInvalidNumber;
    case NonPositiveQuantity():
      return l10n.errNonPositiveQuantity;
    case QuantityRoundsToZero():
      return l10n.errQuantityRoundsToZero;
    case DimensionMismatch(:final unitName):
      return l10n.errDimensionMismatch(unitName);
    case UnknownUnit(:final name):
      return l10n.errUnknownUnit(name);
    case InvalidUnitDefinition(:final name, :final reason):
      return switch (reason) {
        InvalidUnitReason.emptyName => l10n.errUnitEmptyName,
        InvalidUnitReason.nameCollision => l10n.errUnitDuplicate(name),
        InvalidUnitReason.nonPositiveFactor => l10n.errUnitFactor(name),
      };
    case ArithmeticOverflow():
      return l10n.errTooLarge;
    case InvalidRecipe(:final reason):
      return switch (reason) {
        InvalidRecipeReason.nonPositiveQuantity => l10n.errRecipeQuantity,
        InvalidRecipeReason.duplicateIngredient => l10n.errRecipeDuplicate,
      };
    case InvalidMoneyAmount():
      return l10n.errInvalidMoney;
    case InsufficientStock(:final shortages):
      final first = shortages.isEmpty ? null : shortages.first;
      return first == null
          ? l10n.errInsufficientStock
          : l10n.errInsufficientStockFor(first.ingredientName);
    case MenuUnavailable():
      return l10n.errMenuUnavailable;
    case EmptyCart():
      return l10n.errEmptyCart;
    case InvalidDiscount():
      return l10n.errInvalidDiscount;
    case InsufficientPayment(:final total, :final paid):
      return l10n.errInsufficientPayment(fmt.money(total), fmt.money(paid));
    case SaleNotFound():
      return l10n.errSaleNotFound;
    case SaleAlreadyVoided():
      return l10n.errSaleAlreadyVoided;
    case IngredientNotFound():
      return l10n.errIngredientNotFound;
    case IngredientInUse(:final menuNames):
      return l10n.errIngredientInUse(menuNames.join(', '));
    case DuplicateName(:final name):
      return l10n.errDuplicateName(name);
    case EmptyName():
      return l10n.errEmptyName;
    case ProtectedRecord():
      return l10n.errProtectedRecord;
    case DimensionLocked():
      return l10n.errDimensionLocked;
    case CategoryInUse():
      return l10n.errCategoryInUse;
    case RecordNotFound():
      return l10n.errRecordNotFound;
    case InvalidBackupArchive():
      return l10n.errBackupInvalid;
    case UnsupportedBackupFormat():
      return l10n.errBackupFormat;
    case BackupChecksumMismatch():
      return l10n.errBackupChecksum;
    case BackupSchemaTooNew():
      return l10n.errBackupSchemaTooNew;
    case BackupDatabaseCorrupt():
      return l10n.errBackupDatabase;
    case BackupIoFailure():
      return l10n.errBackupIo;
    case RestoreRolledBack():
      return l10n.errRestoreRolledBack;
    default:
      return l10n.errUnexpected;
  }
}
