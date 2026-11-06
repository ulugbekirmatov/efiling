package com.irs.mef.wagefile.accuwage;

import java.util.ArrayList;
import java.util.List;

final class AccuWageScenarios {

    private AccuWageScenarios() {
    }

    static List<AccuWageScenario> all() {
        List<AccuWageScenario> all = new ArrayList<>(List.of(
                Scenario01SingleEmployer.scenario(),
                Scenario02MultiEmployer.scenario(),
                Scenario03HighEarners.scenario(),
                Scenario04RetirementAndHealth.scenario(),
                Scenario05Volume.scenario(),
                Scenario06NamesAndAddresses.scenario(),
                Scenario07TippedEmployees.scenario(),
                Scenario08Resubmission.scenario()));
        all.addAll(NegativeScenarios.scenarios());
        return List.copyOf(all);
    }
}
