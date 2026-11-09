// Answer to: "what is this bytes format in step 2?"
// Real characters of Maria's RW line, file 01 (reports/accuwage-ty2025/01-single-employer.txt, line 3). SSN masked.
/* global title, text, box, note, arrow, legend */

// a ruler line: put each position number at its own column so it sits over that character
function ruler(first, marks) {
  let s = ''
  for (const p of marks) s = s.padEnd(p - first) + p
  return s
}

title('the "bytes" in step 2 are plain text characters', 'a byte = one ASCII character; positions count from 1 at the left of the line')

text('h-a', 0, 150, 'bytes 1-61 of Maria\'s line, as they really are in the file', { size: 'm' })
box('slice-a', 40, 220, 900, 120, '', { color: 'blue' })
text('r-a', 60, 235, ruler(1, [1, 3, 12, 27, 42, 61]), { size: 's', font: 'mono', color: 'grey' })
text('v-a', 60, 285, ('RW#########' + 'MARIA'.padEnd(30) + 'SMITH'.padEnd(20)).replace(/ /g, '·'), { size: 's', font: 'mono', color: 'blue' })
text('c-a', 980, 225, 'RW = record type (1-2)\n######### = SSN (3-11), hidden here\nMARIA = first name, · = a space byte, fills to 26\nmiddle name 27-41 is empty, all spaces\nSMITH = last name 42-61', { size: 's', color: 'blue' })

text('h-b', 0, 420, 'bytes 188-209: box 1 and box 2', { size: 'm' })
box('slice-b', 40, 490, 500, 120, '', { color: 'green' })
text('r-b', 60, 505, ruler(188, [188, 199, 209]), { size: 's', font: 'mono', color: 'grey' })
text('v-b', 60, 555, '0000520000000000421200', { size: 's', font: 'mono', color: 'green' })
text('c-b', 980, 495, 'box 1: 00005200000 = 5200000 cents = 52000.00\nbox 2: 00000421200 = 421200 cents = 4212.00\nthe two fields touch: nothing separates them,\nonly the position says where one stops', { size: 's', color: 'green' })
arrow('slice-b', 'c-b', 'read by position')

text('h-c', 0, 690, 'what the computer stores, one byte per character (hex)', { size: 'm' })
text('hex', 60, 760, 'R  W     0  0  0  0  5  2  0  0  0  0  0\n52 57    30 30 30 30 35 32 30 30 30 30 30', { size: 's', font: 'mono' })
text('end', 60, 880, 'line end: …two spaces at 511-512, then 0D 0A (CR LF), not counted in the 512', { size: 's', font: 'mono' })

note('n-why', 980, 740, 'so "512 bytes" just means 512\ncharacters. the file is ASCII\ntext you can open in any editor;\nthe colored bar in step 2 is\nthis line drawn to scale.', { color: 'yellow', rot: -0.02 })

legend({ blue: 'name area of the RW line', green: 'money fields', grey: 'position numbers (not in the file)' }, 1500, 225)
