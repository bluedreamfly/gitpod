// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.gitpodprotocol.api.entities;

public class WorkspaceInfo {
    private WorkspaceInstance latestInstance;

    public WorkspaceInstance getLatestInstance() {
        return latestInstance;
    }

    public void setLatestInstance(WorkspaceInstance latestInstance) {
        this.latestInstance = latestInstance;
    }
}
