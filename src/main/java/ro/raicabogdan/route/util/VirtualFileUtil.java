package ro.raicabogdan.route.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class VirtualFileUtil {
    public static @Nullable VirtualFile findRouteConfigFile(String... paths) {
        for (String path : paths) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    public static FileType getFileTypeFromEditor(Editor editor, Project project) {

        if (editor == null || project == null) {
            return null;
        }

        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

        if (psiFile != null) {
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if(virtualFile != null){
                return virtualFile.getFileType();
            }
        }

        return null;
    }

    public static VirtualFile getProjectBaseDir(Project project) {
        if (project == null) {
            return null;
        }

        return ProjectUtil.guessProjectDir(project);
    }
}
