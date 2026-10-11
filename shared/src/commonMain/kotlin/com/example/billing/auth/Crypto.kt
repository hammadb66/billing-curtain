package com.example.billing.auth

expect fun sha256Hex(input: String): String

expect fun randomSaltHex(): String

fun hashPassword(password: String, salt: String): String {
    var h = sha256Hex("$salt:$password")
    repeat(20_000) { h = sha256Hex("$salt:$h") }
    return h
}
