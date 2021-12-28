// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.jetbrains.gateway

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.rd.util.concurrentMapOf
import com.jetbrains.rd.util.lifetime.Lifetime
import io.gitpod.gitpodprotocol.api.ConnectionHelper
import io.gitpod.gitpodprotocol.api.GitpodClient
import io.gitpod.gitpodprotocol.api.GitpodServer
import io.gitpod.gitpodprotocol.api.entities.WorkspaceInstance
import io.gitpod.jetbrains.auth.GitpodAuthService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.swing.JComponent

class GitpodConnectionProvider : GatewayConnectionProvider {
    override suspend fun connect(
        parameters: Map<String, String>,
        requestor: ConnectionRequestor
    ): GatewayConnectionHandle? {
        val connectParams = with(jacksonMapper) {
            propertyNamingStrategy = PropertyNamingStrategies.LowerCamelCaseStrategy();
            readValue(parameters["gitpod"], ConnectParams::class.java)
        }

        var credentialsAttributes = CredentialAttributes(generateServiceName("Gitpod", "gitpod.io"))
        var accessToken = PasswordSafe.instance.getPassword(credentialsAttributes)
        if (accessToken == null) {
            accessToken = GitpodAuthService.instance.authorize(connectParams.gitpodHost).await().accessToken
            PasswordSafe.instance.setPassword(credentialsAttributes, accessToken)
        } else {
            // TODO validate token
        }

        val client = GitpodClientImpl()
        val updates = client.listenToWorkspace(connectParams.workspaceId)

        val serverConnection = ConnectionHelper()
        // TODO(ak) handle reconnections
        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/360003146180/comments/360000376240
            Thread.currentThread().contextClassLoader = this::class.java.classLoader
            serverConnection.connect(
                "wss://${connectParams.gitpodHost}/api/v1",
                "https://${connectParams.gitpodHost}",
                accessToken, client
            )
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader;
        }

        coroutineScope {
            launch {
                for (update in updates) {
                    thisLogger().error("update" + update.id + " " + update.status.phase)
                }
            }
        }


        // wait until workspace is running
        // establish tunnel to supervisor
        // generate SSH keys
        // install SSH keys
        // establish tunnel to ssh server

        /* var configFile = OpenSSHConfig.parseFile("/tmp/gitpod_ssh_config")
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
        return SshConnectionHandle(client.lifetime, client.uid); */
        return GitpodConnectionHandle(connectParams);
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean =
        parameters.containsKey("gitpod")

    private data class ConnectParams(val gitpodHost: String, val workspaceId: String)

    private class GitpodConnectionHandle(
        private val params: ConnectParams
    ) : GatewayConnectionHandle(Lifetime.Eternal) {
        override fun createComponent(): JComponent {
            return panel {

            }
        }

        override fun getTitle(): String {
            return "Gitpod: ${params.workspaceId}"
        }

        override fun hideToTrayOnStart(): Boolean {
            return false
        }
    }

    private class GitpodClientImpl() : GitpodClient {

        private val updates = concurrentMapOf<String, Channel<WorkspaceInstance>?>()

        private var server: GitpodServer? = null;

        override fun connect(server: GitpodServer?) {
            this.server = server;
            if (server == null) {
                return
            }
            val workspaceIds = updates.keys
            // TODO(ak) runBlocking here looks expensive
            runBlocking {
                launch {
                    for (id in workspaceIds) {
                        try {
                            val info = server.getWorkspace(id).await()
                            onInstanceUpdate(info.latestInstance)
                        } catch (t: Throwable) {
                            thisLogger().error("failed to sync workspace", t)
                        }
                    }
                }
            }
        }

        override fun server(): GitpodServer {
            if (this.server == null) {
                throw IllegalStateException("not connected")
            }
            return this.server!!;
        }

        override fun onInstanceUpdate(instance: WorkspaceInstance?) {
            if (instance == null) {
                return;
            }
            // TODO(ak) sync phases, see how it is done in supervisor frontend
            val channel = updates[instance.id] ?: return
            // TODO(ak) runBlocking here looks expensive
            runBlocking {
                launch {
                    channel.send(instance)
                }
            }
        }

        // TODO(ak) allow client to close
        fun listenToWorkspace(workspaceId: String): ReceiveChannel<WorkspaceInstance> {
            val updates = Channel<WorkspaceInstance>()
            this.updates[workspaceId] = updates
            return updates;
        }

    }

    companion object {
        private val jacksonMapper = jacksonObjectMapper()
    }
}
