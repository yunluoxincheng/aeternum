#!/bin/bash
# 代码格式检查脚本
#
# 检查 Rust 和 Kotlin 代码格式

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== 代码格式检查 ===${NC}"

# Rust 格式检查
echo -e "${YELLOW}检查 Rust 代码格式...${NC}"
cd core
if cargo fmt --all -- --check; then
    echo -e "${GREEN}✓ Rust 代码格式正确${NC}"
else
    echo -e "${YELLOW}⚠ Rust 代码需要格式化，运行: cargo fmt${NC}"
    exit 1
fi
cd ..

# Kotlin 格式检查 (需要 ktlint)
echo -e "${YELLOW}检查 Kotlin 代码格式...${NC}"
if command -v ktlint &> /dev/null; then
    ktlint --reporter=plain android/app/src/main/kotlin/**/*.kt
    echo -e "${GREEN}✓ Kotlin 代码格式正确${NC}"
else
    echo -e "${YELLOW}⚠ ktlint 未安装，跳过 Kotlin 检查${NC}"
fi

echo -e "${GREEN}=== 检查完成 ===${NC}"
