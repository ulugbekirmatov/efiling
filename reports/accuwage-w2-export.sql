-- One row per active W-2 for the tax year, with every field the EFW2 generator needs:
-- the RE record (employer) and the RW record (employee, Boxes 1-6, 12, 13).
-- Change the tax year in the params CTE. Run it on the tenant schema, then export the result as CSV with headers.
WITH params AS (
    SELECT 2025 AS tax_year
)
SELECT
    -- Employer (RE record)
    lc.legal_company_id,
    lc.legal_name                                                    AS employer_name,
    COALESCE(NULLIF(TRIM(w.box_b_employer_ein), ''), lc.fein_number) AS employer_ein,
    lc.addr_line1                                                    AS employer_address_line1,
    lc.addr_line2                                                    AS employer_address_line2,
    lc.city                                                          AS employer_city,
    employer_state.code                                              AS employer_state,
    lc.postal_code                                                   AS employer_zip,
    lc.phone_number                                                  AS employer_phone,
    lc.contact_email                                                 AS employer_email,
    w.company_code,

    -- Employee (RW record)
    w.w2_form_id,
    w.employee_id,
    COALESCE(NULLIF(TRIM(w.ssn), ''), e.government_no)               AS ssn,
    regexp_replace(COALESCE(w.ssn, ''), '[^0-9]', '', 'g')
        = regexp_replace(COALESCE(e.government_no, ''), '[^0-9]', '', 'g') AS ssn_matches_hr,
    e.first_name,
    e.middle_name,
    e.last_name,
    w.box_e_first_middle                                             AS w2_box_e_first_middle,
    w.box_e_last_name                                                AS w2_box_e_last_name,
    e.street                                                         AS employee_street,
    e.city                                                           AS employee_city,
    employee_state.code                                              AS employee_state,
    e.zip_code                                                       AS employee_zip,
    w.box_f_employee_address                                         AS w2_box_f_address,

    -- Amounts in dollars
    w.box1_wages,
    w.box2_federal_tax,
    w.box3_ss_wages,
    w.box4_ss_tax,
    w.box5_medicare_wages,
    w.box6_medicare_tax,
    w.box12a_code, w.box12a_amount,
    w.box12b_code, w.box12b_amount,
    w.box12c_code, w.box12c_amount,
    w.box12d_code, w.box12d_amount,
    w.box12_codes,
    w.box13_retirement_plan,
    w.box14_other,
    w.tax_year
FROM onepay_w2_form w
CROSS JOIN params p
LEFT JOIN hr_company hc                    ON UPPER(TRIM(hc.code)) = UPPER(TRIM(w.company_code))
LEFT JOIN onepay_legal_company lc          ON lc.legal_company_id = hc.legal_company_id
LEFT JOIN def_states employer_state        ON employer_state.state_id = lc.state_code
LEFT JOIN hr_employee e                    ON e.employee_id = w.employee_id
LEFT JOIN def_states employee_state        ON employee_state.state_id = e.lkp_address_state
WHERE w.is_active
  AND w.tax_year = p.tax_year
ORDER BY lc.legal_name, e.last_name, e.first_name, w.w2_form_id;
