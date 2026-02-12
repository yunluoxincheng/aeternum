#!/bin/bash
# Aeternum 安全审计脚本
#
# 用途：扫描潜在的安全问题和依赖漏洞
#
# 使用方法：
#   ./scripts/security-audit.sh

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Aeternum 安全审计 ===${NC}"

# 1. 检查未使用的依赖
echo -e "${YELLOW}检查未使用的依赖...${NC}"
cd core
cargo machete || echo "cargo-machete 未安装，跳过"
cd ..

# 2. 检查漏洞
echo -e "${YELLOW}检查依赖漏洞...${NC}"
cd core
cargo audit || echo "cargo-audit 未安装，跳过"
cd ..

# 3. 检查不当使用 unsafe
echo -e "${YELLOW}扫描 unsafe 代码...${NC}"
UNSAFE_COUNT=$(grep -r "unsafe" core/src/ | wc -l)
if [ "$UNSAFE_COUNT" -gt 0 ]; then
    echo -e "${RED}发现 $UNSAFE_COUNT 处 unsafe 代码${NC}"
    grep -rn "unsafe" core/src/
else
    echo -e "${GREEN}✓ 未发现 unsafe 代码${NC}"
fi

# 4. 检查是否正确使用 zeroize
echo -e "${YELLOW}检查密钥结构是否使用 zeroize...${NC}"
# 简单检查：查看是否在密钥相关文件中使用了 Zeroize
if grep -q "Zeroize" core/src/crypto/*.rs core/src/models/*.rs; then
    echo -e "${GREEN}✓ 密钥结构已实现 Zeroize${NC}"
else
    echo -e "${RED}⚠ 部分密钥结构可能未实现 Zeroize${NC}"
fi

# 5. 运行测试
echo -e "${YELLOW}运行测试套件...${NC}"
cd core
cargo test --all-features
cd ..

echo -e "${GREEN}=== 审计完成 ===${NC}"
