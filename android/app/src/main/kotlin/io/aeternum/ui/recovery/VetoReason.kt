package io.aeternum.ui.recovery

/**
 * 否决原因枚举
 *
 * 定义用户可以否决恢复请求的原因
 */
enum class VetoReason(val displayName: String, val description: String = "") {
    /**
     * 不是本人操作
     */
    NOT_ME(
        displayName = "不是本人操作",
        description = "我没有发起恢复请求"
    ),

    /**
     * 设备丢失
     */
    LOST_DEVICE(
        displayName = "设备丢失",
        description = "我的设备可能已被他人获取"
    ),

    /**
     * 助记词泄露
     */
    COMPROMISED(
        displayName = "助记词泄露",
        description = "我的助记词可能已被他人获取"
    ),

    /**
     * 误操作
     */
    MISTAKE(
        displayName = "误操作",
        description = "我不小心发起了恢复"
    ),

    /**
     * 其他原因
     */
    OTHER(
        displayName = "其他原因",
        description = "其他安全相关的原因"
    ),
}
