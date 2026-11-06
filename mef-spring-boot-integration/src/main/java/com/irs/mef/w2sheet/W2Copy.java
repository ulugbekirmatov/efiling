package com.irs.mef.w2sheet;

/** The employee copies of Form W-2 and what differs between them (Pub 1141 2.3.10, 2.3.12, 2.3.18). */
enum W2Copy {
    B("Copy B—To Be Filed With Employee’s FEDERAL Tax Return.", true, false),
    C("Copy C—For EMPLOYEE’S RECORDS.", false, true),
    STATE("Copy 2—To Be Filed With Employee’s State, City, or Local Income Tax Return.", false, false);

    static final String NEGLIGENCE_NOTE = "This information is being furnished to the Internal Revenue Service. "
            + "If you are required to file a tax return, a negligence penalty or other sanction may be imposed "
            + "on you if this income is taxable and you fail to report it.";

    final String label;
    final boolean boldCoreRule;
    final boolean negligenceNote;

    W2Copy(String label, boolean boldCoreRule, boolean negligenceNote) {
        this.label = label;
        this.boldCoreRule = boldCoreRule;
        this.negligenceNote = negligenceNote;
    }

    String backLabel() {
        return "Back of " + label.substring(0, label.indexOf('—'));
    }
}
