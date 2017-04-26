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
package org.eclipse.che.plugin.maven.client.editor;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;

/**
 *
 * @author Roman Nikitenko
 */
public class PomReconcileOperationEvent extends GwtEvent<PomReconcileOperationEvent.PomReconcileOperationHandler> {

    public static Type<PomReconcileOperationHandler> TYPE = new Type<>();
    private ReconcileResult reconcileResult;

    /**
     *
     * @param reconcileResult
     *
     */
    public PomReconcileOperationEvent(ReconcileResult reconcileResult) {
        this.reconcileResult = reconcileResult;
    }

    @Override
    public Type<PomReconcileOperationHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(PomReconcileOperationHandler handler) {
        handler.onPomReconcileOperation(reconcileResult);
    }

    /** Apples result of reconcile operation */
    public interface PomReconcileOperationHandler extends EventHandler {
        void onPomReconcileOperation(ReconcileResult reconcileResult);
    }
}
