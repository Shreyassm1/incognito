package com.incognito.vault.crypto

enum class MediaType(val code: Byte) {
    PHOTO(0x01),
    VIDEO(0x02);

    companion object {
        fun fromCode(code: Byte): MediaType = when (code) {
            PHOTO.code -> PHOTO
            VIDEO.code -> VIDEO
            else -> throw IllegalArgumentException("Unknown media type code: $code")
        }
    }
}
