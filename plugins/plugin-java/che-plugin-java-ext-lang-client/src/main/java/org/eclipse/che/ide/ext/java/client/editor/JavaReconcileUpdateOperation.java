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
package org.eclipse.che.ide.ext.java.client.editor;


import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;
import org.eclipse.che.ide.jsonrpc.JsonRpcException;
import org.eclipse.che.ide.jsonrpc.JsonRpcRequestBiOperation;
import org.eclipse.che.ide.jsonrpc.RequestHandlerConfigurator;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 *
 * @author Roman Nikitenko
 */
@Singleton
public class JavaReconcileUpdateOperation implements JsonRpcRequestBiOperation<ReconcileResult> {

    private final EventBus               eventBus;

    @Inject
    public JavaReconcileUpdateOperation(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Inject
    public void configureHandler(RequestHandlerConfigurator configurator) {
        configurator.newConfiguration()
                    .methodName("event:java-reconcile-state-changed")
                    .paramsAsDto(ReconcileResult.class)
                    .noResult()
                    .withOperation(this);
    }

    @Override
    public void apply(String endpointId, ReconcileResult reconcileResult) throws JsonRpcException {
        eventBus.fireEvent(new JavaReconcileOperationEvent(reconcileResult));
    }
}
