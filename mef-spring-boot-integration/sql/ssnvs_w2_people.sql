SELECT x.*
/*!*/FROM (
WITH w AS (
  SELECT
    f.w2_form_id,
    f.employee_id,
    f.company_code,
    lc.legal_company_id,
    lc.is_active AS legal_company_is_active,
    NULLIF(regexp_replace(COALESCE(f.ssn, ''), '[^0-9]', '', 'g'), '') AS ssn_digits,
    NULLIF(regexp_replace(COALESCE(hre.government_no, ''), '[^0-9]', '', 'g'), '') AS hr_ssn_digits,
    NULLIF(regexp_replace(COALESCE(f.box_b_employer_ein, ''), '[^0-9]', '', 'g'), '') AS w2_ein_digits,
    NULLIF(regexp_replace(COALESCE(lc.fein_number, ''), '[^0-9]', '', 'g'), '') AS legal_ein_digits,
    NULLIF(TRIM(f.box_e_first_middle), '') AS box_e_first_middle,
    NULLIF(TRIM(f.box_e_last_name), '') AS box_e_last_name,
    NULLIF(TRIM(hre.first_name), '') AS hr_first_name,
    NULLIF(TRIM(hre.middle_name), '') AS hr_middle_name,
    NULLIF(TRIM(hre.middle_initial), '') AS hr_middle_initial,
    NULLIF(TRIM(hre.last_name), '') AS hr_last_name,
    hre.birth_dt AS hr_birth_dt,
    NULLIF(TRIM(adp.first_name), '') AS adp_first_name,
    NULLIF(TRIM(adp.middle_name), '') AS adp_middle_name,
    NULLIF(TRIM(adp.last_name), '') AS adp_last_name,
    adp.birth_dt AS adp_birth_dt
  FROM onepay_w2_form f
  LEFT JOIN hr_company hc
    ON UPPER(TRIM(hc.code)) = UPPER(TRIM(f.company_code))
  JOIN onepay_legal_company lc
    ON lc.legal_company_id = '${req.xlegal_company_id}'::int
  LEFT JOIN hr_employee hre
    ON hre.employee_id = f.employee_id
  LEFT JOIN adp_employee adp
    ON adp.employee_id = f.employee_id
  WHERE f.is_active = true
    AND f.tax_year = '${req.xtax_year}'::int
    AND (
      hc.legal_company_id = lc.legal_company_id
      OR NULLIF(regexp_replace(COALESCE(f.box_b_employer_ein, ''), '[^0-9]', '', 'g'), '')
         = NULLIF(regexp_replace(COALESCE(lc.fein_number, ''), '[^0-9]', '', 'g'), '')
    )
)
SELECT
  w.legal_company_id,
  MAX(w.legal_ein_digits) AS employer_ein,
  bool_or(w.legal_company_is_active) AS legal_company_is_active,
  '${req.xtax_year}'::int AS tax_year,
  w.employee_id,
  COUNT(DISTINCT w.w2_form_id) AS active_w2_count,
  string_agg(DISTINCT w.w2_form_id::text, ',') AS w2_form_ids,
  string_agg(DISTINCT w.company_code, ',') AS company_codes,
  CASE
    WHEN COUNT(DISTINCT w.w2_form_id) FILTER (WHERE w.w2_ein_digits IS NOT NULL) = COUNT(DISTINCT w.w2_form_id)
     AND COUNT(DISTINCT w.w2_ein_digits) = 1
    THEN MAX(w.w2_ein_digits)
  END AS w2_box_b_ein,
  CASE
    WHEN COUNT(DISTINCT w.w2_form_id) FILTER (WHERE w.ssn_digits IS NOT NULL) = COUNT(DISTINCT w.w2_form_id)
     AND COUNT(DISTINCT w.ssn_digits) = 1
    THEN MAX(w.ssn_digits)
  END AS ssn,
  CASE
    WHEN COUNT(DISTINCT w.w2_form_id) FILTER (WHERE w.hr_ssn_digits IS NOT NULL) = COUNT(DISTINCT w.w2_form_id)
     AND COUNT(DISTINCT w.hr_ssn_digits) = 1
    THEN MAX(w.hr_ssn_digits)
  END AS hr_ssn_digits,
  CASE
    WHEN COUNT(DISTINCT w.w2_form_id) FILTER (WHERE w.box_e_first_middle IS NOT NULL) = COUNT(DISTINCT w.w2_form_id)
     AND COUNT(DISTINCT upper(regexp_replace(trim(w.box_e_first_middle), '\s+', ' ', 'g'))) = 1
    THEN MAX(upper(regexp_replace(trim(w.box_e_first_middle), '\s+', ' ', 'g')))
  END AS w2_box_e_first_middle,
  CASE
    WHEN COUNT(DISTINCT w.w2_form_id) FILTER (WHERE w.box_e_last_name IS NOT NULL) = COUNT(DISTINCT w.w2_form_id)
     AND COUNT(DISTINCT upper(regexp_replace(trim(w.box_e_last_name), '\s+', ' ', 'g'))) = 1
    THEN MAX(upper(regexp_replace(trim(w.box_e_last_name), '\s+', ' ', 'g')))
  END AS w2_box_e_last_name,
  MAX(w.hr_first_name) AS hr_first_name,
  MAX(w.hr_middle_name) AS hr_middle_name,
  MAX(w.hr_middle_initial) AS hr_middle_initial,
  MAX(w.hr_last_name) AS hr_last_name,
  MAX(w.adp_first_name) AS adp_first_name,
  MAX(w.adp_middle_name) AS adp_middle_name,
  MAX(w.adp_last_name) AS adp_last_name,
  MAX(w.hr_birth_dt) AS hr_birth_dt,
  MAX(w.adp_birth_dt) AS adp_birth_dt
FROM w
GROUP BY w.employee_id, w.legal_company_id
) x
/*!*/WHERE

/*!*/GROUP BY

/*!*/ORDER BY x.employee_id
