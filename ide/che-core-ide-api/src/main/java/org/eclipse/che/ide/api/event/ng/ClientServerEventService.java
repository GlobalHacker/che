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

import org.eclipse.che.api.promises.client.Promise;

/**
 * @author Roman Nikitenko
 */
public interface ClientServerEventService {

    Promise<Void> sendFileTrackingStartEvent(String path);
    Promise<Void> sendFileTrackingStopEvent(String path);
    Promise<Void> sendFileTrackingSuspendEvent();
    Promise<Void> sendFileTrackingResumeEvent();
    Promise<Void> sendFileTrackingMoveEvent(String oldPath, String newPath);
}
