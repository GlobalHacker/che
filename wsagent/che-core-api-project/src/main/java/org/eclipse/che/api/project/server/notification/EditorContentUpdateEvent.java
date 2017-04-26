/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.project.server.notification;

import org.eclipse.che.api.project.shared.dto.EditorChangesDto;

/**
 * Describe changes of working copy.
 *
 * @author Roman Nikitenko
 */
public class EditorContentUpdateEvent {

    private final String           endpointId;
    private final EditorChangesDto textChange;

    public EditorContentUpdateEvent(String endpointId, EditorChangesDto textChange) {
        this.endpointId = endpointId;
        this.textChange = textChange;
    }

    public String getEndpointId() {
        return endpointId;
    }


    public EditorChangesDto getChanges() {
        return textChange;
    }
}
