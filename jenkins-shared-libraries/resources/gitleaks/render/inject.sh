#!/bin/sh
set -e

JSON_FILE="gitleaks.json"
TEMPLATE="report.html"
OUTPUT="gitleaks_report.html"

TOTAL=$(jq length "$JSON_FILE")
GENERATED_AT=$(date "+%d %b %Y %H:%M:%S")

# -------------------------------
# Generate table rows safely
# -------------------------------
jq -r '
  .[] |
  "<tr>" +
  "<td>" + .RuleID + "</td>" +
  "<td>" + .File + "</td>" +

  # ✅ FIX: Handle Line number properly across Gitleaks versions
  "<td>" +
    (
      if .Line != null then (.Line | tostring)
      elif .StartLine != null then (.StartLine | tostring)
      elif .Location != null and .Location.StartLine != null then (.Location.StartLine | tostring)
      else "N/A"
      end
    ) +
  "</td>" +

  "<td>" + (.Author // "Unknown") + "</td>" +
  "<td>" + .Description + "</td>" +

  # Mask secret, show last 4 chars only
  "<td><code>****" + (.Secret[-4:]) + "</code></td>" +

  "<td>" + (.Commit[0:7]) + "</td>" +
  "</tr>"
' "$JSON_FILE" > rows.tmp

# -------------------------------
# Replace summary placeholders
# -------------------------------
sed \
  -e "s/{{TOTAL_FINDINGS}}/$TOTAL/g" \
  -e "s/{{GENERATED_AT}}/$GENERATED_AT/g" \
  "$TEMPLATE" > step1.html

# -------------------------------
# Inject rows safely
# -------------------------------
awk '
  /{{ROWS}}/ {
    while ((getline line < "rows.tmp") > 0) print line
    next
  }
  { print }
' step1.html > "$OUTPUT"

# -------------------------------
# Cleanup
# -------------------------------
rm -f rows.tmp step1.html

echo "✅ Gitleaks report generated: $OUTPUT"
