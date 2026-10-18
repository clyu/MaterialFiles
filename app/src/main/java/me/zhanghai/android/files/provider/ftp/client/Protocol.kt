/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import javax.net.ssl.HttpsURLConnection

enum class Protocol(val scheme: String, val defaultPort: Int, val createClient: () -> FTPClient) {
    FTP("ftp", FTPClient.DEFAULT_PORT, ::FTPClient),
    FTPS("ftps", FTPSClient.DEFAULT_FTPS_PORT, { createFtpsClient(true) }),
    FTPES("ftpes", FTPClient.DEFAULT_PORT, { createFtpsClient(false) });

    companion object {
        val SCHEMES = entries.map { it.scheme }

        fun fromScheme(scheme: String): Protocol =
            entries.firstOrNull { it.scheme == scheme } ?: throw IllegalArgumentException(scheme)
    }
}

private fun createFtpsClient(isImplicit: Boolean): FTPSClient =
    FTPSClient(isImplicit).apply {
        // FTPSClient only checks the validity period of server certificates by default, so use the
        // platform trust manager (which respects network security config) and hostname verifier.
        setTrustManager(TrustManagerUtils.getDefaultTrustManager(null))
        setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
    }
