# Registering with SSA as a third-party W-2 submitter

Researched 2026-11-07 against SSA primary sources only. The new BSO authorization process took effect the same day, so re-check ssa.gov/bso before acting.

## Short answer

Clients do not have to grant Pyramos anything at SSA to file their W-2s today. The submitter registers personally, declares itself a third party, and self-attests. SSA mails an activation code to Pyramos's own IRS address, not to the clients. Clients need no BSO account.

As of 2026-11-07 SSA adds an optional, client-side control: a client's Responsible Entity Officer (REO) can register the client organization and delegate named third-party submitters, and can opt in to an Employer Signature PIN that must then appear in that client's RE record. Spring 2026 SSA FAQ: the new process is not mandatory this tax year.

## Process that ran through 2026-11-06 (Registration and Access to Services Handbook, bsoregug.pdf)

- Every BSO user registers personally. "YOU MAY NOT REGISTER ON BEHALF OF ANOTHER PERSON. Even if you are working on behalf of a client, register as yourself." (p. 6)
- Identity proofing is Login.gov or ID.me at NIST IAL2 (Summer 2025 FAQ, Q13).
- Add Employer Information page: "Check the Third-Party checkbox only if you are working on behalf of another business (EXAMPLE: Payroll company submitting wages for several different corporations. Use your own company's information, not the information for your client.)" (p. 17)
- Activation code is mailed to the employer address on file with IRS for the EIN entered, i.e. Pyramos's address. Usually within two weeks; SSA's navigate page says 15 to 20 days.
- "Do I need to register for a BSO credential if I have a 3rd party submitting my W2's? No." Employers may register anyway to view the third party's report (What's New 2026 FAQ).
- Summer 2025 FAQ Q7: each BSO user registers individually; a third party and an employer must not share a User ID.
- Summer 2025 FAQ Q13: SSA "employs a multifaceted approach to verify that third-party submitters are authorized... a combination of identity verification and authorization steps." No form, letter, or client consent is named anywhere in the public material.

## New authorization process, effective 2026-11-07 (ssa.gov/bso, pilot deck bsoAuthInfoSession.pdf, Spring 2026 meeting deck)

- Purpose: "provides employers with the ability to assign and delegate who can submit wage reports and do business on their behalf."
- Roles: REO (can execute binding agreements for the entity), EBA (Entity Business Administrator), ESA (Entity Service Administrator), Service User. "Delegated users may include third-party submitters if designated by the REO (or a delegated user with such authority)."
- Registration asks "Is Your Organization a Payroll Service Provider?" and "Attest as an REO and Accept the Declaration." One registration code per organization is mailed to the REO at the IRS address on file; pilot average 15 days.
- Employers that use a payroll service provider "will need their: Company name, EIN" (Spring 2026 deck, p. 7).
- Pilot stats: 25% of participants were payroll service providers; 70% chose to restrict access.
- "Once the new process takes effect, organizations using the existing process must re-register to access BSO Electronic Wage Reporting services unless they participated in the January 2026 limited release." Affected: Wage File Upload, AccuWage Online, Submission Status, Employer Report Status, W-2 Online, Name/SSN errors. SSNVS is unaffected.
- Spring 2026 FAQ: "Is the new authorization process mandatory this tax year? No, it is not mandatory for this tax year."

## Employer Signature PIN (employer-signature-pin.htm, Pub 42-007 TY2026 v2 2026-09-14)

- Optional for 2026. "If an employer opts in, then both they, and any third-party submitters submitting wage reports on the employer's behalf, will be required to include the Employer Signature PIN in the wage report for the Social Security Administration to process the wage report."
- Issued annually to the organization's administrator, who gives it to authorized submitters.
- RE record positions 319-328, format S-XXXXXX-Y, uppercase S, six digits, Luhn check digit, hyphens included; otherwise blanks. Section 4.2.6 has valid and invalid examples.

## What this means for Pyramos

1. Pyramos's REO (an officer who can bind the LLC) creates a Login.gov or ID.me credential, registers Pyramos Software LLC on ssa.gov/bso, answers yes to Payroll Service Provider, and waits about 15 days for the mailed registration code. Start now; BSO opens for TY2026 on 2026-12-08.
2. The REO then adds a second Pyramos user so the upload role does not sit with one person.
3. Ask each of the ten clients two things: whether they have registered an REO with SSA, and whether they opted in to the Employer Signature PIN. A client that did either must delegate Pyramos (company name and EIN) and hand over the PIN. A client that did neither needs nothing at SSA.
4. Add a nullable per-client Signature PIN to EmployerWage and write it at RE 319-328 with the Luhn check; blanks otherwise.
5. Confirm with the Employer Reporting Service Center (1-800-772-6270) that an unregistered client can still be filed by a registered third party under the new process, and the RE Agent Indicator reading for a Form 8655 agent.

## Sources

- https://www.ssa.gov/bso
- https://www.ssa.gov/employer/returnfilers.html
- https://www.ssa.gov/employer/navigate.htm
- https://www.ssa.gov/employer/bsoregug.pdf
- https://www.ssa.gov/employer/documents/howtoregister.pdf
- https://www.ssa.gov/employer/bsoAuthInfoSession.pdf
- https://www.ssa.gov/employer/reporter/meetingPresentationSpring2026.pdf
- https://www.ssa.gov/employer/reporter/faqSpring2026.pdf
- https://www.ssa.gov/employer/reporter/faqSummer2025.pdf
- https://www.ssa.gov/employer/employer-signature-pin.htm
- https://www.ssa.gov/employer/efw/26efw2.pdf
- https://www.ssa.gov/employer/w2news/index.htm
- https://www.ssa.gov/employer/suspended.htm
