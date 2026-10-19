import '../support/drift_harness.dart';
import '../support/sales_scenarios.dart';

void main() {
  defineSalesScenarios('Drift (SQLite dalam memori)', () async => DriftHarness());
}
