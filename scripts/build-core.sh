#!/bin/bash
# Aeternum Rust 内核交叉编译脚本
#
# 用途：将 Rust 核心编译为各 Android 平台的 .so 库
#
# 使用方法：
#   ./scripts/build-core.sh

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Aeternum Core 交叉编译 ===${NC}"

# 目标架构
TARGETS=(
    "aarch64-linux-android"    # ARM64
    "armv7-linux-androideabi"  # ARMv7
    "x86_64-linux-android"     # x86_64
)

# NDK 路径 (从环境变量或默认位置)
NDK="${ANDROID_NDK_HOME:-$ANDROID_NDK}"

if [ -z "$NDK" ]; then
    echo -e "${RED}错误: 未找到 Android NDK${NC}"
    echo "请设置 ANDROID_NDK_HOME 或 ANDROID_NDK 环境变量"
    exit 1
fi

echo -e "${YELLOW}使用 NDK: $NDK${NC}"

# 设置 NDK 工具链
export PATH="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

# 创建输出目录
mkdir -p android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}

# 编译每个目标
for TARGET in "${TARGETS[@]}"; do
    echo -e "${YELLOW}编译 $TARGET...${NC}"

    case $TARGET in
        "aarch64-linux-android")
            ABI="arm64-v8a"
            ;;
        "armv7-linux-androideabi")
            ABI="armeabi-v7a"
            ;;
        "x86_64-linux-android")
            ABI="x86_64"
            ;;
    esac

    # 构建
    cargo build --release --target $TARGET

    # 复制 .so 文件
    cp "target/$TARGET/release/libaeternum_core.so" \
       "android/app/src/main/jniLibs/$ABI/"

    echo -e "${GREEN}✓ $ABI 编译完成${NC}"
done

echo -e "${GREEN}=== 编译完成 ===${NC}"
echo "输出目录: android/app/src/main/jniLibs/"
