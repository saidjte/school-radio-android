#!/usr/bin/env bash
# =====================================================================
# سكربت التحقق قبل رفع أي إصدار — الإذاعة المدرسية (school-radio-android)
# يفحص: versionCode / versionName / المعماريات (arm64-v8a + armeabi-v7a)
#       / بصمة توقيع SHA-256 (يجب أن تبقى ثابتة عبر كل الإصدارات)
# الاستعمال:  bash tools/verify_release.sh app/build/outputs/apk/release/SchoolRadio_vX.Y.Z.apk
# متطلبات:    build-tools (aapt + apksigner) عبر $BT أو $ANDROID_HOME، و unzip
# =====================================================================
set -uo pipefail

APK="${1:?الاستعمال: verify_release.sh <ملف-apk>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# أين أدوات البناء؟
if [ -z "${BT:-}" ]; then
  for cand in "$ANDROID_HOME/build-tools/35.0.0" "$ANDROID_HOME/build-tools"/*; do
    [ -x "$cand/aapt" ] && BT="$cand" && break
  done
fi
AAPT="${BT}/aapt"
APKSIGNER="${BT}/apksigner"

# التوقعات من مصدر الحقيقة الوحيد
V_NAME=$(grep '^versionName=' "$ROOT/version.properties" | cut -d= -f2 | tr -d '[:space:]')
V_CODE=$(grep '^versionCode=' "$ROOT/version.properties" | cut -d= -f2 | tr -d '[:space:]')
EXPECTED_SHA256=$(grep -oE '[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}' "$ROOT/docs/SIGNING.md" | head -1 | tr -d ':' | tr 'a-f' 'A-F')

PASS=0; FAIL=0
ok()  { echo "  ✔ $1"; PASS=$((PASS+1)); }
bad() { echo "  ✘ $1"; FAIL=$((FAIL+1)); }
sec() { echo ""; echo "== $1 =="; }

echo "تحقق الإصدار: $APK"

# --- 1) versionCode و versionName من ملف الـ APK نفسه ---
sec "1) رقم الإصدار (versionCode / versionName)"
BADGING=$("$AAPT" dump badging "$APK" 2>/dev/null | head -1)
GOT_CODE=$(echo "$BADGING" | grep -oE "versionCode='[0-9]+'" | grep -oE "[0-9]+" | head -1)
GOT_NAME=$(echo "$BADGING"  | grep -oE "versionName='[^']+'" | sed "s/versionName='//;s/'//" | head -1)
echo "  المتوقع: versionCode=$V_CODE versionName=$V_NAME"
echo "  الفعلي : versionCode=$GOT_CODE versionName=$GOT_NAME"
[ "$GOT_CODE" = "$V_CODE" ] && ok "versionCode مطابق" || bad "versionCode غير مطابق!"
[ "$GOT_NAME" = "$V_NAME" ] && ok "versionName مطابق" || bad "versionName غير مطابق!"

# --- 2) المعماريات المدعومة داخل الـ APK ---
sec "2) المعماريات (arm64-v8a + armeabi-v7a)"
LIBS=$(unzip -l "$APK" 2>/dev/null | grep -E 'lib/[^/]+/' | sed -E 's#.*(lib/[^/]+)/.*#\1#' | sort -u)
if [ -z "$LIBS" ]; then
  ok "APK عالمي (universal) بلا مكتبات native — يعمل على arm64-v8a و armeabi-v7a وغيرهما (تطبيق Kotlin خالص)"
else
  echo "  المعماريات المضمنة: $(echo $LIBS | tr '\n' ' ')"
  echo "$LIBS" | grep -q "arm64-v8a"     && ok "arm64-v8a موجودة"     || bad "arm64-v8a مفقودة!"
  echo "$LIBS" | grep -q "armeabi-v7a"   && ok "armeabi-v7a موجودة"   || bad "armeabi-v7a مفقودة!"
  EXTRA=$(echo "$LIBS" | grep -cv -E 'arm64-v8a|armeabi-v7a' || true)
  [ "$EXTRA" = "0" ] && ok "لا معماريات خارج القائمة" || bad "توجد معماريات غير مقصودة!"
fi

# --- 3) بصمة التوقيع SHA-256 (ثابتة عبر الإصدارات) ---
sec "3) بصمة التوقيع SHA-256 (يجب أن تطابق docs/SIGNING.md)"
CERTS=$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null)
GOT_SHA=$(echo "$CERTS" | grep -E "SHA-256 digest" | head -1 | sed 's/.*: //' | tr -d ':' | tr 'a-f' 'A-F')
echo "  المتوقع: ${EXPECTED_SHA256:0:16}…"
echo "  الفعلي : ${GOT_SHA:0:16}…"
if [ -n "$GOT_SHA" ] && [ "$GOT_SHA" = "$EXPECTED_SHA256" ]; then
  ok "البصمة مطابقة — التحديث فوق النسخ القديمة ممكن (نفس المفتاح)"
else
  bad "البصمة غير مطابقة — لا ترفع هذا الملف أبداً!"
fi

# --- 4) أمان التوقيع v2 ---
sec "4) سلامة توقيع APK"
if "$APKSIGNER" verify "$APK" >/dev/null 2>&1; then
  ok "APK موقّع بشكل صحيح (v1/v2)"
else
  bad "توقيع APK غير سليم!"
fi

echo ""
echo "=============================="
if [ "$FAIL" = "0" ]; then
  echo "✔ نجاح التحقق: $PASS فحصاً ناجحاً — الملف جاهز للرفع"
  exit 0
else
  echo "✘ فشل التحقق: $FAIL فحصاً فاشلاً — لا ترفع الملف"
  exit 1
fi
