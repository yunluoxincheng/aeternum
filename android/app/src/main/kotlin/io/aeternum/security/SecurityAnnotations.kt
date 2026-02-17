package io.aeternum.security

/**
 * 安全边界注解
 *
 * 用于标记代码的安全边界约束，便于代码审查和静态分析
 */

/**
 * 标记函数或类属于安全边界
 *
 * 被标记的代码需要额外审查，确保不违反安全约束
 *
 * @property boundary 安全边界类型
 * @property constraints 安全约束说明
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SecurityBoundary(
    val boundary: Boundary,
    val constraints: Array<String> = [],
)

/**
 * 安全边界类型
 */
enum class Boundary {
    /** UI 层 - 非信任域 */
    UI_LAYER,

    /** 安全层 - 部分信任域 */
    SECURITY_LAYER,

    /** Bridge 层 - 信任边界 */
    BRIDGE_LAYER,

    /** Rust 核心 - 完全信任域 */
    RUST_CORE,
}

/**
 * 标记函数为安全敏感操作
 *
 * 被标记的函数需要确保：
 * - 不泄露敏感信息
 * - 遵循最小权限原则
 *
 * @property risk 风险等级
 * @property mitigations 缓解措施
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SecuritySensitive(
    val risk: RiskLevel,
    val mitigations: Array<String> = [],
)

/**
 * 风险等级
 */
enum class RiskLevel {
    /** 低风险 - 仅影响 UI 显示 */
    LOW,

    /** 中风险 - 可能泄露非敏感信息 */
    MEDIUM,

    /** 高风险 - 可能泄露敏感信息 */
    HIGH,

    /** 关键风险 - 可能泄露密钥或凭据 */
    CRITICAL,
}

/**
 * 标记函数不持有明文密钥
 *
 * 用于验证函数遵守安全边界约束
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class NoPlaintextKey

/**
 * 标记函数需要生物识别认证
 *
 * @property reason 认证原因
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class RequiresBiometric(val reason: String)

/**
 * 标记屏幕需要 FLAG_SECURE
 *
 * @property reason 启用原因
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class RequiresSecureFlag(val reason: String)
