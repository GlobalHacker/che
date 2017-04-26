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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

import static org.eclipse.che.ide.api.event.ng.FileTrackingEvent.OperationType.MOVED;
import static org.eclipse.che.ide.api.event.ng.FileTrackingEvent.OperationType.RESUMED;
import static org.eclipse.che.ide.api.event.ng.FileTrackingEvent.OperationType.STARTED;
import static org.eclipse.che.ide.api.event.ng.FileTrackingEvent.OperationType.STOPPED;
import static org.eclipse.che.ide.api.event.ng.FileTrackingEvent.OperationType.SUSPENDED;

/**
 * Consumed by {@link ClientServerEventService} and sent to server side so we could manage
 * server side VFS file watching from client.
 *
 * @author Dmitry Kuleshov
 */
public class FileTrackingEvent extends GwtEvent<FileTrackingEvent.FileTrackingEventHandler> {

    public static Type<FileTrackingEventHandler> TYPE = new Type<>();

    private final String path;

    private final String oldPath;

    private final OperationType type;

    private FileTrackingEvent(String path, String oldPath, OperationType type) {
        this.path = path;
        this.oldPath = oldPath;
        this.type = type;
    }

    public static FileTrackingEvent newFileTrackingSuspendedEvent() {
        return new FileTrackingEvent(null, null, SUSPENDED);
    }

    public static FileTrackingEvent newFileTrackingResumedEvent() {
        return new FileTrackingEvent(null, null, RESUMED);
    }

    public static FileTrackingEvent newFileTrackingStartedEvent(String path) {
        return new FileTrackingEvent(path, null, STARTED);
    }

    public static FileTrackingEvent newFileTrackingStopedEvent(String path) {
        return new FileTrackingEvent(path, null, STOPPED);
    }

    public static FileTrackingEvent newFileTrackingMovedEvent(String path, String oldPath) {
        return new FileTrackingEvent(path, oldPath, MOVED);
    }

    public String getOldPath() {
        return oldPath;
    }

    public OperationType getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    @Override
    public Type<FileTrackingEventHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(FileTrackingEventHandler handler) {
        handler.onEvent(this);
    }

    public interface FileTrackingEventHandler extends EventHandler {
        void onEvent(FileTrackingEvent event);
    }

    public enum OperationType {
        STARTED,
        STOPPED,
        SUSPENDED,
        RESUMED,
        MOVED
    }
}
