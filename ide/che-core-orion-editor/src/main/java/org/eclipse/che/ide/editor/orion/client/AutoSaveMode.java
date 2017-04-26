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
package org.eclipse.che.ide.editor.orion.client;

import org.eclipse.che.ide.api.editor.document.UseDocumentHandle;
import org.eclipse.che.ide.api.editor.events.DocumentChangeHandler;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.editor.preferences.editorproperties.EditorProperties;

/**
 * Auto save functionality.
 *
 * @author Roman Nikitenko
 */
public interface AutoSaveMode extends DocumentChangeHandler, UseDocumentHandle {


    void install(TextEditor editor);

    void uninstall();

    /**
     * Turn On auto save mode.
     * Note: If option {@link EditorProperties#ENABLE_AUTO_SAVE} in editor preferences is disabled - do nothing.
     */
    void activate();

    /**
     * Disable auto save.
     */
    void deactivate();

    /**
     * Return true if auto save mode is activated, false otherwise.
     */
    boolean isActivated();

    enum Mode {
        ACTIVATED,
        SUSPENDED,
        RESUMING,
        DEACTIVATED
    }
}
