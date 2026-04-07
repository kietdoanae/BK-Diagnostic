package com.example.bkdiagnostic.diagnostic

enum class SendStatus { PENDING, ACK, ERROR, TIMEOUT }

data class CanResponseEntry(
    val canId: Int,
    val dataBytes: ByteArray,
    val receivedAfterMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanResponseEntry) return false
        return canId == other.canId &&
               dataBytes.contentEquals(other.dataBytes) &&
               receivedAfterMs == other.receivedAfterMs
    }
    override fun hashCode(): Int {
        var r = canId
        r = 31 * r + dataBytes.contentHashCode()
        r = 31 * r + receivedAfterMs.hashCode()
        return r
    }
}

data class CanSendEntry(
    val seq: Int,
    val timestampMs: Long,
    val canId: Int,
    val dataBytes: ByteArray,
    val dlc: Int,
    val status: SendStatus,
    val roundTripMs: Long? = null,
    val errorMsg: String? = null,
    val responses: List<CanResponseEntry> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanSendEntry) return false
        return seq == other.seq &&
               timestampMs == other.timestampMs &&
               canId == other.canId &&
               dataBytes.contentEquals(other.dataBytes) &&
               dlc == other.dlc &&
               status == other.status &&
               roundTripMs == other.roundTripMs &&
               errorMsg == other.errorMsg &&
               responses == other.responses
    }
    override fun hashCode(): Int {
        var r = seq
        r = 31 * r + timestampMs.hashCode()
        r = 31 * r + canId
        r = 31 * r + dataBytes.contentHashCode()
        r = 31 * r + dlc
        r = 31 * r + status.hashCode()
        r = 31 * r + (roundTripMs?.hashCode() ?: 0)
        r = 31 * r + (errorMsg?.hashCode() ?: 0)
        r = 31 * r + responses.hashCode()
        return r
    }
}
