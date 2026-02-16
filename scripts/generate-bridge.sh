#!/bin/bash
# UniFFI 桥接代码生成脚本
#
# 用途：从编译好的 Rust 库生成 Kotlin 桥口代码
#
# 使用方法：
#   ./scripts/generate-bridge.sh [--platform <host|android>]
#
# 注意：
#   - 默认从 host 平台编译好的库生成
#   - 使用 --platform android 需要 Android 库已编译
#   - 生成的 Kotlin 代码与平台无关（仅 FFI 符号不同）
#   - 使用 proc-macro 模式（不需要 UDL 文件，兼容 Windows）

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 解析参数
PLATFORM="host"
MODE="proc-macro"
while [[ $# -gt 0 ]]; do
    case $1 in
        --platform)
            PLATFORM="$2"
            shift 2
            ;;
        --mode)
            MODE="$2"
            shift 2
            ;;
        *)
            echo "未知参数: $1"
            echo "用法: $0 [--platform <host|android>] [--mode <proc-macro|udl>]"
            exit 1
            ;;
    esac
done

echo -e "${GREEN}=== UniFFI 桥接代码生成 ===${NC}"
echo -e "${BLUE}目标平台: $PLATFORM${NC}"
echo -e "${BLUE}生成模式: $MODE${NC}"

# 输出目录
KOTLIN_OUTPUT_DIR="android/app/src/main/kotlin/aeternum"

# 根据平台选择库文件
LIB_FILE=""
if [ "$PLATFORM" = "android" ]; then
    # Android 平台 - 使用 ARM64 库（最常用）
    if [ -f "android/app/src/main/jniLibs/arm64-v8a/libaeternum_core.so" ]; then
        LIB_FILE="android/app/src/main/jniLibs/arm64-v8a/libaeternum_core.so"
        echo -e "${YELLOW}使用 Android ARM64 库${NC}"
    else
        echo -e "${YELLOW}⚠️  Android 库未编译，正在运行 build-core.sh...${NC}"
        bash scripts/build-core.sh
        LIB_FILE="android/app/src/main/jniLibs/arm64-v8a/libaeternum_core.so"
    fi
else
    # Host 平台（开发机器）
    if [ -f "core/target/release/aeternum_core.dll" ]; then
        LIB_FILE="core/target/release/aeternum_core.dll"
        echo -e "${YELLOW}检测到 Windows 动态库${NC}"
    elif [ -f "core/target/release/libaeternum_core.so" ]; then
        LIB_FILE="core/target/release/libaeternum_core.so"
        echo -e "${YELLOW}检测到 Unix 共享库${NC}"
    elif [ -f "core/target/release/libaeternum_core.dylib" ]; then
        LIB_FILE="core/target/release/libaeternum_core.dylib"
        echo -e "${YELLOW}检测到 macOS 动态库${NC}"
    else
        echo -e "${YELLOW}⚠️  核心库未编译，正在编译...${NC}"
        cd core
        cargo build --release
        cd ..

        if [ -f "core/target/release/aeternum_core.dll" ]; then
            LIB_FILE="core/target/release/aeternum_core.dll"
        elif [ -f "core/target/release/libaeternum_core.so" ]; then
            LIB_FILE="core/target/release/libaeternum_core.so"
        elif [ -f "core/target/release/libaeternum_core.dylib" ]; then
            LIB_FILE="core/target/release/libaeternum_core.dylib"
        else
            echo -e "${YELLOW}❌ 错误：找不到编译好的核心库${NC}"
            exit 1
        fi
    fi
fi

# 生成 Kotlin 代码
echo -e "${YELLOW}从编译好的库生成 Kotlin 桥接...${NC}"
cd core
# 转换为相对于 core 目录的路径
LIB_RELATIVE_PATH="../$LIB_FILE"

if [ "$MODE" = "proc-macro" ]; then
    # Proc-macro 模式（推荐，跨平台兼容）
    # 直接使用编译好的库文件作为 SOURCE，UniFFI 会自动检测
    echo -e "${BLUE}使用 proc-macro 模式（从库文件生成）${NC}"
    cargo run --release --bin uniffi-bindgen \
        generate \
        "$LIB_RELATIVE_PATH" \
        --language kotlin \
        --out-dir "../$KOTLIN_OUTPUT_DIR"
else
    # UDL 模式（传统方式，Windows 上可能有问题）
    echo -e "${YELLOW}使用 UDL 模式（可能在 Windows 上失败）${NC}"

    # 检查 UDL 文件
    if [ ! -f "uniffi/aeternum.udl" ]; then
        echo -e "${YELLOW}❌ 错误：找不到 UDL 文件 uniffi/aeternum.udl${NC}"
        exit 1
    fi

    cargo run --release --bin uniffi-bindgen \
        generate \
        uniffi/aeternum.udl \
        --language kotlin \
        --out-dir "../$KOTLIN_OUTPUT_DIR" \
        --lib-file "$LIB_RELATIVE_PATH"
fi

cd ..

echo -e "${GREEN}=== 生成完成 ===${NC}"
echo "Kotlin 输出: $KOTLIN_OUTPUT_DIR/uniffi/"
echo ""
echo -e "${BLUE}重要说明：${NC}"
echo "1. UniFFI 生成的 Kotlin 代码使用 JNA (Java Native Access)"
echo "2. 已在 android/app/build.gradle.kts 中添加 JNA AAR 依赖"
echo "3. 已在 proguard-rules.pro 中添加 JNA 混淆规则"
echo "4. 生成的代码同时支持 Android 和桌面平台"
echo "5. Proc-macro 模式完全兼容 Windows，无需 UDL 文件"
echo ""
echo "下一步："
echo "1. 确保 Android .so 库已编译: ./scripts/build-core.sh"
echo "2. 同步 Gradle 依赖"
echo "3. 构建 Android APK: cd android && ./gradlew assembleDebug"
