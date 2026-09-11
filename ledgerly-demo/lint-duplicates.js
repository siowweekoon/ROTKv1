#!/usr/bin/env node
// Duplicate-logic lint for dashboard.html's forecasting/KPI code, requested by
// Su Ann after the round-6/7 audit (2026-09-12): the same "computed in more
// than one place, no shared source" pattern caused two real bugs in this
// build (the Gig Payroll KPI vs Roster tab disagreement at v21.0, and the
// projected-low-point disagreement across the KPI card, Recommended Actions,
// and Mobile Preview at v40-v42). This won't catch everything -- it flags two
// specific shapes: repeated "magic number" thresholds, and repeated
// filter/reduce chains -- but it's exactly the two shapes that actually bit
// this codebase, and it's meant to slow down copy-paste, not replace review.
//
// Usage: node lint-duplicates.js [path-to-html-file]

const fs = require('fs');
const path = require('path');

const target = process.argv[2] || path.join(__dirname, 'dashboard.html');
const html = fs.readFileSync(target, 'utf-8');
const scriptMatch = html.match(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/);
if (!scriptMatch) {
  console.error('No inline <script> block found in', target);
  process.exit(1);
}
let js = scriptMatch[1];
const scriptStartLine = html.slice(0, scriptMatch.index).split('\n').length;

// Strip known large sample-data array literals so their contents don't
// pollute the numeric-literal scan (weeks13.retail = [8200,8400,...], etc.
// are real sample data, not thresholds). Anything between a top-level
// `const NAME = [` ... `];` or `const NAME = {` ... `};` spanning the known
// data blocks is blanked out (preserving line count so line numbers stay
// accurate for the remaining findings).
const dataBlockNames = ['weeks13', 'rosterRows', 'arRows', 'agingRows', 'drivers'];
function blankDataBlocks(src) {
  let out = src;
  for (const name of dataBlockNames) {
    const re = new RegExp(`const ${name}\\s*=\\s*[\\[{][\\s\\S]*?\\n[\\]}];`, 'g');
    out = out.replace(re, (m) => m.replace(/[^\n]/g, ' '));
  }
  return out;
}
const scanned = blankDataBlocks(js);

function lineAt(index) {
  return scriptStartLine + scanned.slice(0, index).split('\n').length - 1;
}

const findings = [];

// --- Check 1: repeated "magic number" thresholds ---------------------------
// Numeric literals with 3+ digits (filters out trivial small indices/counts
// like week numbers, array lengths) used either in a comparison (< > <= >=)
// or as a bare `const X = N;` assignment. Grouped by value; any value used
// at 2+ distinct source locations is flagged.
const thresholdRe = /(?:[<>]=?\s*(\d{3,})\b)|(?:=\s*(\d{3,})\s*;)/g;
const byValue = new Map();
let m;
while ((m = thresholdRe.exec(scanned)) !== null) {
  const value = m[1] || m[2];
  const line = lineAt(m.index);
  if (!byValue.has(value)) byValue.set(value, []);
  byValue.get(value).push(line);
}
for (const [value, lines] of byValue.entries()) {
  const uniqueLines = [...new Set(lines)];
  if (uniqueLines.length > 1) {
    findings.push({
      type: 'duplicate-threshold',
      detail: `Literal ${value} used as a threshold/assignment in ${uniqueLines.length} places`,
      lines: uniqueLines,
    });
  }
}

// --- Check 2: repeated filter/reduce chains ---------------------------------
// Normalizes whitespace so cosmetic differences (arrow-fn spacing, etc.)
// don't hide a real duplicate. Flags any .filter(...).reduce(...) chain
// whose normalized text appears more than once.
// [^)]* would stop at the FIRST close-paren, which breaks on a filter
// callback containing its own nested call (e.g. `.has(r[0])`) -- bounded
// lazy matching tolerates one level of nesting instead.
const chainRe = /\w+(?:\.\w+)*\.filter\([\s\S]{0,80}?\)\.reduce\([\s\S]{0,80}?\)/g;
const byChain = new Map();
while ((m = chainRe.exec(scanned)) !== null) {
  const normalized = m[0].replace(/\s+/g, '').replace(/\bexcludedDriverIds\.has\(r\[0\]\)/g, 'EXCL(r)');
  const line = lineAt(m.index);
  if (!byChain.has(normalized)) byChain.set(normalized, []);
  byChain.get(normalized).push(line);
}
for (const [chain, lines] of byChain.entries()) {
  const uniqueLines = [...new Set(lines)];
  if (uniqueLines.length > 1) {
    findings.push({
      type: 'duplicate-filter-reduce-chain',
      detail: `Same filter/reduce chain repeated in ${uniqueLines.length} places: ${chain.slice(0, 70)}...`,
      lines: uniqueLines,
    });
  }
}

if (findings.length === 0) {
  console.log(`clean: no duplicate thresholds or filter/reduce chains found in ${path.basename(target)}`);
  process.exit(0);
} else {
  console.log(`${findings.length} duplicate pattern(s) found in ${path.basename(target)}:\n`);
  findings.forEach(f => {
    console.log(`[${f.type}] ${f.detail}`);
    console.log(`  lines: ${f.lines.join(', ')}\n`);
  });
  process.exit(1);
}
