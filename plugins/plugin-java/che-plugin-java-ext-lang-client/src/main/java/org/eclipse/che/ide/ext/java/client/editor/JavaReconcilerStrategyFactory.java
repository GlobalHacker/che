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

import org.eclipse.che.ide.api.editor.annotation.AnnotationModel;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;

/**
 * Factory of reconciler factories for java documents.
 */
public interface JavaReconcilerStrategyFactory {
    JavaReconcilerStrategy create(TextEditor editor,
                                  JavaCodeAssistProcessor codeAssistProcessor,
                                  AnnotationModel annotationModel);
}
