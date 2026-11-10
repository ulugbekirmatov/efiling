// mode: worked
// The EFW2 wage file format, one small AccuWage test file (01) played start to finish.
// Every number comes from efw2-format.run.log, read from the generated files in reports/accuwage-ty2025/.
/* global title, text, box, note, strip, arrow, legend, column, moveTo, bounds */
title('a wage file is a stack of 512-byte lines, read by position', 'one small test file (01), played start to finish')

// ── stage 1: the lines
text('h1', 0, 150, '1 · every line is 512 bytes, and its first two letters say what it is', { size: 'm', claim: 'problem-ask' })
box('ra', 0, 0, 640, null, 'RA · the submitter, Pyramos as reporting agent', { size: 's', claim: 'shape-order' })
box('re', 0, 0, 640, null, 'RE · one employer, Keystone Home Care in PA', { size: 's', color: 'violet', claim: 'shape-order' })
box('rw1', 0, 0, 640, null, 'RW · one W-2, Maria Smith', { size: 's', color: 'blue', claim: 'shape-order' })
box('rw2', 0, 0, 640, null, 'RW · one W-2, Robert Smith', { size: 's', color: 'blue', claim: 'shape-order' })
box('rw3', 0, 0, 640, null, 'RW · one W-2, Linda Smith', { size: 's', color: 'blue', claim: 'shape-order' })
box('rt', 0, 0, 640, null, 'RT · totals of this employer\'s RW lines', { size: 's', color: 'violet', claim: 'shape-order' })
box('rf', 0, 0, 640, null, 'RF · end of file, how many RW lines in all', { size: 's', claim: 'shape-order' })
column(['ra', 're', 'rw1', 'rw2', 'rw3', 'rt', 'rf'], { x: 160, y: 250, gap: 14 })
moveTo('re', 220, bounds('re').y)
moveTo('rt', 220, bounds('rt').y)
moveTo('rw1', 280, bounds('rw1').y)
moveTo('rw2', 280, bounds('rw2').y)
moveTo('rw3', 280, bounds('rw3').y)
text('c-01', 1060, 260, 'file 01 has exactly these 7 lines.\neach one is 512 bytes, then CR LF.\nno labels, no commas inside a line.', { size: 's', claims: ['value-01', 'how-length'] })
note('n-02', 1060, 470, 'a file for several clients repeats\nRE, its RW lines, RT once per\nemployer. file 02 does it 3 times.', { rot: -0.02, claim: 'shape-order' })

// ── stage 2: one W-2 in bytes
text('h2', 0, 900, '2 · one W-2 is one RW line, and each box sits at fixed byte positions', { size: 'm', claim: 'how-pad' })
// byte widths drawn to scale, 1400 px for 512 bytes
box('f-rw', 160, 1000, 5, 50, '', { color: 'blue', claim: 'value-layout' })
box('f-ssn', 166, 1000, 24, 50, '', { color: 'blue', claim: 'value-layout' })
box('f-name', 191, 1000, 136, 50, 'name 12-61', { size: 's', color: 'blue', claim: 'value-layout' })
box('f-addr', 328, 1000, 343, 50, 'address and more 62-187', { size: 's', color: 'blue', claim: 'value-layout' })
box('f-b1', 672, 1000, 29, 50, '', { color: 'green', claims: ['value-layout', 'value-box1'] })
box('f-b2', 702, 1000, 29, 50, '', { color: 'blue', claim: 'value-layout' })
box('f-b36', 732, 1000, 119, 50, '', { color: 'blue', claim: 'value-layout' })
text('l-b36', 800, 1070, 'boxes 3-6 210-253', { size: 's', color: 'blue', of: 'f-b36' })
box('f-rest', 852, 1000, 708, 50, 'the rest, up to byte 512', { size: 's', color: 'blue', claim: 'value-layout' })
text('l-rw', 120, 1070, 'RW 1-2', { size: 's', color: 'blue', of: 'f-rw' })
text('l-ssn', 160, 1110, 'SSN 3-11', { size: 's', color: 'blue', of: 'f-ssn' })
text('l-b1', 590, 1070, 'box 1 188-198', { size: 's', color: 'green', of: 'f-b1' })
text('l-b2', 690, 1110, 'box 2 199-209', { size: 's', color: 'blue', of: 'f-b2' })
text('l-full', 160, 950, 'Maria\'s RW line, 512 bytes, drawn to scale', { size: 's', color: 'blue', claims: ['value-layout', 'how-length'] })
text('d1', 160, 1230, '52000.00 dollars', { size: 'm', font: 'mono', claim: 'value-box1' })
text('d2', 640, 1230, '5200000 cents', { size: 'm', font: 'mono', claim: 'value-box1' })
text('d3', 1240, 1230, '00005200000', { size: 'm', font: 'mono', color: 'green', claims: ['value-box1', 'how-pad'] })
arrow('d1', 'd2', '× 100')
arrow('d2', 'd3', 'zeros on the left to 11 bytes')
text('c-pad', 160, 1320, 'text gets spaces on the right, money gets zeros on the left. there is no decimal point.\nwhole cents never round, so a total can match its lines to the cent (inferred).', { size: 's', claims: ['how-pad', 'why-cents'] })
note('n-shift', 1640, 1000, 'one byte too many or too few\nand every field after it moves.\na name lands where money\nshould be. so SSA says every\nline must be exactly 512 bytes.', { color: 'orange', rot: 0.02, claim: 'why-position' })
text('code', 160, 1420, 'in the code: zeroFill pads the money, RECORD_LENGTH = 512 (FixedWidth.java)', { size: 's', color: 'grey', claims: ['how-pad', 'how-length'] })

// ── stage 3: totals
text('h3', 0, 1560, '3 · RT adds up the employer\'s RW lines, RF counts every RW line', { size: 'm', claim: 'how-total' })
strip('sum', 160, 1660, [
  { amount: 52000.00, label: 'Maria', color: 'blue' },
  { amount: 38500.50, label: 'Robert', color: 'blue' },
  { amount: 71250.00, label: 'Linda', color: 'blue' }
], { of: 161750.50, width: 1400, color: 'green', label: 'box 1 wages of the three RW lines, added up', claim: 'value-sum' })
text('t-rt', 160, 1780, 'RT 10-24  = 000000016175050   161750.50, 15 bytes wide', { size: 's', font: 'mono', color: 'green', claims: ['value-sum', 'how-total'] })
text('t-cnt', 160, 1830, 'RT 3-9    = 0000003           RW lines for this employer\nRF 8-16   = 000000003         RW lines in the whole file', { size: 's', font: 'mono', claim: 'value-sum' })
text('c-why', 160, 1950, 'SSA checks each RT against the RW lines since the last RE. a mismatch is out of balance.', { size: 's', claim: 'why-total' })

// ── stage 4: the broken files
text('h4', 0, 2060, '4 · each broken test file breaks one of these rules on purpose', { size: 'm', claim: 'value-broken' })
box('b94', 160, 2160, 440, null, 'file 94\nRT says 321500.25, the RW lines\nadd to 321400.25. 100.00 off', { geo: 'octagon', size: 's', color: 'red', claims: ['edge-94', 'value-broken'] })
box('b95', 680, 2160, 440, null, 'file 95\nRF says 4,\nthe file has 3 RW lines', { geo: 'octagon', size: 's', color: 'red', claims: ['edge-95', 'value-broken'] })
box('b97', 1200, 2160, 440, null, 'file 97\none RW line is 511 bytes,\ntesting stops right there', { geo: 'octagon', size: 's', color: 'red', claims: ['edge-97', 'value-broken'] })
note('n-94', 1720, 2160, 'file 94 has its own three\nemployees, not the ones\nfrom file 01', { rot: 0.02, claim: 'value-broken' })
legend({ black: 'file level (RA, RF)', violet: 'employer level (RE, RT)', blue: 'one W-2 (RW)', green: 'money as written, matching', red: 'what a broken file breaks', orange: 'warning' }, 1720, 250)
