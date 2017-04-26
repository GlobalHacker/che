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
package org.eclipse.che.api.project.shared.dto;

import org.eclipse.che.dto.shared.DTO;

/**
 * DTO represents the information about the text change of a file.
 *
 * @author Roman Nikitenko
 */
@DTO
public interface EditorChangesDto {
    Type getType();

    /** Returns the offset of the change. */
    int getOffset();

    /** Returns length of the text change. */
    int getLength();


    /** Returns text of the change. */
    String getText();


    /** Returns the ID of the working copy owner */
    String getWorkingCopyOwnerID();


    EditorChangesDto withWorkingCopyOwnerID(String id);

    /** Returns the path to the project that contains the modified file */
    String getProjectPath();

    EditorChangesDto withProjectPath(String path);

    /** Returns the full path to the file that was changed */
    String getFileLocation();


    EditorChangesDto withFileLocation(String fileLocation);

    /** Returns the number of characters removed from the file. */
    int getRemovedCharCount();

    EditorChangesDto withType(Type type);

    EditorChangesDto withRemovedCharCount(int removedCharCount);

    EditorChangesDto withOffset(int offset);

    EditorChangesDto withLength(int length);

    EditorChangesDto withText(String text);

    enum Type {
        INSERT,
        REMOVE,
        REPLACE_ALL
    }
}
