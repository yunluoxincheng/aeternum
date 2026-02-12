# Implementation Tasks

## 1. 项目设置与依赖配置
- [x] 1.1 创建 `core/src/crypto/` 目录结构（hash/kdf/aead/kem/ecdh）
- [x] 1.2 更新 `core/Cargo.toml`，添加密码学依赖（精确版本锁定）
- [x] 1.3 添加 `thiserror`、`zeroize`、`zeroize_derive` 依赖
- [x] 1.4 配置开发依赖：`proptest`、`criterion`
- [x] 1.5 创建 `core/src/crypto/mod.rs`，定义模块导出
- [x] 1.6 修复 Windows 编译问题（升级 pqcrypto-kyber 到 0.8.1，修复类型冲突）

## 2. 错误处理实现
- [x] 2.1 创建 `core/src/crypto/error.rs`
- [x] 2.2 定义 `CryptoError` 枚举（KdfError, AeadError, KemError, InvalidKeyLength, VerificationFailed）
- [x] 2.3 实现 `Result<T>` 类型别名
- [x] 2.4 添加错误测试用例

## 3. BLAKE3 哈希实现
- [x] 3.1 创建 `core/src/crypto/hash/mod.rs` 和 `blake3.rs`
- [x] 3.2 实现 `HashOutput` 新类型（32 字节，Zeroize）
- [x] 3.3 实现 `Blake3Hasher` 结构体（update/finalize API）
- [x] 3.4 实现便捷函数 `hash(data: &[u8]) -> HashOutput`
- [x] 3.5 实现密钥派生模式 `DeriveKey::derive(ikm, info, length)`
- [x] 3.6 添加 BLAKE3 官方测试向量（含已知向量验证和一致性测试）
- [x] 3.7 添加 proptest 属性测试

## 4. Argon2id KDF 实现
- [x] 4.1 创建 `core/src/crypto/kdf/mod.rs` 和 `argon2id.rs`
- [x] 4.2 实现 `Argon2idConfig` 结构体（OWASP 2024 推荐默认参数）
- [x] 4.3 实现 `DerivedKey` 类型（Zeroize）
- [x] 4.4 实现 `Argon2idKDF` 结构体及 `derive_key()` 方法
- [x] 4.5 添加参数验证（m_cost >= 8192, t_cost >= 1）
- [x] 4.6 添加 RFC 9106 测试向量
- [x] 4.7 添加 proptest 属性测试
- [x] 4.8 添加 criterion 性能基准测试（目标 <500ms）

## 5. XChaCha20-Poly1305 AEAD 实现
- [ ] 5.1 创建 `core/src/crypto/aead/mod.rs` 和 `xchacha20.rs`
- [ ] 5.2 实现 `XChaCha20Key` 类型（32 字节，Zeroize）
- [ ] 5.3 实现 `XChaCha20Nonce` 类型（24 字节）
- [ ] 5.4 实现 `AuthTag` 类型（16 字节）
- [ ] 5.5 实现 `AeadCipher` 结构体（encrypt/decrypt/in_place）
- [ ] 5.6 添加 RFC 8439 和 Wycheproof 测试向量
- [ ] 5.7 添加 proptest 加密-解密往返测试
- [ ] 5.8 添加边界测试（空明文、超大输入）

## 6. Kyber-1024 KEM 实现
- [ ] 6.1 创建 `core/src/crypto/kem/mod.rs` 和 `kyber.rs`
- [ ] 6.2 实现 `KyberPublicKeyBytes` 类型（1184 字节）
- [ ] 6.3 实现 `KyberSecretKeyBytes` 类型（2400 字节，Zeroize）
- [ ] 6.4 实现 `KyberCipherText` 类型（1568 字节）
- [ ] 6.5 实现 `KyberSharedSecret` 类型（32 字节，Zeroize）
- [ ] 6.6 实现 `KyberKeyPair` 结构体
- [ ] 6.7 实现 `KyberKEM::generate_keypair()`
- [ ] 6.8 实现 `KyberKEM::encapsulate()` 和 `decapsulate()`
- [ ] 6.9 添加 NIST FIPS 203 KAT 测试向量
- [ ] 6.10 添加 proptest 封装-解封装往返测试

## 7. X25519 ECDH 实现
- [ ] 7.1 创建 `core/src/crypto/ecdh/mod.rs` 和 `x25519.rs`
- [ ] 7.2 实现 `X25519PublicKeyBytes` 类型（32 字节）
- [ ] 7.3 实现 `X25519SecretKeyBytes` 类型（32 字节，Zeroize）
- [ ] 7.4 实现 `EcdhSharedSecret` 类型（32 字节，Zeroize）
- [ ] 7.5 实现 `X25519KeyPair` 结构体
- [ ] 7.6 实现 `X25519ECDH::generate_keypair()` 和 `diffie_hellman()`
- [ ] 7.7 实现 `HybridKeyExchange::combine_secrets()`（Kyber + X25519）
- [ ] 7.8 添加 RFC 7748 测试向量
- [ ] 7.9 添加 proptest DH 交换对称性测试
- [ ] 7.10 添加边界测试（全零密钥、全 0xFF 密钥）

## 8. 测试向量准备
- [ ] 8.1 创建 `core/src/crypto/test_vectors/` 目录
- [ ] 8.2 下载/整理 Wycheproof BLAKE3 测试向量
- [ ] 8.3 下载/整理 RFC 9106 Argon2id 测试向量
- [ ] 8.4 下载/整理 RFC 8439 XChaCha20-Poly1305 测试向量
- [ ] 8.5 下载/整理 NIST FIPS 203 Kyber-1024 KAT 测试向量
- [ ] 8.6 下载/整理 RFC 7748 X25519 测试向量

## 9. 集成与文档
- [ ] 9.1 更新 `core/src/lib.rs`，导出 `crypto` 模块
- [ ] 9.2 为每个公共 API 添加 rustdoc 文档注释
- [ ] 9.3 添加安全警告和使用示例
- [ ] 9.4 运行 `cargo test` 确保所有测试通过
- [ ] 9.5 运行 `cargo clippy` 修复所有警告
- [ ] 9.6 运行 `cargo fmt` 确保代码格式一致
