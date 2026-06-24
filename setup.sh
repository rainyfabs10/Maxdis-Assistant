#!/bin/bash
# ============================================================
# SETUP SCRIPT - Jalankan ini SEKALI sebelum push ke GitHub
# Otomatis download gradle-wrapper.jar yang dibutuhkan
# ============================================================

echo "📦 Downloading gradle-wrapper.jar..."

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"

# Coba download
curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"

if [ $? -eq 0 ] && [ -s "$WRAPPER_JAR" ]; then
    echo "✅ gradle-wrapper.jar berhasil didownload!"
    echo "📁 Ukuran: $(du -h $WRAPPER_JAR | cut -f1)"
else
    echo "❌ Gagal download. Coba cara manual:"
    echo "   1. Buka: https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
    echo "   2. Save file ke folder: gradle/wrapper/"
fi

echo ""
echo "✅ Setup selesai! Sekarang bisa push ke GitHub."
