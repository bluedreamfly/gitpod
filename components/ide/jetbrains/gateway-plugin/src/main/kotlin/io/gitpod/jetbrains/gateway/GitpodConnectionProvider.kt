// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.jetbrains.gateway

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.gitpod.jetbrains.auth.GitpodAuthService
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentials
import com.intellij.ssh.config.OpenSSHConfig
import com.intellij.util.Base64
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.ClientOverSshTunnelConnector
import com.jetbrains.gateway.ssh.connection.SshConnectionHandle
import kotlinx.coroutines.future.await
import java.net.URI

class GitpodConnectionProvider : GatewayConnectionProvider {
    override suspend fun connect(
        parameters: Map<String, String>,
        requestor: ConnectionRequestor
    ): GatewayConnectionHandle? {
        val connectParam = with(jacksonMapper) {
            propertyNamingStrategy = PropertyNamingStrategies.LowerCamelCaseStrategy();
            readValue(parameters["gitpod"], ConnectParam::class.java)
        }

        var credentialsAttributes = CredentialAttributes(generateServiceName("Gitpod", "gitpod.io"))
        var accessToken = PasswordSafe.instance.getPassword(credentialsAttributes)
        if (accessToken == null) {
            accessToken = GitpodAuthService.instance.authorize(connectParam.gitpodHost).await().accessToken
            PasswordSafe.instance.setPassword(credentialsAttributes, accessToken)
        } else {
            // TODO validate token
        }

        // connect to server
        // wait until workspace is running
        // establish tunnel to supervisor
        // generate SSH keys
        // install SSH keys
        // establish tunnel to ssh server

        var configFile = OpenSSHConfig.parseFile("/tmp/gitpod_ssh_config")
        var config = configFile.getConfig("gold-alpaca-k2l7apgv")
        var hostname = config.hostname ?: return null;
        var identityFile = config.getValue("IdentityFile") ?: return null;
        val connector = ClientOverSshTunnelConnector(
            com.jetbrains.rd.util.lifetime.Lifetime.Eternal,
            object: RemoteCredentials {
                override fun getHost(): String {
                    return hostname
                }

                override fun getPort(): Int {
                    return config.port
                }

                override fun getLiteralPort(): String {
                    return config.port.toString()
                }

                override fun getUserName(): String? {
                    return config.user;
                }

                override fun getPassword(): String? {
                    return null
                }

                override fun getPassphrase(): String? {
                    return null
                }

                override fun getAuthType(): AuthType {
                    return AuthType.KEY_PAIR
                }

                override fun getPrivateKeyFile(): String {
                    return identityFile
                }

                override fun isStorePassword(): Boolean {
                    return false
                }

                override fun isStorePassphrase(): Boolean {
                    return false
                }
            },
            URI("tcp://127.0.0.1:5990#jt=381dc6c7-6f97-4a3a-acf2-bdcbff747c1c&fp=B06ADFECBAE5C92A1A3AD0CE76577CBE56E473B8F7E7A83869A4D3433DC30221&cb=213.5744.223&jb=11_0_13b1751.19")
        )
        val client = connector.connect()
        return SshConnectionHandle(client.lifetime, client.uid);
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean =
        parameters.containsKey("gitpod")

    private data class ConnectParam(val gitpodHost: String)

    companion object {
        private val jacksonMapper = jacksonObjectMapper()
    }
}
