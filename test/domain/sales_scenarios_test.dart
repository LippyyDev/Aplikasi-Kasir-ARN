import '../support/in_memory_harness.dart';
import '../support/sales_scenarios.dart';

void main() {
  defineSalesScenarios('repositori dalam memori', () async => InMemoryHarness());
}
