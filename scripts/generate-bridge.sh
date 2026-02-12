#!/bin/bash
# UniFFI 桥接代码生成脚本
#
# 用途：从 .udl 文件生成 Kotlin 接口代码
#
# 使用方法：
#   ./scripts/generate-bridge.sh

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== UniFFI 桥接代码生成 ===${NC}"

# 检查 uniffi-bindgen 是否安装
if ! command -v uniffi-bindgen &> /dev/null; then
    echo -e "${YELLOW}安装 uniffi-bindgen...${NC}"
    cargo install uniffi_bindgen --locked
fi

# 输出目录
KOTLIN_OUTPUT_DIR="android/app/src/main/kotlin"

# 清理旧代码
echo -e "${YELLOW}清理旧代码...${NC}"
rm -rf "$KOTLIN_OUTPUT_DIR/aeternum"

# 生成 Kotlin 代码
echo -e "${YELLOW}生成 Kotlin 接口...${NC}"
uniffi-bindgen generate "core/uniffi/aeternum.udl" \
    --language kotlin \
    --out-dir "$KOTLIN_OUTPUT_DIR"

# 生成 Rust scaffolding
echo -e "${YELLOW}生成 Rust scaffolding...${NC}"
uniffi-bindgen generate "core/uniffi/aeternum.udl" \
    --language rust \
    --out-dir "core/src"

echo -e "${GREEN}=== 生成完成 ===${NC}"
echo "Kotlin 输出: $KOTLIN_OUTPUT_DIR"
