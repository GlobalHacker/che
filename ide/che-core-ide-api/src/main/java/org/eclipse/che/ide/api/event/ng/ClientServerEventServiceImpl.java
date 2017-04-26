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
package org.eclipse.che.ide.api.event.ng;

import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.jsonrpc.RequestTransmitter;
import org.eclipse.che.ide.util.loging.Log;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto.Type.MOVE;
import static org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto.Type.RESUME;
import static org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto.Type.START;
import static org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto.Type.STOP;
import static org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto.Type.SUSPEND;

/**
 * @author Roman Nikitenko
 */
@Singleton
public class ClientServerEventServiceImpl implements ClientServerEventService {
    private static final String ENDPOINT_ID      = "ws-agent";
    private static final String OUTCOMING_METHOD = "track:editor-file";

    private final DtoFactory         dtoFactory;
    private final RequestTransmitter requestTransmitter;

    @Inject
    public ClientServerEventServiceImpl(DtoFactory dtoFactory, RequestTransmitter requestTransmitter) {
        this.dtoFactory = dtoFactory;
        this.requestTransmitter = requestTransmitter;
    }


    private Promise<Void> transmit(String path, String oldPath, FileTrackingOperationDto.Type type) {
        Log.error(getClass(), "///// client server operation " + type + " /// " + path);
        final FileTrackingOperationDto dto = dtoFactory.createDto(FileTrackingOperationDto.class)
                                                       .withPath(path)
                                                       .withType(type)
                                                       .withOldPath(oldPath);
        return requestTransmitter.transmitOneToEmty(ENDPOINT_ID, OUTCOMING_METHOD, dto);
    }

    @Override
    public Promise<Void> sendFileTrackingStartEvent(String path) {
        return transmit(path, "", START);
    }

    @Override
    public Promise<Void> sendFileTrackingStopEvent(String path) {
        return transmit(path, "", STOP);
    }

    @Override
    public Promise<Void> sendFileTrackingSuspendEvent() {
        return transmit("", "", SUSPEND);
    }

    @Override
    public Promise<Void> sendFileTrackingResumeEvent() {
        return transmit("", "", RESUME);
    }

    @Override
    public Promise<Void> sendFileTrackingMoveEvent(String oldPath, String newPath) {
        return transmit(oldPath, newPath, MOVE);
    }
}
