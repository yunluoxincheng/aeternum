# Change: 实现核心密码学原语

## Why

Aeternum 项目需要实现 Rust 核心层的五大基础密码学原语，作为整个密钥管理系统的密码学基础。当前 `core/src/crypto/` 目录为空，需要实现：
- BLAKE3 哈希
- Argon2id KDF
- XChaCha20-Poly1305 AEAD
- Kyber-1024 KEM
- X25519 ECDH

这些原语将支撑上层协议（PQRR、影子包装、密钥派生等）的实现。

## What Changes

- **新增**: `core/src/crypto/` 模块结构（hash/kdf/aead/kem/ecdh 子模块）
- **新增**: 密码学错误类型体系 (`CryptoError`)
- **新增**: BLAKE3 哈希和密钥派生功能
- **新增**: Argon2id 密钥派生功能（带安全的默认参数）
- **新增**: XChaCha20-Poly1305 认证加密功能
- **新增**: Kyber-1024 密钥封装机制（抗量子）
- **新增**: X25519 椭圆曲线 Diffie-Hellman 密钥交换
- **新增**: 混合密钥交换（Kyber + X25519）
- **新增**: 测试向量集成（Wycheproof/RFC 标准向量）
- **新增**: 属性测试（proptest）

## Impact

- **影响范围**: `core/` Rust 密码内核
- **依赖项**: 添加 5 个密码学 crate（blake3, argon2, chacha20poly1305, pqcrypto-kyber, x25519-dalek）
- **向后兼容**: 不影响现有代码（新增功能）
- **安全影响**: 所有密钥类型实现 `Zeroize`，确保内存安全

## 风险评估

- **低风险**: 使用经过审计的成熟 crate，不涉及算法创新
- **缓解措施**:
  - 所有依赖使用精确版本锁定（`=x.y.z`）
  - 100% 测试覆盖率要求
  - Wycheproof 标准测试向量验证
