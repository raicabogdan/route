package ro.raicabogdan.route;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ro.raicabogdan.route.util.NotificationUtil;
import ro.raicabogdan.route.util.VirtualFileUtil;

import java.util.Collection;

public class RouteProjectComponent {
    public static class PostStartupActivity implements ProjectActivity, DumbAware {
        @Nullable
        @Override
        public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
                DumbService.getInstance(project).runWhenSmart(() -> ReadAction.run(() -> checkIfProjectHasPluginEnabled(project)));
            }

            return Unit.INSTANCE;
        }

        /**
         * Match a file path against a glob pattern
         * Supports * (any characters except /) and ** (any characters including /)
         */
        private static boolean matchesGlobPattern(String path, String pattern) {
            // Convert glob pattern to regex
            String regex = pattern
                .replace("\\", "/")  // Normalize path separators
                .replace(".", "\\.")  // Escape dots
                .replace("**/", "DOUBLE_STAR_PLACEHOLDER/")  // Temporarily replace **
                .replace("**", "DOUBLE_STAR_PLACEHOLDER")    // Temporarily replace **
                .replace("*", "[^/]*")  // * matches any character except /
                .replace("DOUBLE_STAR_PLACEHOLDER", ".*");  // ** matches any character including /

            // Normalize the path
            String normalizedPath = path.replace("\\", "/");

            return normalizedPath.matches(regex);
        }
    }

    private static void checkIfProjectHasPluginEnabled(@NotNull Project project) {
        VirtualFile baseDir = VirtualFileUtil.getProjectBaseDir(project);
        boolean hasConfigPath = baseDir != null &&
                VfsUtil.findRelativeFile(baseDir, Settings.DEFAULT_ROUTE_CONFIG_PATH) != null;
        boolean hasRouteAttribute = phpClassExists(project, "App\\Attributes\\Route");

        if(!isEnabled(project) && !Settings.getInstance(project).dismissEnableNotification
                && (hasConfigPath || hasRouteAttribute)) {
            NotificationUtil.showEnableMessage(project);
        }
    }

    public static boolean isEnabled(@Nullable Project project) {
        return project != null && Settings.getInstance(project).pluginEnabled;
    }

    public static boolean psiElementIsValid(@Nullable PsiElement psiElement) {
        return psiElement != null && isEnabled(psiElement.getProject());
    }

    /**
     * Checks if a PHP class exists in the project by its fully qualified name.
     *
     * @param project The project to search in
     * @param fqn The fully qualified class name (e.g., "App\\Attributes\\Route")
     * @return true if the class exists, false otherwise
     */
    public static boolean phpClassExists(@NotNull Project project, @NotNull String fqn) {
        return ReadAction.compute(() -> {
            PhpIndex phpIndex = PhpIndex.getInstance(project);
            Collection<PhpClass> classes = phpIndex.getAnyByFQN(fqn);
            return !classes.isEmpty();
        });
    }

    /**
     * Gets the first PHP class matching the fully qualified name.
     *
     * @param project The project to search in
     * @param fqn The fully qualified class name (e.g., "App\\Attributes\\Route")
     * @return The PhpClass if found, null otherwise
     */
    @Nullable
    public static PhpClass getPhpClass(@NotNull Project project, @NotNull String fqn) {
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Collection<PhpClass> classes = phpIndex.getAnyByFQN(fqn);
        return classes.isEmpty() ? null : classes.iterator().next();
    }
}