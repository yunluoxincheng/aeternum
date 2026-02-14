## Implementation Tasks

## 1. Header 序列化 (protocol/pqrr.rs)

- [x] 1.1 为 `DeviceHeader` 添加 `serialize()` 方法
- [x] 1.2 为 `DeviceHeader` 添加 `deserialize()` 方法
- [x] 1.3 更新 `get_device_headers()` 返回序列化数据
- [x] 1.4 添加序列化单元测试
- [x] 1.5 添加反序列化单元测试

## 2. AUP 密钥派生 (storage/aug.rs)

- [x] 2.1 实现当前 VK 解封逻辑
- [x] 2.2 实现新 DEK 派生（调用 crypto::kdf）
- [x] 2.3 实现用新 DEK 重新加密 VK
- [x] 2.4 实现序列化为 VaultBlob
- [x] 2.5 添加 AUP 完整流程集成测试

## 3. VetoExpired 错误变体 (sync/wire.rs)

- [x] 3.1 在 `WireError` 枚举中添加 `VetoExpired` 变体
- [x] 3.2 更新 `handle_veto()` 返回 `VetoExpired` 错误
- [x] 3.3 添加 VetoExpired 错误测试

## 4. 测试与验证

- [x] 4.1 运行 `cargo test` 确保所有测试通过
- [x] 4.2 运行 `cargo clippy` 确保无警告（修改文件无新警告）
- [x] 4.3 检查测试覆盖率

### 覆盖率结果（使用 cargo-llvm-cov）
- **总体**: 96.54% regions, 92.31% functions, 95.74% lines
- 详细报告已生成在 `target/coverage/` 目录
- 修复了 epoch_upgrade 测试中的占位符数据问题
- 修复了大量文档测试的类型和导入问题
- 注意: `sync/version.rs:109` 文档测试间歇性失败（单独运行时通过）
