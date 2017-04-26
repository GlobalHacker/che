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
package org.eclipse.che.api.project.server;

import org.eclipse.che.api.core.jsonrpc.RequestHandlerConfigurator;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.project.server.notification.EditorContentUpdateEvent;
import org.eclipse.che.api.project.shared.dto.EditorChangesDto;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.BiConsumer;

/**
 *
 *
 * @author Roman Nikitenko
 */
@Singleton
public class EditorChangesTracker {
    private static final String INCOMING_METHOD = "track:editor-changes";

    private final EventService       eventService;

    @Inject
    public EditorChangesTracker(EventService eventService) {
        this.eventService = eventService;
    }

    @Inject
    public void configureHandler(RequestHandlerConfigurator configurator) {
        configurator.newConfiguration()
                    .methodName(INCOMING_METHOD)
                    .paramsAsDto(EditorChangesDto.class)
                    .noResult()
                    .withConsumer(getEditorChangesConsumer());
    }

    private BiConsumer<String, EditorChangesDto> getEditorChangesConsumer() {
        return (String endpointId, EditorChangesDto change) -> eventService.publish(new EditorContentUpdateEvent(endpointId, change));
    }
}
