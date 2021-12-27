// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.jetbrains.auth

import com.intellij.collaboration.auth.services.OAuthService
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

internal class GitpodAuthCallbackHandler : RestService() {
    private val service: OAuthService<*> get() = GitpodAuthService.instance

    override fun getServiceName(): String = service.name

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        service.handleServerCallback(urlDecoder.path(), urlDecoder.parameters())
        // TODO(ak) render auth result path
        return null
    }
}