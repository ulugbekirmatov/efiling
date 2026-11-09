-- Follow-up checks after the first AccuWage W-2 file (2026-11-02). Neither query returns a name, SSN or address.

-- 1. W-2s whose Box 3 is over the 2025 Social Security wage base ($176,100).
--    The checker flagged one: Box 3 $184,100, Box 6 $3,088 against about $216,000 of Medicare wages.
SELECT w.w2_form_id, w.employee_id, w.company_code,
       w.box3_ss_wages, w.box4_ss_tax, w.box5_medicare_wages, w.box6_medicare_tax
FROM onepay_w2_form w
WHERE w.is_active
  AND w.tax_year = 2025
  AND w.box3_ss_wages > 176100
ORDER BY w.w2_form_id;

-- 2. The 25 rows left out for a ZIP with fewer than 5 digits: is the ZIP missing, or did it lose a leading zero?
SELECT w.w2_form_id,
       employee_state.code                                                      AS employee_state,
       length(regexp_replace(COALESCE(e.zip_code::text, ''), '[^0-9]', '', 'g')) AS zip_digits,
       COALESCE(TRIM(e.street), '') = ''                                         AS street_missing,
       COALESCE(TRIM(e.city), '') = ''                                           AS city_missing
FROM onepay_w2_form w
LEFT JOIN hr_employee e             ON e.employee_id = w.employee_id
LEFT JOIN def_states employee_state ON employee_state.state_id = e.lkp_address_state
WHERE w.w2_form_id IN (4371, 4465, 4502, 4480, 4491, 4444, 4486, 4501, 4447, 4423, 4478, 4427, 4469,
                       4467, 4439, 4497, 4514, 4458, 4449, 4498, 4475, 4483, 4426, 4482, 4473)
ORDER BY w.w2_form_id;
