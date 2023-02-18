package ftp

enum class TransferType(val character: Char) {
    ASCII('A'), BINARY('I');

    companion object {
        fun find(c: Char) = TransferType.values().firstOrNull { it.character == c }
    }
}