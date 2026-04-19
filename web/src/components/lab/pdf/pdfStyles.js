// Exported as a string so we can inject it via <style> inside the template's
// root container (id="lab-report-root"). Scoping to that id keeps AntD's
// global resets out — the template must not be styled by the host page.
//
// Spec Section 7.2:
//   - Body: Times New Roman 12 pt
//   - Tables / code blocks: Arial 10 pt
//   - A4 page, 20 mm margins, mostly monochrome

export const PDF_STYLES = `
#lab-report-root {
  font-family: "Times New Roman", "Liberation Serif", Times, serif;
  font-size: 12pt;
  line-height: 1.45;
  color: #000;
  background: #fff;
  width: 170mm;           /* 210mm A4 − 2×20mm margin */
  margin: 0 auto;
  padding: 0;
}

#lab-report-root .page {
  page-break-after: always;
  padding: 20mm;
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
}

#lab-report-root .page:last-child {
  page-break-after: auto;
}

#lab-report-root h1 {
  font-size: 18pt;
  margin: 0 0 8mm;
  font-weight: 700;
}

#lab-report-root h2 {
  font-size: 14pt;
  margin: 6mm 0 3mm;
  font-weight: 700;
  border-bottom: 1pt solid #000;
  padding-bottom: 1mm;
}

#lab-report-root h3 {
  font-size: 12pt;
  margin: 4mm 0 2mm;
  font-weight: 700;
}

#lab-report-root p,
#lab-report-root li {
  margin: 0 0 2mm;
  text-align: justify;
}

/* Tables and code → Arial 10pt per spec. */
#lab-report-root table,
#lab-report-root pre,
#lab-report-root code {
  font-family: Arial, "Liberation Sans", Helvetica, sans-serif;
  font-size: 10pt;
}

#lab-report-root table {
  width: 100%;
  border-collapse: collapse;
  margin: 2mm 0 4mm;
  table-layout: fixed;
  word-wrap: break-word;
}

#lab-report-root th,
#lab-report-root td {
  border: 0.5pt solid #000;
  padding: 1.5mm 2mm;
  vertical-align: top;
  text-align: left;
}

#lab-report-root th {
  background: #eee;
  font-weight: 700;
}

#lab-report-root pre {
  border: 0.5pt solid #888;
  padding: 2mm;
  background: #fafafa;
  white-space: pre-wrap;
  word-break: break-all;
}

#lab-report-root .cover {
  text-align: center;
  padding-top: 30mm;
}

#lab-report-root .cover-logo {
  width: 28mm;
  height: auto;
  margin: 0 auto 6mm;
  display: block;
}

#lab-report-root .cover-meta {
  margin-top: 20mm;
  text-align: left;
  display: inline-block;
  min-width: 100mm;
}

#lab-report-root .tick {
  color: #2c7a2c;
  font-weight: 700;
}
#lab-report-root .cross {
  color: #a33;
  font-weight: 700;
}

#lab-report-root .declaration-sig {
  margin-top: 30mm;
  display: flex;
  justify-content: flex-end;
}

#lab-report-root .declaration-sig .sig-box {
  text-align: center;
  width: 70mm;
}

#lab-report-root .declaration-sig .sig-line {
  margin: 18mm auto 2mm;
  border-top: 0.5pt solid #000;
  width: 60mm;
}

#lab-report-root .post-image {
  max-width: 150mm;
  max-height: 90mm;
  display: block;
  margin: 2mm 0;
  border: 0.5pt solid #888;
}

#lab-report-root .evidence-sample {
  max-width: 80mm;
  max-height: 50mm;
  border: 0.5pt solid #888;
  margin: 1mm;
}

#lab-report-root .muted {
  color: #555;
}

@media print {
  body { margin: 0; }
  #lab-report-root { margin: 0; }
}
`
