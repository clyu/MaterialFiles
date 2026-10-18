/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.webdav.client

import android.os.Parcelable
import android.util.Log
import at.bitfire.dav4jvm.BasicDigestAuthHandler
import at.bitfire.dav4jvm.UrlUtils
import kotlinx.parcelize.Parcelize
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

sealed class Authentication : Parcelable {
    abstract fun createAuthenticatorInterceptor(authority: Authority): AuthenticatorInterceptor
}

interface AuthenticatorInterceptor : Authenticator, Interceptor

@Parcelize
data object NoneAuthentication : Authentication() {
    override fun createAuthenticatorInterceptor(authority: Authority): AuthenticatorInterceptor =
        object : AuthenticatorInterceptor {
            override fun authenticate(route: Route?, response: Response): Request? = null

            override fun intercept(chain: Interceptor.Chain): Response =
                chain.proceed(chain.request())
        }
}

@Parcelize
data class PasswordAuthentication(
    val password: String
) : Authentication() {
    override fun createAuthenticatorInterceptor(authority: Authority): AuthenticatorInterceptor =
        object : AuthenticatorInterceptor {
            private val basicDigestAuthHandler = BasicDigestAuthHandler(
                UrlUtils.hostToDomain(authority.host), authority.username, password.toCharArray()
            )

            override fun authenticate(route: Route?, response: Response): Request? =
                if (authority.isOriginOf(response.request.url)) {
                    basicDigestAuthHandler.authenticate(route, response)
                } else {
                    null
                }

            override fun intercept(chain: Interceptor.Chain): Response =
                if (authority.isOriginOf(chain.request().url)) {
                    basicDigestAuthHandler.intercept(chain)
                } else {
                    chain.proceed(chain.request())
                }
        }
}

@Parcelize
data class AccessTokenAuthentication(
    val accessToken: String
) : Authentication() {
    override fun createAuthenticatorInterceptor(authority: Authority): AuthenticatorInterceptor =
        object : AuthenticatorInterceptor {
            override fun authenticate(route: Route?, response: Response): Request? = null

            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                if (!authority.isOriginOf(request.url)) {
                    Log.w(
                        LOG_TAG,
                        "Not authenticating against ${request.url.redact()} because it isn't on" +
                            " the same origin"
                    )
                    return chain.proceed(request)
                }
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                return chain.proceed(newRequest)
            }
        }

    companion object {
        private val LOG_TAG = AccessTokenAuthentication::class.java.simpleName
    }
}

// Only send credentials to the server itself, instead of any host in the same domain that a
// redirect may point to, or over plain HTTP that a redirect may downgrade to.
private fun Authority.isOriginOf(url: HttpUrl): Boolean {
    // Built in the same way as WebDavPath.url, so that the host is canonicalized in the same way.
    val originUrl = HttpUrl.Builder()
        .scheme(protocol.httpScheme)
        .host(host)
        .port(port)
        .build()
    return url.scheme == originUrl.scheme && url.host == originUrl.host
        && url.port == originUrl.port
}
