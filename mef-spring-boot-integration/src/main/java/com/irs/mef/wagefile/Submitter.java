package com.irs.mef.wagefile;

/** RA submitter. The BSO user id is exactly 8 characters and is not read from the environment. */
public record Submitter(
        String ein,
        String bsoUserId,
        String companyName,
        String locationAddress,
        String deliveryAddress,
        String city,
        String state,
        String zip,
        String submitterName,
        String contactEmail) {

    public Submitter {
        ein = FieldRules.digits(ein, 9, "submitter EIN");
        if (bsoUserId == null || bsoUserId.length() != 8) {
            throw new IllegalArgumentException("BSO user id must be 8 characters");
        }
        FieldRules.ascii(bsoUserId, "BSO user id");
        companyName = FieldRules.bounded(companyName, 57, "company name");
        locationAddress = FieldRules.bounded(locationAddress, 22, "location address");
        deliveryAddress = FieldRules.required(deliveryAddress, 22, "delivery address");
        city = FieldRules.required(city, 22, "city");
        state = FieldRules.state(state);
        zip = FieldRules.digits(zip, 5, "ZIP");
        submitterName = FieldRules.bounded(submitterName, 57, "submitter name");
        contactEmail = FieldRules.email(contactEmail);
    }
}
