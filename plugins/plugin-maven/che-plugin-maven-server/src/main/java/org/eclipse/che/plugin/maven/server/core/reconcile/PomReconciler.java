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
import com.google.inject.Singleton;

import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.jsonrpc.RequestTransmitter;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.VirtualFileEntry;
import org.eclipse.che.api.project.server.notification.EditorContentUpdateEvent;
import org.eclipse.che.api.project.shared.dto.EditorChangesDto;
import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;
import org.eclipse.che.api.vfs.Path;
import org.eclipse.che.api.vfs.impl.file.event.detectors.FileTrackingOperationEvent;
import org.eclipse.che.commons.xml.XMLTreeException;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.maven.tools.Model;
import org.eclipse.che.maven.data.MavenProjectProblem;
import org.eclipse.che.plugin.maven.server.core.MavenProjectManager;
import org.eclipse.che.plugin.maven.server.core.project.MavenProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Roman Nikitenko
 */
@Singleton
public class PomReconciler {
    private static final Logger LOG             = LoggerFactory.getLogger(PomReconciler.class);
    private static final String OUTGOING_METHOD = "event:pom-reconcile-state-changed";

    private ProjectManager      projectManager;
    private MavenProjectManager mavenProjectManager;
    private EventService        eventService;
    private RequestTransmitter  transmitter;

    private final List<EventSubscriber> subscribers = new ArrayList<>(2);

    @Inject
    public PomReconciler(ProjectManager projectManager,
                         MavenProjectManager mavenProjectManager,
                         EventService eventService,
                         RequestTransmitter transmitter) {
        this.projectManager = projectManager;
        this.mavenProjectManager = mavenProjectManager;
        this.eventService = eventService;
        this.transmitter = transmitter;

        EventSubscriber<FileTrackingOperationEvent> fileOperationEventSubscriber = new EventSubscriber<FileTrackingOperationEvent>() {
            @Override
            public void onEvent(FileTrackingOperationEvent event) {
                onFileOperation(event.getEndpointId(), event.getFileTrackingOperation());
            }
        };
        eventService.subscribe(fileOperationEventSubscriber);
        subscribers.add(fileOperationEventSubscriber);

        EventSubscriber<EditorContentUpdateEvent> editorContentUpdateEventSubscriber = new EventSubscriber<EditorContentUpdateEvent>() {
            @Override
            public void onEvent(EditorContentUpdateEvent event) {
                onEditorContentChanged(event.getEndpointId(), event.getChanges());
            }
        };
        eventService.subscribe(editorContentUpdateEventSubscriber);
        subscribers.add(editorContentUpdateEventSubscriber);
    }

    @PreDestroy
    private void unsubscribe() {
        subscribers.forEach(eventService::unsubscribe);
    }

    public List<Problem> reconcile(String pomPath) {
        VirtualFileEntry entry = null;
        List<Problem> result = new ArrayList<>();
        try {
            entry = projectManager.getProjectsRoot().getChild(pomPath);
            if (entry == null) {
                return result;
            }

            Model.readFrom(entry.getVirtualFile());
            Path path = entry.getPath();
            String pomContent = entry.getVirtualFile().getContentAsString();
            MavenProject mavenProject =
                    mavenProjectManager.findMavenProject(ResourcesPlugin.getWorkspace().getRoot().getProject(path.getParent().toString()));
            if (mavenProject == null) {
                return result;
            }

            List<MavenProjectProblem> problems = mavenProject.getProblems();
            int start = pomContent.indexOf("<project ") + 1;
            int end = start + "<project ".length();

            List<Problem> problemList = problems.stream().map(mavenProjectProblem -> DtoFactory.newDto(Problem.class)
                                                                                               .withError(true)
                                                                                               .withSourceStart(start)
                                                                                               .withSourceEnd(end)
                                                                                               .withMessage(mavenProjectProblem
                                                                                                                    .getDescription()))
                                                .collect(Collectors.toList());
            result.addAll(problemList);
        } catch (ServerException | ForbiddenException | IOException e) {
            LOG.error(e.getMessage(), e);
        } catch (XMLTreeException exception) {
            Throwable cause = exception.getCause();
            if (cause != null && cause instanceof SAXParseException) {
                result.add(createProblem(entry, (SAXParseException)cause));
            }
        }
        return result;
    }

    private void onEditorContentChanged(String endpointId, EditorChangesDto editorChanges) {

    }

    private void onFileOperation(String endpointId, FileTrackingOperationDto operation) {

    }


//    public ReconcileResult reconcile(String pomPath) {
//
//
//
//
//        DtoFactory dtoFactory = DtoFactory.getInstance();
//        return dtoFactory.createDto(ReconcileResult.class)
//                             .withFileLocation(pomPath)
//                             .withProblems(new ArrayList<>());
//
//    }

    private Problem createProblem(VirtualFileEntry entry, SAXParseException spe) {
        Problem problem = DtoFactory.newDto(Problem.class);
        problem.setError(true);
        problem.setMessage(spe.getMessage());
        if (entry != null) {
            int lineNumber = spe.getLineNumber();
            int columnNumber = spe.getColumnNumber();
            try {
                String content = entry.getVirtualFile().getContentAsString();
                Document document = new Document(content);
                int lineOffset = document.getLineOffset(lineNumber - 1);
                problem.setSourceStart(lineOffset + columnNumber - 1);
                problem.setSourceEnd(lineOffset + columnNumber);
            } catch (ForbiddenException | ServerException | BadLocationException e) {
                LOG.error(e.getMessage(), e);
            }

        }
        return problem;
    }

    private void ttt() {
        try {

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
//            String error = format("Can't reconcile pom: %s, the reason is %s", pomPath, e.getLocalizedMessage());
//            throw new JsonRpcException(500, error, endpointId);
        }
    }
}
