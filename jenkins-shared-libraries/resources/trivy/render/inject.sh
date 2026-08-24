#!/bin/sh
# =============================================================================
# inject.sh — Trivy JSON → Human-Readable HTML Report
# =============================================================================
set -e

JSON_FILE="trivy_report.json"
OUTPUT="trivy_report.html"

if [ ! -f "$JSON_FILE" ]; then
  echo "❌  $JSON_FILE not found in $(pwd)"
  exit 1
fi

GENERATED_AT=$(date "+%d %b %Y %H:%M:%S")

cat > _trivy_parse.py << 'PYEOF'
import sys, json, html as H

JSON_FILE    = sys.argv[1]
GENERATED_AT = sys.argv[2]

try:
    with open(JSON_FILE, 'r') as f:
        data = json.load(f)
except Exception as e:
    print(f"Error reading JSON: {e}")
    sys.exit(1)

artifact_name = data.get('ArtifactName', 'Unknown Image')

results = data.get('Results', [])

total_vulns = 0
critical_count = 0
high_count = 0
medium_count = 0

def badge(sev):
    s = (sev or 'UNKNOWN').upper()
    cls = {'CRITICAL':'badge-critical','HIGH':'badge-high','MEDIUM':'badge-medium',
           'LOW':'badge-low'}.get(s, 'badge-unknown')
    return '<span class="badge ' + cls + '">' + H.escape(s) + '</span>'

target_sections = []

for result in results:
    target = result.get('Target', 'Unknown Target')
    target_class = result.get('Class', '')
    type_name = result.get('Type', '')
    
    header_info = target
    if type_name:
        header_info += f" ({type_name})"
        
    vulns = result.get('Vulnerabilities', [])
    if not vulns:
        continue
        
    total_vulns += len(vulns)
    
    rows = []
    for v in vulns:
        vuln_id = v.get('VulnerabilityID', 'Unknown')
        pkg_name = v.get('PkgName', 'Unknown')
        installed_ver = v.get('InstalledVersion', 'Unknown')
        fixed_ver = v.get('FixedVersion', 'Not Fixed')
        severity = v.get('Severity', 'UNKNOWN')
        title = v.get('Title', '')
        desc = v.get('Description', 'No description available.')
        primary_url = v.get('PrimaryURL', '')
        
        if severity == 'CRITICAL':
            critical_count += 1
        elif severity == 'HIGH':
            high_count += 1
        elif severity == 'MEDIUM':
            medium_count += 1
            
        cve_tag = f'<a class="cve-tag" href="{primary_url}" target="_blank">{H.escape(vuln_id)}</a>' if primary_url else f'<span class="cve-tag">{H.escape(vuln_id)}</span>'
        
        title_html = f'<div class="vuln-title">{H.escape(title)}</div>' if title else ''
        
        rows.append('<tr>'
                    '<td><div class="pkg-name">' + H.escape(pkg_name) + '</div>'
                    '<div class="version-info"><span class="version-installed">Installed: ' + H.escape(installed_ver) + '</span> | '
                    '<span class="version-fixed">Fixed: ' + H.escape(fixed_ver) + '</span></div></td>'
                    '<td>' + cve_tag + title_html + '</td>'
                    '<td>' + badge(severity) + '</td>'
                    '<td style="font-size:12px;color:#94a3b8;">' + H.escape(desc) + '</td>'
                    '</tr>')
                    
    if rows:
        target_sections.append(
            '<div class="target-box">'
            '<div class="target-header">'
            '<span class="target-name">&#128193; ' + H.escape(header_info) + '</span>'
            '<span style="font-size:11px;color:#94a3b8;font-weight:normal;">' + str(len(vulns)) + ' vulnerabilities</span>'
            '</div>'
            '<table><thead><tr>'
            '<th style="width:25%">Package</th><th style="width:25%">Vulnerability</th><th style="width:10%">Severity</th><th style="width:40%">Description</th>'
            '</tr></thead><tbody>' + ''.join(rows) + '</tbody></table>'
            '</div>'
        )

vuln_class = 'danger' if total_vulns > 0 else 'safe'

if total_vulns == 0:
    vuln_banner  = '<div class="no-vuln">&#10003; No vulnerabilities were found!</div>'
    target_sections_html = ''
else:
    vuln_banner = ''
    target_sections_html = ''.join(target_sections)

with open('report.html', 'r') as f:
    out = f.read()

out = out.replace('{{IMAGE_NAME}}',      H.escape(artifact_name))
out = out.replace('{{TOTAL_VULNS}}',     str(total_vulns))
out = out.replace('{{CRITICAL_COUNT}}',  str(critical_count))
out = out.replace('{{HIGH_COUNT}}',      str(high_count))
out = out.replace('{{MEDIUM_COUNT}}',    str(medium_count))
out = out.replace('{{GENERATED_AT}}',    GENERATED_AT)
out = out.replace('{{VULN_CLASS}}',      vuln_class)
out = out.replace('{{VULN_BANNER}}',     vuln_banner)
out = out.replace('{{TARGET_SECTIONS}}', target_sections_html)

with open('trivy_report.html', 'w') as f:
    f.write(out)

print('Report  : trivy_report.html')
print('Image   : ' + artifact_name)
print('Vulns   : ' + str(total_vulns) + ' (Crit: ' + str(critical_count) + ', High: ' + str(high_count) + ', Med: ' + str(medium_count) + ')')
PYEOF

python3 _trivy_parse.py "$JSON_FILE" "$GENERATED_AT"
STATUS=$?

rm -f _trivy_parse.py

if [ $STATUS -ne 0 ]; then
  echo "❌ Python parser failed (exit $STATUS)"
  exit $STATUS
fi

echo "✅ Trivy report generated: $OUTPUT"
