#!/bin/sh
# =============================================================================
# inject.sh — OWASP Dependency Check XML → Human-Readable HTML Report
# =============================================================================
set -e

XML_FILE="dependency-check-report.xml"
OUTPUT="owasp_report.html"

if [ ! -f "$XML_FILE" ]; then
  echo "❌  $XML_FILE not found in $(pwd)"
  exit 1
fi

GENERATED_AT=$(date "+%d %b %Y %H:%M:%S")

# Write the Python parser to a temp file to avoid heredoc quoting issues
cat > _owasp_parse.py << 'PYEOF'
import sys, re, html as H, datetime
import xml.etree.ElementTree as ET

XML_FILE     = sys.argv[1]
GENERATED_AT = sys.argv[2]

# ── Parse XML keeping namespaces intact ──────────────────────────────────────
tree = ET.parse(XML_FILE)
root = tree.getroot()

# ── Helper: local tag name (strips Clark-notation namespace) ─────────────────
def loc(el):
    return re.sub(r'\{[^}]+\}', '', el.tag)

# ── Namespace-agnostic find helpers ──────────────────────────────────────────
def find_local(parent, *local_names):
    """Return first descendant matching the local-name path, or None."""
    if not local_names:
        return parent
    target = local_names[0]
    for child in (parent if parent is not None else []):
        if loc(child) == target:
            if len(local_names) == 1:
                return child
            r = find_local(child, *local_names[1:])
            if r is not None:
                return r
    return None

def findall_local(parent, *local_names):
    """Return all descendants matching the local-name path."""
    if not local_names:
        return [parent] if parent is not None else []
    target = local_names[0]
    results = []
    for child in (parent if parent is not None else []):
        if loc(child) == target:
            if len(local_names) == 1:
                results.append(child)
            else:
                results.extend(findall_local(child, *local_names[1:]))
    return results

def txt(parent, *local_path, default='N/A'):
    el = find_local(parent, *local_path) if local_path else parent
    if el is not None and el.text and el.text.strip():
        return H.escape(el.text.strip())
    return default

def attr(el, name, default=''):
    if el is None:
        return default
    v = el.get(name)
    if v is None:
        # attribute might carry namespace too
        for k, val in el.attrib.items():
            if re.sub(r'\{[^}]+\}', '', k) == name:
                return H.escape(val.strip()) if val.strip() else default
    return H.escape(v.strip()) if v and v.strip() else default

# ── Project info ──────────────────────────────────────────────────────────────
proj_info   = find_local(root, 'projectInfo')
proj_name   = txt(proj_info, 'name',         default='N/A')
dc_version  = txt(proj_info, 'reportSchema', default='')
report_date = txt(proj_info, 'reportDate',   default=GENERATED_AT)

try:
    dt = datetime.datetime.fromisoformat(report_date.replace('Z', '+00:00'))
    report_date = dt.strftime('%d %b %Y %H:%M:%S UTC')
except Exception:
    pass

# ── Dependency list ───────────────────────────────────────────────────────────
deps       = findall_local(root, 'dependencies', 'dependency')
total_deps = len(deps)
vuln_deps  = 0
vuln_count = 0
suppressed = 0

SEV_ORDER = {'CRITICAL': 0, 'HIGH': 1, 'MEDIUM': 2, 'LOW': 3, 'INFO': 4, 'NONE': 5}

def badge(sev):
    s = (sev or 'NONE').upper()
    cls = {'CRITICAL':'badge-critical','HIGH':'badge-high','MEDIUM':'badge-medium',
           'LOW':'badge-low','INFO':'badge-info'}.get(s, 'badge-none')
    return '<span class="badge ' + cls + '">' + H.escape(sev or 'NONE') + '</span>'

vuln_rows_html = []
all_dep_cards  = []

for dep in deps:
    file_name = txt(dep, 'fileName',    default='(unknown)')
    file_path = txt(dep, 'filePath',    default='')
    md5       = txt(dep, 'md5',         default='')
    sha1      = txt(dep, 'sha1',        default='')
    sha256    = txt(dep, 'sha256',      default='')
    license_  = txt(dep, 'license',     default='')

    vulns     = findall_local(dep, 'vulnerabilities', 'vulnerability')
    sup_vulns = findall_local(dep, 'suppressedVulnerabilities', 'vulnerability')
    suppressed += len(sup_vulns)

    # ── Evidence table ────────────────────────────────────────────────────────
    evidences     = findall_local(dep, 'evidenceCollected', 'evidence')
    ev_rows_list  = []
    for ev in evidences:
        ev_type = attr(ev, 'type', default=txt(ev, 'type', default=''))
        ev_conf = attr(ev, 'confidence', default='')
        ev_src  = txt(ev, 'source', default='')
        ev_name = txt(ev, 'name',   default='')
        ev_val  = txt(ev, 'value',  default='')
        row = ('<tr>'
               '<td>' + ev_type + '</td>'
               '<td>' + ev_src  + '</td>'
               '<td>' + ev_name + '</td>'
               '<td>' + ev_val  + '</td>'
               '<td>' + ev_conf + '</td>'
               '</tr>')
        ev_rows_list.append(row)

    ev_tbody = ''.join(ev_rows_list)
    if ev_tbody:
        ev_table = ('<table class="evidence-table">'
                    '<thead><tr>'
                    '<th>Type</th>'
                    '<th>Source</th>'
                    '<th>Name</th>'
                    '<th>Value</th>'
                    '<th>Confidence</th>'
                    '</tr></thead>'
                    '<tbody>' + ev_tbody + '</tbody></table>')
    else:
        ev_table = '<p class="no-evidence">No evidence collected</p>'

    # ── Identifiers ───────────────────────────────────────────────────────────
    id_nodes = findall_local(dep, 'identifiers', 'identifier')
    # Also try packages/identifier (newer DC format)
    if not id_nodes:
        id_nodes = findall_local(dep, 'identifiers', 'packages', 'identifier')

    id_parts = []
    for idf in id_nodes:
        id_name = txt(idf, 'name', default='')
        # newer DC uses <id>pkg:...</id>
        if id_name == 'N/A' or not id_name:
            id_name = txt(idf, 'id', default='')
        id_url  = txt(idf, 'url',  default='')
        if not id_name or id_name == 'N/A':
            continue
        if id_url and id_url != 'N/A':
            id_parts.append('<a class="cve-tag verified" href="' + id_url + '" target="_blank">' + id_name + '</a>')
        else:
            id_parts.append('<span class="cve-tag unverified">' + id_name + '</span>')
    id_html = ('<div class="cve-list">' + ''.join(id_parts) + '</div>'
               if id_parts else '<span class="empty-val">None</span>')

    # ── Hash display ──────────────────────────────────────────────────────────
    def hash_span(val):
        if val and val != 'N/A':
            return '<span class="hash-val">' + val + '</span>'
        return '<span class="empty-val">—</span>'

    md5_disp    = hash_span(md5)
    sha1_disp   = hash_span(sha1)
    sha256_disp = hash_span(sha256[:40] + '…' if sha256 and sha256 != 'N/A' and len(sha256) > 40 else sha256)

    # ── Vulnerable section entry ──────────────────────────────────────────────
    has_vulns = len(vulns) > 0
    if has_vulns:
        vuln_deps  += 1
        highest_sev = 'NONE'
        cve_parts   = []
        inner_rows  = []

        for v in vulns:
            vuln_count += 1
            name     = txt(v, 'name',        default='N/A')
            severity = txt(v, 'severity',    default='UNKNOWN')
            cvss2    = txt(v, 'cvssScore',   default='')
            cvss3_el = find_local(v, 'cvssV3')
            cvss3    = txt(cvss3_el, 'baseScore', default='') if cvss3_el is not None else ''
            score    = cvss3 or cvss2 or '–'
            desc     = txt(v, 'description', default='No description available.')
            if len(desc) > 320:
                desc = desc[:320] + '…'

            if name.startswith('CVE-'):
                cve_parts.append('<a class="cve-tag" href="https://nvd.nist.gov/vuln/detail/'
                                 + name + '" target="_blank">' + name + '</a>')
            else:
                cve_parts.append('<span class="cve-tag">' + name + '</span>')

            if SEV_ORDER.get(severity.upper(), 5) < SEV_ORDER.get(highest_sev.upper(), 5):
                highest_sev = severity

            inner_rows.append('<tr>'
                              '<td><code>' + name + '</code></td>'
                              '<td>' + badge(severity) + '</td>'
                              '<td>' + score + '</td>'
                              '<td class="vuln-desc">' + desc + '</td>'
                              '</tr>')

        md5_short = (md5[:12] + '…') if md5 and md5 != 'N/A' else '–'
        inner_table = ('<table class="inner-vuln-table">'
                       '<thead><tr><th>CVE / ID</th><th>Severity</th>'
                       '<th>CVSS</th><th>Description</th></tr></thead>'
                       '<tbody>' + ''.join(inner_rows) + '</tbody></table>')

        vuln_rows_html.append(
            '<tr><td class="filepath">' + file_name + '</td>'
            '<td>' + badge(highest_sev) + '</td>'
            '<td><div class="cve-list">' + ''.join(cve_parts) + '</div></td>'
            '<td class="hash-val">' + md5_short + '</td>'
            '<td class="license-val">' + license_ + '</td></tr>'
            '<tr><td colspan="5" style="padding:0;">'
            '<details>'
            '<summary>'
            '&#9654; ' + str(len(vulns)) + ' vulnerability detail(s)</summary>'
            + inner_table +
            '</details></td></tr>')

    # ── Dep card (ALL deps) ───────────────────────────────────────────────────
    status_badge = (badge('HIGH') if has_vulns
                    else '<span class="badge badge-none">CLEAN</span>')

    ev_count = str(len(evidences))
    card = ('<div class="dep-card">'
            '<div class="dep-card-header">'
            '<div><span class="dep-name">&#128230; ' + file_name + '</span>'
            '<span style="margin-left:10px;">' + status_badge + '</span></div>'
            '<span class="ev-count">' + ev_count + ' evidence entries</span>'
            '</div>'
            '<div class="dep-card-body">'
            '<div class="info-grid">'
            '<div class="info-row">'
            '<span class="info-label">&#128194; File Path</span>'
            '<span class="info-value filepath-sm">' + file_path + '</span>'
            '</div>'
            '<div class="info-row">'
            '<span class="info-label">&#128196; License</span>'
            '<span class="info-value">' + license_ + '</span>'
            '</div>'
            '<div class="info-row">'
            '<span class="info-label">&#128273; Identifiers</span>'
            '<span class="info-value">' + id_html + '</span>'
            '</div>'
            '</div>'
            '<div class="hash-grid">'
            '<div class="hash-box"><span class="hash-label">MD5</span>' + md5_disp + '</div>'
            '<div class="hash-box"><span class="hash-label">SHA-1</span>' + sha1_disp + '</div>'
            '<div class="hash-box"><span class="hash-label">SHA-256</span>' + sha256_disp + '</div>'
            '</div>'
            '<details>'
            '<summary>'
            '&#9654; Evidence Collected (' + ev_count + ' entries)</summary>'
            + ev_table +
            '</details>'
            '</div>'
            '</div>')
    all_dep_cards.append(card)

# ── Build final HTML sections ─────────────────────────────────────────────────
vuln_class = 'danger' if vuln_deps > 0 else 'safe'

if vuln_deps == 0:
    vuln_banner  = '<div class="no-vuln">&#10003; No vulnerable dependencies were detected.</div>'
    vuln_section = ''
else:
    vuln_section = ('<div class="section-title">&#9888;&#65039; Vulnerable Dependencies ('
                    + str(vuln_deps) + ')</div>'
                    '<table><thead><tr>'
                    '<th>Dependency</th><th>Highest Severity</th><th>CVE / IDs</th>'
                    '<th>MD5 (partial)</th><th>License</th>'
                    '</tr></thead><tbody>'
                    + ''.join(vuln_rows_html) +
                    '</tbody></table>')
    vuln_banner = ''

all_deps_section = ('<div class="section-title">&#128203; All Scanned Dependencies ('
                    + str(total_deps) + ')</div>'
                    '<div class="dep-cards-container">'
                    + ''.join(all_dep_cards) +
                    '</div>') if all_dep_cards else ''

# ── Fill template ─────────────────────────────────────────────────────────────
with open('report.html', 'r') as f:
    out = f.read()

out = out.replace('{{PROJECT_NAME}}',    proj_name)
out = out.replace('{{TOTAL_DEPS}}',      str(total_deps))
out = out.replace('{{VULN_DEPS}}',       str(vuln_deps))
out = out.replace('{{VULN_COUNT}}',      str(vuln_count))
out = out.replace('{{SUPPRESSED}}',      str(suppressed))
out = out.replace('{{GENERATED_AT}}',    GENERATED_AT)
out = out.replace('{{DC_VERSION}}',      dc_version)
out = out.replace('{{REPORT_DATE}}',     report_date)
out = out.replace('{{VULN_CLASS}}',      vuln_class)
out = out.replace('{{VULN_BANNER}}',     vuln_banner)
out = out.replace('{{VULN_SECTION}}',    vuln_section)
out = out.replace('{{ALL_DEPS_SECTION}}', all_deps_section)

with open('owasp_report.html', 'w') as f:
    f.write(out)

print('Report  : owasp_report.html')
print('Project : ' + proj_name)
print('Scanned : ' + str(total_deps) + ' deps')
print('Vulns   : ' + str(vuln_deps) + ' vulnerable, ' + str(vuln_count) + ' CVEs')
PYEOF

# Run the parser
python3 _owasp_parse.py "$XML_FILE" "$GENERATED_AT"
STATUS=$?

rm -f _owasp_parse.py

if [ $STATUS -ne 0 ]; then
  echo "❌ Python parser failed (exit $STATUS)"
  exit $STATUS
fi

echo "✅ OWASP Dependency Check report generated: $OUTPUT"
