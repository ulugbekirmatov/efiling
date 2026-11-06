package com.irs.mef.w2sheet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One employee's Form W-2 for one employer and tax year, as the employee sheet prints it.
 *
 * The employee SSN is held as its last four digits only. Pub 1141 2.3.07 allows a truncated SSN on
 * the employee copies, and a full SSN that never enters this type cannot leak onto the paper.
 */
public record W2Statement(
        int taxYear,
        Employer employer,
        Employee employee,
        CoreAmounts core,
        OptionalAmounts optional,
        List<CodedAmount> box12,
        Box13 box13,
        List<LabeledAmount> box14a,
        List<String> box14b,
        List<StateLine> stateLines,
        List<LocalLine> localLines) {

    private static final Pattern EIN = Pattern.compile("\\d{2}-\\d{7}");
    private static final Pattern SSN_LAST_FOUR = Pattern.compile("\\d{4}");
    private static final Pattern BOX_12_CODE = Pattern.compile("[A-Z]{1,2}");

    public W2Statement {
        require(employer, "employer");
        require(employee, "employee");
        require(core, "core");
        optional = optional == null ? new OptionalAmounts(null, null, null, null) : optional;
        box12 = List.copyOf(orEmpty(box12));
        box13 = box13 == null ? new Box13(false, false, false) : box13;
        box14a = List.copyOf(orEmpty(box14a));
        box14b = List.copyOf(orEmpty(box14b));
        stateLines = List.copyOf(orEmpty(stateLines));
        localLines = List.copyOf(orEmpty(localLines));
    }

    public record Employer(String ein, String name, List<String> address) {
        public Employer {
            if (ein == null || !EIN.matcher(ein).matches()) {
                throw new W2SheetException("employer.ein must look like 12-3456789, got " + ein);
            }
            requireText(name, "employer.name");
            address = List.copyOf(require(address, "employer.address"));
        }
    }

    public record Employee(String firstName, String lastName, String suffix, String ssnLast4, List<String> address) {
        public Employee {
            requireText(firstName, "employee.firstName");
            requireText(lastName, "employee.lastName");
            if (ssnLast4 == null || !SSN_LAST_FOUR.matcher(ssnLast4).matches()) {
                throw new W2SheetException("employee.ssnLast4 must be exactly four digits");
            }
            address = List.copyOf(require(address, "employee.address"));
        }

        String fullName() {
            return firstName + " " + lastName + (suffix == null || suffix.isBlank() ? "" : " " + suffix);
        }
    }

    /** Boxes 1 to 6, which every copy must carry (Pub 1141 2.3.08). */
    public record CoreAmounts(
            BigDecimal wages,
            BigDecimal federalIncomeTaxWithheld,
            BigDecimal socialSecurityWages,
            BigDecimal socialSecurityTaxWithheld,
            BigDecimal medicareWages,
            BigDecimal medicareTaxWithheld) {
        public CoreAmounts {
            require(wages, "core.wages");
            require(federalIncomeTaxWithheld, "core.federalIncomeTaxWithheld");
            require(socialSecurityWages, "core.socialSecurityWages");
            require(socialSecurityTaxWithheld, "core.socialSecurityTaxWithheld");
            require(medicareWages, "core.medicareWages");
            require(medicareTaxWithheld, "core.medicareTaxWithheld");
        }
    }

    /** Boxes 7, 8, 10 and 11. A null amount is left off the sheet (Pub 1141 2.3.08). */
    public record OptionalAmounts(
            BigDecimal socialSecurityTips,
            BigDecimal allocatedTips,
            BigDecimal dependentCareBenefits,
            BigDecimal nonqualifiedPlans) {}

    public record CodedAmount(String code, BigDecimal amount) {
        public CodedAmount {
            if (code == null || !BOX_12_CODE.matcher(code).matches()) {
                throw new W2SheetException("box12 code must be one or two capital letters, got " + code);
            }
            require(amount, "box12 amount");
        }
    }

    public record Box13(boolean statutoryEmployee, boolean retirementPlan, boolean thirdPartySickPay) {}

    public record LabeledAmount(String label, BigDecimal amount) {
        public LabeledAmount {
            requireText(label, "box14a label");
            require(amount, "box14a amount");
        }
    }

    public record StateLine(String state, String stateId, BigDecimal wages, BigDecimal tax) {
        public StateLine {
            requireText(state, "stateLines.state");
        }
    }

    public record LocalLine(String locality, BigDecimal wages, BigDecimal tax) {
        public LocalLine {
            requireText(locality, "localLines.locality");
        }
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new W2SheetException(field + " is required");
        }
        return value;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new W2SheetException(field + " is required");
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return Objects.requireNonNullElse(list, List.of());
    }
}
