package com.irs.mef.returnpdf;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Filer(
        String ein,
        String name,
        Optional<String> tradeName,
        String street,
        String city,
        Optional<String> state,
        Optional<String> postalCode,
        Optional<String> country,
        Optional<String> province) {

    static Filer read(ReturnXml.Node header) {
        header.notPrinted("Filer/BusinessNameControlTxt", "IRS name-matching key, not a box on any 94x form");
        String ein = header.text("Filer/EIN").orElseThrow(() -> new ReturnPdfException("Filer has no EIN"));
        String name = nameLines(header, "Filer/BusinessName")
                .orElseThrow(() -> new ReturnPdfException("Filer has no BusinessName"));
        Optional<String> tradeName = nameLines(header, "Filer/TradeName");
        Optional<String> inCareOf = header.text("Filer/InCareOfNm").map(careOf -> "C/O " + careOf);
        if (header.child("Filer").flatMap(filer -> filer.child("USAddress")).isPresent()) {
            return new Filer(ein, name, tradeName,
                    street(inCareOf, header, "Filer/USAddress"),
                    header.text("Filer/USAddress/CityNm").orElse(""),
                    header.text("Filer/USAddress/StateAbbreviationCd"),
                    header.text("Filer/USAddress/ZIPCd"),
                    Optional.empty(),
                    Optional.empty());
        }
        return new Filer(ein, name, tradeName,
                street(inCareOf, header, "Filer/ForeignAddress"),
                header.text("Filer/ForeignAddress/CityNm").orElse(""),
                Optional.empty(),
                header.text("Filer/ForeignAddress/ForeignPostalCd"),
                header.text("Filer/ForeignAddress/CountryCd"),
                header.text("Filer/ForeignAddress/ProvinceOrStateNm"));
    }

    private static Optional<String> nameLines(ReturnXml.Node header, String path) {
        String joined = Stream.of(
                        header.text(path + "/BusinessNameLine1Txt"),
                        header.text(path + "/BusinessNameLine2Txt"))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(" "));
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    private static String street(Optional<String> inCareOf, ReturnXml.Node header, String addressPath) {
        return Stream.of(
                        inCareOf,
                        header.text(addressPath + "/AddressLine1Txt"),
                        header.text(addressPath + "/AddressLine2Txt"))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));
    }
}
