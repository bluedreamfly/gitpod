// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.gitpodprotocol.api;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

import io.gitpod.gitpodprotocol.api.entities.WorkspaceInstance;

public interface GitpodClient {
    void connect(GitpodServer server);

    GitpodServer server();

    @JsonNotification
	void onInstanceUpdate(WorkspaceInstance instance);
}
