// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package io.gitpod.gitpodprotocol.api.entities;

public class WorkspaceInstance {
    private String id;
    private String workspaceId;
    private WorkspaceInstanceStatus status;
    private String ideUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public WorkspaceInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(WorkspaceInstanceStatus status) {
        this.status = status;
    }

    public String getIdeUrl() {
        return ideUrl;
    }

    public void setIdeUrl(String ideUrl) {
        this.ideUrl = ideUrl;
    }
}
