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
package org.eclipse.che.api.vfs.impl.file.event.detectors;

import org.eclipse.che.api.core.jsonrpc.JsonRpcException;
import org.eclipse.che.api.core.jsonrpc.RequestHandlerConfigurator;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Roman Nikitenko
 */
@Singleton
public class EditorFileOperationHandler {
    private static final String INCOMING_METHOD = "track:editor-file";

    private final EventService eventService;
    private final EditorFileTracker editorFileTracker;

    @Inject
    public EditorFileOperationHandler(EventService eventService, EditorFileTracker editorFileTracker) {
        this.eventService = eventService;
        this.editorFileTracker = editorFileTracker;
    }

    @Inject
    public void configureHandler(RequestHandlerConfigurator configurator) {
        configurator.newConfiguration()
                    .methodName(INCOMING_METHOD)
                    .paramsAsDto(FileTrackingOperationDto.class)
                    .resultAsEmpty()
                    .withFunction(this::handleFileTrackingOperation);
    }

    private Void handleFileTrackingOperation(String endpointId, FileTrackingOperationDto operation) {
        try {
            editorFileTracker.onFileTrackingOperationReceived(endpointId, operation);
            eventService.publish(new FileTrackingOperationEvent(endpointId, operation));
            return null;
        } catch (Exception e) {
            throw new JsonRpcException(500, e.getLocalizedMessage());
        }
    }
}
