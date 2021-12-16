/**
 * Copyright (c) 2021 Gitpod GmbH. All rights reserved.
 * Licensed under the GNU Affero General Public License (AGPL).
 * See License-AGPL.txt in the project root for license information.
 */

import { Project, ProjectEnvVar } from "@gitpod/gitpod-protocol";
import { useContext, useEffect, useState } from "react";
import Modal from "../components/Modal";
import { getGitpodService } from "../service/service";
import { ProjectContext } from "./project-context";
import { ProjectSettingsPage } from "./ProjectSettings";

export default function () {
    const { project } = useContext(ProjectContext);
    const [ envVars, setEnvVars ] = useState<ProjectEnvVar[]>([]);
    const [ showAddVariableModal, setShowAddVariableModal ] = useState<boolean>(false);

    const updateEnvVars = async () => {
        if (!project) {
            return;
        }
        const vars = await getGitpodService().server.getProjectEnvironmentVariables(project.id);
        setEnvVars(vars);
    }

    useEffect(() => {
        updateEnvVars();
    }, [project]);

    return <ProjectSettingsPage project={project}>
        {showAddVariableModal && <AddVariableModal project={project} onClose={() => { updateEnvVars(); setShowAddVariableModal(false); }} />}
        <h3>Project Variables</h3>
        <ul>{envVars.map(e => <li>{e.name}</li>)}</ul>
        <button onClick={() => setShowAddVariableModal(true)}>Add Variable</button>
    </ProjectSettingsPage>;
}

function AddVariableModal(props: { project?: Project, onClose: () => void }) {
    const [ name, setName ] = useState<string>("");
    const [ value, setValue ] = useState<string>("");
    const [ error, setError ] = useState<Error | undefined>();

    const addVariable = async () => {
        if (!props.project) {
            return;
        }
        try {
            await getGitpodService().server.setProjectEnvironmentVariable(props.project.id, name, value);
            props.onClose();
        } catch (err) {
            setError(err);
        }
    }

    return <Modal visible={true} onClose={props.onClose}>
        <h3 className="mb-4">Add Variable</h3>
        <div className="border-t border-b border-gray-200 dark:border-gray-800 -mx-6 px-6 py-4 flex flex-col">
            {error && <div className="bg-gitpod-kumquat-light rounded-md p-3 text-gitpod-red text-sm mb-2">
                {error}
            </div>}
            <div>
                <h4>Name</h4>
                <input autoFocus className="w-full" type="text" value={name} onChange={e => setName(e.target.value)} />
            </div>
            <div className="mt-4">
                <h4>Value</h4>
                <input className="w-full" type="text" value={value} onChange={e => setValue(e.target.value)} />
            </div>
        </div>
        <div className="flex justify-end mt-6">
            <button className="secondary" onClick={props.onClose}>Cancel</button>
            <button className="ml-2" onClick={addVariable} >Add Variable</button>
        </div>
    </Modal>;
}