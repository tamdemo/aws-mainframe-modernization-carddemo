#!/usr/bin/env bash
# Runs the original COBOL CBACT03C (GnuCOBOL) and the Java port on the same
# sample cross-reference data and diffs their SYSOUT line by line.
# Requires: cobc (GnuCOBOL 3.x with indexed-file support), JDK 17+.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PORT="$(cd "$HERE/.." && pwd)"
REPO="$(cd "$PORT/../.." && pwd)"
BUILD="$HERE/build"
rm -rf "$BUILD" && mkdir -p "$BUILD/classes"

# 1. Build the original COBOL program, unchanged, plus the test-only KSDS loader.
cobc -x -std=ibm -I "$REPO/app/cpy" -ext cpy -o "$BUILD/cbact03c" "$REPO/app/cbl/CBACT03C.cbl"
cobc -x -o "$BUILD/loadxref" "$HERE/LOADXREF.cbl"

# 2. Load the ASCII sample (padded to LRECL 50) into an indexed file, like the
#    IDCAMS REPRO in app/jcl/XREFFILE.jcl.
awk '{ printf "%-50s", $0 }' "$REPO/app/data/ASCII/cardxref.txt" > "$BUILD/xref.flat"
INFILE="$BUILD/xref.flat" DD_XREFFILE="$BUILD/xref.ksds" "$BUILD/loadxref"

# 3. Run the COBOL original.
DD_XREFFILE="$BUILD/xref.ksds" "$BUILD/cbact03c" > "$BUILD/cobol.out"

# 4. Build and run the Java port on the EBCDIC and ASCII copies of the same data.
javac -d "$BUILD/classes" $(find "$PORT/src/main/java" -name '*.java')
java -cp "$BUILD/classes" com.carddemo.batch.cbact03c.Cbact03c \
    --xreffile="$REPO/app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS" > "$BUILD/java-ebcdic.out"
java -cp "$BUILD/classes" com.carddemo.batch.cbact03c.Cbact03c \
    --xreffile="$REPO/app/data/ASCII/cardxref.txt" --encoding=US-ASCII --format=LINE > "$BUILD/java-ascii.out"

# 5. Compare.
diff "$BUILD/cobol.out" "$BUILD/java-ebcdic.out"
diff "$BUILD/cobol.out" "$BUILD/java-ascii.out"
echo "PARITY OK: $(wc -l < "$BUILD/cobol.out") lines identical (COBOL vs Java, EBCDIC and ASCII input)"
