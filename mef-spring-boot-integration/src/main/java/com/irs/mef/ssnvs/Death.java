package com.irs.mef.ssnvs;

/** SSA's death indicator at result position 121, with what the operator does when it is not N. */
enum Death {
    YES("Y", "SSA records this person as deceased: confirm the wages belong to this person."),
    NO("N", ""),
    UNKNOWN("?", "Death indicator is neither Y nor N: ask BSO support.");

    private final String flag;
    private final String action;

    Death(String flag, String action) {
        this.flag = flag;
        this.action = action;
    }

    static Death of(char indicator) {
        return switch (indicator) {
            case 'Y' -> YES;
            case 'N' -> NO;
            default -> UNKNOWN;
        };
    }

    String flag() {
        return flag;
    }

    String action() {
        return action;
    }
}
