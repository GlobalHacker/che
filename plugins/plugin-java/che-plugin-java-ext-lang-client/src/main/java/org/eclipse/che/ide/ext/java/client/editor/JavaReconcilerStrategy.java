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

import com.google.common.base.Optional;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.editor.EditorWithErrors;
import org.eclipse.che.ide.api.editor.annotation.AnnotationModel;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.reconciler.DirtyRegion;
import org.eclipse.che.ide.api.editor.reconciler.ReconcilingStrategy;
import org.eclipse.che.ide.api.editor.text.Region;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.api.event.ng.FileTrackingEvent;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.ext.java.client.JavaLocalizationConstant;
import org.eclipse.che.ide.ext.java.client.editor.JavaReconcileOperationEvent.JavaReconcileOperationHandler;
import org.eclipse.che.ide.ext.java.client.util.JavaUtil;
import org.eclipse.che.ide.ext.java.shared.dto.HighlightedPosition;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;
import org.eclipse.che.ide.project.ResolvingProjectStateHolder;
import org.eclipse.che.ide.project.ResolvingProjectStateHolder.ResolvingProjectState;
import org.eclipse.che.ide.project.ResolvingProjectStateHolder.ResolvingProjectStateListener;
import org.eclipse.che.ide.project.ResolvingProjectStateHolderRegistry;
import org.eclipse.che.ide.util.loging.Log;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.eclipse.che.ide.project.ResolvingProjectStateHolder.ResolvingProjectState.IN_PROGRESS;

public class JavaReconcilerStrategy implements ReconcilingStrategy, ResolvingProjectStateListener, JavaReconcileOperationHandler {
    private final TextEditor                          editor;
    private final JavaCodeAssistProcessor             codeAssistProcessor;
    private final AnnotationModel                     annotationModel;
    private final SemanticHighlightRenderer           highlighter;
    private final ResolvingProjectStateHolderRegistry resolvingProjectStateHolderRegistry;
    private final JavaLocalizationConstant            localizationConstant;
    private final JavaReconcileClient                 client;

    private EditorWithErrors            editorWithErrors;
    private ResolvingProjectStateHolder resolvingProjectStateHolder;
    private HashSet<HandlerRegistration> handlerRegistrations = new HashSet<>(2);

    @AssistedInject
    public JavaReconcilerStrategy(@Assisted @NotNull final TextEditor editor,
                                  @Assisted final JavaCodeAssistProcessor codeAssistProcessor,
                                  @Assisted final AnnotationModel annotationModel,
                                  final JavaReconcileClient client,
                                  final SemanticHighlightRenderer highlighter,
                                  final ResolvingProjectStateHolderRegistry resolvingProjectStateHolderRegistry,
                                  final JavaLocalizationConstant localizationConstant,
                                  final EventBus eventBus) {
        this.editor = editor;
        this.client = client;
        this.codeAssistProcessor = codeAssistProcessor;
        this.annotationModel = annotationModel;
        this.highlighter = highlighter;
        this.resolvingProjectStateHolderRegistry = resolvingProjectStateHolderRegistry;
        this.localizationConstant = localizationConstant;
        if (editor instanceof EditorWithErrors) {
            this.editorWithErrors = ((EditorWithErrors)editor);
        }

        HandlerRegistration reconcileOperationHandlerRegistration = eventBus.addHandler(JavaReconcileOperationEvent.TYPE, this);
        handlerRegistrations.add(reconcileOperationHandlerRegistration);

        HandlerRegistration fileTrackingHandlerRegistration = eventBus.addHandler(FileTrackingEvent.TYPE, this::onFileTrackingEvent);
        handlerRegistrations.add(fileTrackingHandlerRegistration);
    }

    @Override
    public void setDocument(final Document document) {
        highlighter.init(editor.getEditorWidget(), document);

        if (getFile() instanceof Resource) {
            final Optional<Project> project = ((Resource)getFile()).getRelatedProject();

            if (!project.isPresent()) {
                return;
            }

            String projectType = project.get().getType();
            resolvingProjectStateHolder = resolvingProjectStateHolderRegistry.getResolvingProjectStateHolder(projectType);
            if (resolvingProjectStateHolder == null) {
                return;
            }
            resolvingProjectStateHolder.addResolvingProjectStateListener(this);

            if (isProjectResolving()) {
                disableReconciler(localizationConstant.codeAssistErrorMessageResolvingProject());
            }
        }
    }

    @Override
    public void reconcile(final DirtyRegion dirtyRegion, final Region subRegion) {
        parse();
    }

    void parse() {
        if (getFile() instanceof Resource) {
            final Optional<Project> project = ((Resource)getFile()).getRelatedProject();

            if (!project.isPresent()) {
                return;
            }

            String fqn = JavaUtil.resolveFQN(getFile());
            String projectPath = project.get().getLocation().toString();

            Log.error(getClass(), "333333333 sent reconcile request ");
            client.reconcile(fqn, projectPath).then(reconcileResult -> {
                Log.error(getClass(), "333333333 reconcile request success");
                if (isProjectResolving()) {
                    disableReconciler(localizationConstant.codeAssistErrorMessageResolvingProject());
                    return;
                } else {
                    codeAssistProcessor.enableCodeAssistant();
                }

                if (reconcileResult == null) {
                    return;
                }

                doReconcile(reconcileResult.getProblems());
                highlighter.reconcile(reconcileResult.getHighlightedPositions());
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError error) throws OperationException {
                    Log.error(getClass(), "333333333 reconcile request failed");
                    Log.info(getClass(), error.getMessage());
                }
            });
        }
    }


    @Override
    public void reconcile(final Region partition) {
        parse();
    }

    public VirtualFile getFile() {
        return editor.getEditorInput().getFile();
    }

    private void doReconcile(final List<Problem> problems) {
        Log.error(getClass(), "7777777777777 doReconcile " + editor.getDocument().getFile().getLocation() + " /// " + problems.size());
        if (this.annotationModel == null) {
            return;
        }
        ProblemRequester problemRequester;
        if (this.annotationModel instanceof ProblemRequester) {
            problemRequester = (ProblemRequester)this.annotationModel;
            problemRequester.beginReporting();
        } else {
            if (editorWithErrors != null) {
                editorWithErrors.setErrorState(EditorWithErrors.EditorState.NONE);
            }
            return;
        }
        try {
            boolean error = false;
            boolean warning = false;
            for (Problem problem : problems) {

                if (!error) {
                    error = problem.isError();
                }
                if (!warning) {
                    warning = problem.isWarning();
                }
                problemRequester.acceptProblem(problem);
            }
            if (editorWithErrors != null) {
                if (error) {
                    editorWithErrors.setErrorState(EditorWithErrors.EditorState.ERROR);
                } else if (warning) {
                    editorWithErrors.setErrorState(EditorWithErrors.EditorState.WARNING);
                } else {
                    editorWithErrors.setErrorState(EditorWithErrors.EditorState.NONE);
                }
            }
        } catch (final Exception e) {
            Log.error(getClass(), e);
        } finally {
            problemRequester.endReporting();
        }
    }

    private void disableReconciler(String errorMessage) {
        codeAssistProcessor.disableCodeAssistant(errorMessage);
        doReconcile(Collections.<Problem>emptyList());
        highlighter.reconcile(Collections.<HighlightedPosition>emptyList());
    }

    @Override
    public void closeReconciler() {
        if (resolvingProjectStateHolder != null) {
            resolvingProjectStateHolder.removeResolvingProjectStateListener(this);
        }
        handlerRegistrations.forEach(HandlerRegistration::removeHandler);
    }

    @Override
    public void onResolvingProjectStateChanged(ResolvingProjectState state) {
        switch (state) {
            case IN_PROGRESS:
                Log.error(getClass(), "************** IN_PROGRESS ");
                disableReconciler(localizationConstant.codeAssistErrorMessageResolvingProject());
                break;
            case RESOLVED:
                Log.error(getClass(), "************** RESOLVED ");
                parse();
                break;
            default:
                break;
        }
    }

    @Override
    public void onJavaReconcileOperation(ReconcileResult reconcileResult) {
        Log.error(getClass(), "7777777777777 onJavaReconcileOperation " + reconcileResult.getFileLocation());
        if (!editor.getEditorInput().getFile().getLocation().toString().equals(reconcileResult.getFileLocation())) {
            Log.error(getClass(), "========= return " + editor.getEditorInput().getFile().getLocation().toString());
            return;
        }

        if (isProjectResolving()) {
            disableReconciler(localizationConstant.codeAssistErrorMessageResolvingProject());
            return;
        } else {
            codeAssistProcessor.enableCodeAssistant();
        }

        doReconcile(reconcileResult.getProblems());
        highlighter.reconcile(reconcileResult.getHighlightedPositions());
    }

    private void onFileTrackingEvent(FileTrackingEvent event) {
        switch (event.getType()) {
            case SUSPENDED: {
                Log.error(getClass(), "888888888 SUSPENDED disableReconciler ");
                disableReconciler(localizationConstant.codeAssistErrorMessageResolvingProject());
                break;
            }

            case RESUMED: {
                Log.error(getClass(), "888888888 RESUMED enableReconciler ");
                codeAssistProcessor.enableCodeAssistant();
                parse();
                break;
            }

            default: {
                break;
            }
        }
    }

    private boolean isProjectResolving() {
        return resolvingProjectStateHolder != null && resolvingProjectStateHolder.getState() == IN_PROGRESS;
    }
}
