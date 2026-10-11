package com.example.billing.auth

class HttpResponse(val code: Int, val body: String)

internal expect suspend fun platformHttpCall(
    method: String,
    url: String,
    body: String?,
    timeoutMs: Int,
): HttpResponse

/** Internet par request bhejna. Net na ho to exception phenkta hai (UnknownHost, Timeout...). */
suspend fun httpCall(
    method: String,
    url: String,
    body: String? = null,
    timeoutMs: Int = 6000,
): HttpResponse = platformHttpCall(method, url, body, timeoutMs)
