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
package org.eclipse.che.plugin.maven.server.core.reconcile;

import com.google.inject.Inject;

import org.eclipse.che.api.core.jsonrpc.JsonRpcException;
import org.eclipse.che.api.core.jsonrpc.RequestHandlerConfigurator;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;

import java.util.List;

import static java.lang.String.format;

/**
 * @author Roman Nikitenko
 */
public class PomReconcileRequestHandler {
//    private static final String INCOMING_METHOD = "request:pom-reconcile";

    private PomReconciler reconciler;

    @Inject
    public PomReconcileRequestHandler(PomReconciler pomReconciler) {
        this.reconciler = pomReconciler;
    }

//    @Inject
//    public void configureHandler(RequestHandlerConfigurator configurator) {
//        configurator.newConfiguration()
//                    .methodName(INCOMING_METHOD)
//                    .paramsAsString()
//                    .resultAsDto(ReconcileResult.class)
//                    .withFunction(this::getReconcileOperation);
//    }

    private ReconcileResult getReconcileOperation(String endpointId, String pomPath) {
        try {
            List<Problem> problems = reconciler.reconcile(pomPath);
            DtoFactory dtoFactory = DtoFactory.getInstance();
            return dtoFactory.createDto(ReconcileResult.class)
                             .withFileLocation(pomPath)
                             .withProblems(problems);
        } catch (Exception e) {
            String error = format("Can't reconcile pom: %s, the reason is %s",
                                  pomPath,
                                  e.getLocalizedMessage());
            throw new JsonRpcException(500, error, endpointId);
        }
    }
}
