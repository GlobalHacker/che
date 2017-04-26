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
package org.eclipse.che.api.project.server;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.project.server.notification.EditorContentUpdateEvent;
import org.eclipse.che.api.project.shared.dto.EditorChangesDto;
import org.eclipse.che.api.project.shared.dto.event.FileTrackingOperationDto;
import org.eclipse.che.api.vfs.impl.file.event.detectors.FileTrackingOperationEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static org.eclipse.che.api.project.shared.Constants.CHE_DIR;

/**
 *
 *
 * @author Roman Nikitenko
 */
@Singleton
public class EditorWorkingCopyManager {
    private static final String WORKING_COPIES_DIR = "/" + CHE_DIR + "/workingCopies";

    private final ProjectManager projectManager;

    @Inject
    public EditorWorkingCopyManager(ProjectManager projectManager,
                                    EventService eventService) {
        this.projectManager = projectManager;

        eventService.subscribe(new EventSubscriber<EditorContentUpdateEvent>() {
            @Override
            public void onEvent(EditorContentUpdateEvent event) {
                onWorkingCopyChanged(event.getChanges());
            }
        });

        eventService.subscribe(new EventSubscriber<FileTrackingOperationEvent>() {
            @Override
            public void onEvent(FileTrackingOperationEvent event) {
                onFileOperation(event.getFileTrackingOperation());
            }
        });
    }

    private void onFileOperation(FileTrackingOperationDto operation) {
        try {
            FileTrackingOperationDto.Type type = operation.getType();
            System.out.println("*********** EditorWorkingCopyManager onFileOperation " + type);
            switch (type) {
                case START: {
                    String path = operation.getPath();
                    String projectPath = projectManager.asFile(path).getProject();
                    VirtualFileEntry workingCopy = getWorkingCopy(path, projectPath);
                    if (workingCopy != null) {
                        System.out.println("*********** EditorWorkingCopyManager working copy already exist !!! ");
                        //TODO check hashes of contents for working copy and base file
                    } else {
                        System.out.println("*********** EditorWorkingCopyManager CREATE working copy ");
                        createWorkingCopy(path, projectPath);
                    }
                    break;
                }
                case STOP: {
                    String path = operation.getPath();
                    String projectPath = projectManager.asFile(path).getProject();
                    //TODO check hashes of contents for working copy and base file
                    VirtualFileEntry workingCopy = getWorkingCopy(path, projectPath);
                    System.out.println("*********** EditorWorkingCopyManager REMOVE working copy ");
                    workingCopy.remove();//TODO handle null pointer
                    break;
                }
                case SUSPEND: {



                    break;
                }
                case RESUME: {

                    break;
                }
                case MOVE: {
                    String oldPath = operation.getOldPath();
                    if (oldPath == null) {
                        return;
                    }

                    String path = operation.getPath();
                    String projectPath = projectManager.asFile(path).getProject();
                    VirtualFileEntry workingCopy = getWorkingCopy(oldPath, projectPath);

                    String newName = getWorkingCopyFileName(path);
                    workingCopy.getVirtualFile().rename(newName);

                    break;
                }
                default: {


                    break;
                }
            }

        } catch (NotFoundException | ServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ForbiddenException e) {
            e.printStackTrace();
        } catch (ConflictException e) {
            e.printStackTrace();
        }
    }

    private void onWorkingCopyChanged(EditorChangesDto changes) {
        try {
            //TODO handle rename file
            String filePath = changes.getFileLocation();
            String projectPath = changes.getProjectPath();
            String text = changes.getText();
            int offset = changes.getOffset();
            int removedCharCount = changes.getRemovedCharCount();
            VirtualFileEntry workingCopy = getWorkingCopy(filePath, projectPath);
            if (workingCopy == null) {
                workingCopy = createWorkingCopy(filePath, projectPath);
            }

            String newContent = null;
            String oldContent = workingCopy.getVirtualFile().getContentAsString();
            System.out.println("*********** EditorWorkingCopyManager onWorkingCopyChanged " + changes.getType());
            switch (changes.getType()) {
                case INSERT: {
                    newContent = new StringBuilder(oldContent).insert(offset, text).toString();
                    break;
                }
                case REMOVE: {
                    if (removedCharCount > 0) {
                        newContent = new StringBuilder(oldContent).delete(offset, offset + removedCharCount).toString();
                    }
                    break;
                }
                case REPLACE_ALL: {
                    newContent = new StringBuilder(oldContent).replace(0, oldContent.length(), text).toString();
                    break;
                }
            }


            if (newContent != null) {
                //TODO
                System.out.println("*********** EditorWorkingCopyManager onWorkingCopyChanged update workingCopy ");
                workingCopy.getVirtualFile().updateContent(newContent);
            }
        } catch (Exception e) {
            //TODO handle exception
            System.out.println(e.getMessage());

        }
    }

    private VirtualFileEntry createWorkingCopy(String filePath, String projectPath)
            throws NotFoundException, ServerException, ConflictException, ForbiddenException, IOException {
        FileEntry file = projectManager.asFile(filePath);//TODO when file null???

        String workingCopyPath = getWorkingCopyFileName(filePath);
        FolderEntry workingCopiesStorage = getWorkingCopiesStorage(projectPath);

        return workingCopiesStorage.createFile(workingCopyPath, file.getInputStream());
    }

    private VirtualFileEntry getWorkingCopy(String filePath, String projectPath)
            throws NotFoundException, ServerException, ConflictException, ForbiddenException, IOException {
        FolderEntry workingCopiesStorage = getWorkingCopiesStorage(projectPath);
        String workingCopyPath = getWorkingCopyFileName(filePath);

        return workingCopiesStorage.getChild(workingCopyPath);
    }

    private FolderEntry getWorkingCopiesStorage(String projectPath)
            throws NotFoundException, ServerException, ConflictException, ForbiddenException {
        RegisteredProject project = projectManager.getProject(projectPath);
        FolderEntry baseFolder = project.getBaseFolder();
        if (baseFolder == null) {
            throw new NotFoundException("Base folder not found for " + projectPath);
        }

        String tempDirectoryPath = baseFolder.getPath().toString() + WORKING_COPIES_DIR;
        FolderEntry workingCopiesStorage = projectManager.asFolder(tempDirectoryPath);
        return workingCopiesStorage != null ? workingCopiesStorage : baseFolder.createFolder(WORKING_COPIES_DIR);
    }

    private String getWorkingCopyFileName(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }
        return path.replace('/', '.');
    }
}
