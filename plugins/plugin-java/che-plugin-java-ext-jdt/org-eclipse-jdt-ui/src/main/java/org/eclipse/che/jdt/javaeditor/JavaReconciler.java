/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.jdt.javaeditor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.jsonrpc.JsonRpcException;
import org.eclipse.che.api.core.jsonrpc.RequestTransmitter;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.project.server.ProjectManager;
import org.eclipse.che.api.project.server.VirtualFileEntry;
import org.eclipse.che.api.project.server.notification.EditorContentUpdateEvent;
import org.eclipse.che.api.project.shared.dto.EditorChangesDto;
import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;
import org.eclipse.che.api.vfs.impl.file.event.detectors.FileTrackingOperationEvent;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.ide.ext.java.shared.dto.HighlightedPosition;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.ClassFileWorkingCopy;
import org.eclipse.jdt.internal.core.JavaModel;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;

/**
 * @author Evgen Vidolob
 * @author Roman Nikitenko
 */
@Singleton
public class JavaReconciler {
    private static final Logger    LOG             = LoggerFactory.getLogger(JavaReconciler.class);
    private static final JavaModel JAVA_MODEL      = JavaModelManager.getJavaModelManager().getJavaModel();
    private static final String    OUTGOING_METHOD = "event:java-reconcile-state-changed";

    private final List<EventSubscriber>         subscribers              = new ArrayList<>(2);
    private final Map<String, WorkingCopyOwner> workingCopyOwnersStorage = new HashMap<>();
    private final Map<String, ProblemRequestor> problemRequestorStorage  = new HashMap<>();

    private final EventService                   eventService;
    private final RequestTransmitter             transmitter;
    private final ProjectManager                 projectManager;
    private final SemanticHighlightingReconciler semanticHighlighting;

    @Inject
    public JavaReconciler(SemanticHighlightingReconciler semanticHighlighting,
                          EventService eventService,
                          RequestTransmitter transmitter,
                          ProjectManager projectManager) {
        this.semanticHighlighting = semanticHighlighting;
        this.eventService = eventService;
        this.transmitter = transmitter;
        this.projectManager = projectManager;

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

    public ReconcileResult reconcile(IJavaProject javaProject, String fqn) throws JavaModelException {
        IType type = getType(fqn, javaProject);
        String filePath = type.getPath().toString();

        WorkingCopyOwner workingCopyOwner = provideWorkingCopyOwner(filePath);
        ProblemRequestor problemRequestor = problemRequestorStorage.get(filePath);

        return reconcile(type.getCompilationUnit(), javaProject, workingCopyOwner, problemRequestor);
    }

    private ReconcileResult reconcile(ICompilationUnit compilationUnit, IJavaProject javaProject, WorkingCopyOwner wcOwner,
                                      ProblemRequestor requestor)
            throws JavaModelException {
        List<HighlightedPosition> positions;
        try {
            final ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);
            requestor.reset();

            final CompilationUnit unit = workingCopy.reconcile(AST.JLS8, true, wcOwner, null);
            positions = semanticHighlighting.reconcileSemanticHighlight(unit);

            if (workingCopy instanceof ClassFileWorkingCopy) {
                //we don't wont to show any errors from ".class" files
                requestor.reset();
            }
        } catch (JavaModelException e) {
            LOG.error(format("Can't reconcile class: %s in project: %s", compilationUnit.getPath().toString(),
                             javaProject.getPath().toOSString()), e);
            throw e;
        }

        DtoFactory dtoFactory = DtoFactory.getInstance();
        return dtoFactory.createDto(ReconcileResult.class)
                         .withFileLocation(compilationUnit.getPath().toOSString())
                         .withProblems(convertProblems(requestor.problems))
                         .withHighlightedPositions(positions);
    }


    private void onEditorContentChanged(String endpointId, EditorChangesDto editorChanges) {
        String filePath = editorChanges.getFileLocation();
        checkPath(filePath, "Can't reconcile class: path for the file should be defined", endpointId);

        String projectPath = editorChanges.getProjectPath();
        checkPath(projectPath, "Can't reconcile class: path for the project should be defined", endpointId);

        try {
            ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
            if (compilationUnit == null) {
                return;
            }

            WorkingCopyOwner wcOwner = provideWorkingCopyOwner(filePath);
            ProblemRequestor requestor = problemRequestorStorage.get(filePath);

            applyChanges(editorChanges, compilationUnit);
            ReconcileResult reconcileResult = reconcile(compilationUnit, getJavaProject(projectPath), wcOwner, requestor);

            transmitter.transmitOneToNone(endpointId, OUTGOING_METHOD, reconcileResult);
        } catch (JavaModelException e) {
            String error =
                    format("Can't reconcile class: %s in project: %s, the reason is %s", filePath, projectPath, e.getLocalizedMessage());
            throw new JsonRpcException(500, error, endpointId);
        }
    }

    private void applyChanges(EditorChangesDto changes, ICompilationUnit compilationUnit) throws JavaModelException {
        String filePath = changes.getFileLocation();
        String text = changes.getText();
        int offset = changes.getOffset();
        int removedCharCount = changes.getRemovedCharCount();

        WorkingCopyOwner wcOwner = provideWorkingCopyOwner(filePath);
        ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);

        TextEdit textEdit = null;
        switch (changes.getType()) {
            case INSERT: {
                textEdit = new InsertEdit(offset, text);
                break;
            }
            case REMOVE: {
                if (removedCharCount > 0) {
                    textEdit = new DeleteEdit(offset, removedCharCount);
                }
                break;
            }
            case REPLACE_ALL: {
                String oldContent = workingCopy.getBuffer().getContents();
                textEdit = new ReplaceEdit(0, oldContent.length(), text);
                break;
            }
        }

        if (textEdit != null) {
            workingCopy.applyTextEdit(textEdit, null);
        }
    }

    private void onFileOperation(String endpointId, FileTrackingOperationDto operation) {
        try {
            String filePath = operation.getPath();
            checkPath(filePath, "Can't handle file operation: path for the file should be defined", endpointId);

            VirtualFileEntry fileEntry = projectManager.getProjectsRoot().getChild(filePath);
            if (fileEntry == null) {
                throw new ServerException("The file is not found by path " + filePath);
            }

            String projectPath = fileEntry.getProject();
            if (isNullOrEmpty(projectPath)) {
                throw new ServerException("The project is not recognized for " + filePath);
            }

            FileTrackingOperationDto.Type operationType = operation.getType();
            switch (operationType) {
                case START: {
                    ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
                    if (compilationUnit == null) {
                        return;
                    }

                    WorkingCopyOwner workingCopyOwner = provideWorkingCopyOwner(filePath);
                    ProblemRequestor problemRequestor = problemRequestorStorage.get(filePath);

                    ReconcileResult result = reconcile(compilationUnit, getJavaProject(projectPath), workingCopyOwner, problemRequestor);
                    transmitter.transmitOneToNone(endpointId, OUTGOING_METHOD, result);
                    break;
                }
                case STOP: {
                    discardWorkingCopy(filePath, projectPath);
                    break;
                }
                case MOVE: {
                    String oldPath = operation.getOldPath();
                    checkPath(oldPath, "Can't handle 'move' file operation: old path for the file should be defined", endpointId);

                    WorkingCopyOwner workingCopyOwner = workingCopyOwnersStorage.remove(oldPath);
                    if (workingCopyOwner != null) {
                        workingCopyOwnersStorage.put(filePath, workingCopyOwner);
                    }

                    ProblemRequestor problemRequestor = problemRequestorStorage.remove(oldPath);
                    if (problemRequestor != null) {
                        problemRequestorStorage.put(filePath, problemRequestor);
                    }
                    break;
                }

                default: {
                    break;
                }
            }
        } catch (ServerException | JavaModelException e) {
            String errorMessage = "Can not handle file operation: " + e.getMessage();

            LOG.error(errorMessage);

            throw new JsonRpcException(500, errorMessage, endpointId);
        }
    }

    private void discardWorkingCopy(String filePath, String projectPath) throws JavaModelException {
        problemRequestorStorage.remove(filePath);
        WorkingCopyOwner wcOwner = workingCopyOwnersStorage.remove(filePath);

        ICompilationUnit compilationUnit = getCompilationUnit(filePath, projectPath);
        if (compilationUnit == null) {
            return;
        }

        ICompilationUnit workingCopy = compilationUnit.getWorkingCopy(wcOwner, null);
        if (workingCopy != null && workingCopy.isWorkingCopy()) {
            try {
                workingCopy.getBuffer().close();
                workingCopy.discardWorkingCopy();
            } catch (JavaModelException e) {
                //ignore
            }
        }
    }

    @Nullable
    private ICompilationUnit getCompilationUnit(String filePath, String projectPath) throws JavaModelException {
        IJavaProject javaProject = getJavaProject(projectPath);
        if (javaProject == null) {
            return null;
        }

        List<IClasspathEntry> classpathEntries = asList(javaProject.getRawClasspath());
        for (IClasspathEntry classpathEntry : classpathEntries) {
            String entryPath = classpathEntry.getPath().toString();
            if (!filePath.contains(entryPath)) {
                continue;
            }

            String fileRelativePath = filePath.substring(entryPath.length() + 1);
            IJavaElement javaElement = javaProject.findElement(new Path(fileRelativePath));
            if (javaElement == null) {
                continue;
            }

            int elementType = javaElement.getElementType();
            if (COMPILATION_UNIT == elementType) {
                return (ICompilationUnit)javaElement;
            }
        }
        return null;
    }

    @Nullable
    private IJavaProject getJavaProject(String projectPath) throws JavaModelException {
        IJavaProject project = JAVA_MODEL.getJavaProject(projectPath);
        List<IJavaProject> javaProjects = asList(JAVA_MODEL.getJavaProjects());

        return javaProjects.contains(project) ? project : null;
    }

    private WorkingCopyOwner provideWorkingCopyOwner(String filePath) {
        if (workingCopyOwnersStorage.containsKey(filePath)) {
            return workingCopyOwnersStorage.get(filePath);
        }

        final ProblemRequestor requestor = new ProblemRequestor();
        final WorkingCopyOwner wcOwner = new WorkingCopyOwner() {
            public IProblemRequestor getProblemRequestor(ICompilationUnit unit) {
                return requestor;
            }

            @Override
            public IBuffer createBuffer(ICompilationUnit workingCopy) {
                return new DocumentAdapter(workingCopy, (IFile)workingCopy.getResource());
            }
        };

        workingCopyOwnersStorage.put(filePath, wcOwner);
        problemRequestorStorage.put(filePath, requestor);

        return wcOwner;
    }

    private List<Problem> convertProblems(List<IProblem> problems) {
        List<Problem> result = new ArrayList<>(problems.size());
        for (IProblem problem : problems) {
            result.add(convertProblem(problem));
        }
        return result;
    }

    private Problem convertProblem(IProblem problem) {
        Problem result = DtoFactory.getInstance().createDto(Problem.class);

        result.setArguments(asList(problem.getArguments()));
        result.setID(problem.getID());
        result.setMessage(problem.getMessage());
        result.setOriginatingFileName(new String(problem.getOriginatingFileName()));
        result.setError(problem.isError());
        result.setWarning(problem.isWarning());
        result.setSourceEnd(problem.getSourceEnd());
        result.setSourceStart(problem.getSourceStart());
        result.setSourceLineNumber(problem.getSourceLineNumber());

        return result;
    }

    private IType getType(String fqn, IJavaProject javaProject) throws JavaModelException {
        checkState(!isNullOrEmpty(fqn), "Incorrect fully qualified name is specified");

        final IType type = javaProject.findType(fqn);
        if (type == null) {
            throw new JavaModelException(new Throwable("Can not find type for " + fqn), 500);
        }

        if (type.isBinary()) {
            throw new JavaModelException(new Throwable("Can't reconcile binary type: " + fqn), 500);
        }
        return type;
    }

    private static void checkPath(String path, String errorMessage, String endpointId) {
        if (isNullOrEmpty(path)) {
            LOG.error(errorMessage);
            throw new JsonRpcException(400, errorMessage, endpointId);
        }
    }

    private class ProblemRequestor implements IProblemRequestor {

        private List<IProblem> problems = new ArrayList<>();

        @Override
        public void acceptProblem(IProblem problem) {
            problems.add(problem);
        }

        @Override
        public void beginReporting() {

        }

        @Override
        public void endReporting() {

        }

        @Override
        public boolean isActive() {
            return true;
        }

        public void reset() {
            problems.clear();
        }
    }
}
