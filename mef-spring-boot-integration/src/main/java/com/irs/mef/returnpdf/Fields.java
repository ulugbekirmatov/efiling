package com.irs.mef.returnpdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Field values for one IRS form, keyed by the PDF's fully qualified field name. Knows how IRS boxes
 * split a value: dollars and cents in two fields, an EIN across comb or per-digit fields, a
 * checkbox set by its on-state name.
 */
final class Fields {

    private static final int CENTS_SCALE = 2;

    private final Map<String, String> values = new LinkedHashMap<>();

    void text(String field, Optional<String> value) {
        value.ifPresent(present -> put(field, present));
    }

    void text(String field, String value) {
        put(field, value);
    }

    /** An IRS amount box: whole dollars with thousands separators in one field, two-digit cents in the next. */
    void money(String dollarsField, String centsField, Optional<String> amount) {
        amount.map(Fields::parseAmount).ifPresent(value -> {
            BigDecimal dollars = value.abs().setScale(0, RoundingMode.DOWN);
            String cents = value.abs().subtract(dollars).movePointRight(CENTS_SCALE)
                    .setScale(0, RoundingMode.UNNECESSARY).toPlainString();
            put(dollarsField, (value.signum() < 0 ? "-" : "") + thousands().format(dollars));
            put(centsField, cents.length() == 1 ? "0" + cents : cents);
        });
    }

    void check(String field, String onValue, boolean checked) {
        if (checked) {
            put(field, onValue);
        }
    }

    /** One character per field, for forms that draw a box per digit. */
    void perCharacter(List<String> fieldsInOrder, String value) {
        if (value.length() != fieldsInOrder.size()) {
            throw new ReturnPdfException(value.length() + " characters do not fit " + fieldsInOrder.size() + " boxes");
        }
        for (int i = 0; i < value.length(); i++) {
            put(fieldsInOrder.get(i), String.valueOf(value.charAt(i)));
        }
    }

    /** EIN prefix (2 digits) and serial (7 digits), the split every 94x form prints. */
    void ein(String prefixField, String serialField, String ein) {
        put(prefixField, ein.substring(0, 2));
        put(serialField, ein.substring(2));
    }

    Map<String, String> values() {
        return Map.copyOf(values);
    }

    private void put(String field, String value) {
        String previous = values.putIfAbsent(field, value);
        if (previous != null && !previous.equals(value)) {
            throw new ReturnPdfException("Field " + field + " would hold both " + previous + " and " + value);
        }
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw).setScale(CENTS_SCALE, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ReturnPdfException("Not a two-decimal amount: " + raw, e);
        }
    }

    private static DecimalFormat thousands() {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    }
}
