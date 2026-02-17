package io.aeternum.ui.viewmodel

import uniffi.aeternum.DeviceInfo as UniFFIDeviceInfo
import uniffi.aeternum.ProtocolState

/**
 * UI 层设备信息模型
 *
 * INVARIANT: 仅包含脱敏数据，不持有敏感信息
 * 此类型用于 UI 层显示，与 UniFFI 层的 DeviceInfo 分离
 *
 * ## 安全约束
 * - ID 以脱敏形式存储（String 而非 ByteArray）
 * - 不包含任何密钥材料
 * - 所有字段均可安全显示在 UI 上
 *
 * @property id 设备 ID（脱敏字符串，仅用于标识）
 * @property name 设备名称
 * @property epoch 当前纪元
 * @property state 设备状态字符串（如 "active", "degraded", "revoked"）
 * @property isThisDevice 是否为本机
 */
data class DeviceInfo(
    val id: String,
    val name: String,
    val epoch: UInt,
    val state: String,
    val isThisDevice: Boolean,
)

/**
 * 设备状态过滤器
 *
 * 用于设备列表的状态过滤
 */
enum class DeviceFilter(val displayName: String) {
    /** 全部设备 */
    ALL("全部"),

    /** 仅活跃设备 */
    ACTIVE("活跃"),

    /** 仅降级设备 */
    DEGRADED("降级"),

    /** 仅撤销设备 */
    REVOKED("撤销"),
}

/**
 * 将 UniFFI 层的 DeviceInfo 转换为 UI 层的 DeviceInfo
 *
 * @param uniFFIDevice UniFFI 层的设备信息
 * @return UI 层的设备信息（脱敏）
 */
fun sanitizeDeviceInfo(uniFFIDevice: UniFFIDeviceInfo): DeviceInfo {
    // 将 ByteArray ID 转换为脱敏字符串
    val sanitizedId = uniFFIDevice.deviceId
        .take(8)
        .joinToString("") { "%02x".format(it) }

    return DeviceInfo(
        id = sanitizedId,
        name = uniFFIDevice.deviceName,
        epoch = uniFFIDevice.epoch,
        state = uniFFIDevice.getStateString(),
        isThisDevice = uniFFIDevice.isThisDevice,
    )
}

/**
 * UniFFI DeviceInfo 扩展函数
 *
 * 将 ProtocolState 转换为字符串用于 UI 显示
 */
fun UniFFIDeviceInfo.getStateString(): String {
    return when (state) {
        ProtocolState.IDLE -> "idle"
        ProtocolState.REKEYING -> "rekeying"
        ProtocolState.RECOVERY_INITIATED -> "recovery_initiated"
        ProtocolState.DEGRADED -> "degraded"
        ProtocolState.REVOKED -> "revoked"
    }
}
