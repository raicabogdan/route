package ro.raicabogdan.route.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.Nullable;
import ro.raicabogdan.route.Settings;
import ro.raicabogdan.route.indexing.RouteKeyIndex;

import java.util.*;
import java.util.List;

public class RouteIndexUtil {
    public static Map<String, String> getAllRoutes(Project project) {
        Map<String, String> routes = new HashMap<>();
        FileBasedIndex index = FileBasedIndex.getInstance();
        ID<String, String> indexId = RouteKeyIndex.INDEX_ID;

        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        index.processAllKeys(indexId, key -> {
            List<String> values = index.getValues(indexId, key, scope);
            if (!values.isEmpty()) {
                routes.put(key, values.get(0));
            }
            return true;
        }, project);

        return routes;
    }

    /**
     * Find PSI element(s) for a route key.
     * Returns the first found element (config file takes priority over attributes)
     * For backward compatibility with GotoDeclarationHandler
     */
    public static @Nullable PsiElement getPsiElement(Project project, String routeKey) {
        List<PsiElement> elements = getAllPsiElements(project, routeKey);
        return elements.isEmpty() ? null : elements.get(0);
    }

    /**
     * Find all PSI elements for a route key.
     * Returns all matching definitions (both from config file and attributes)
     */
    public static List<PsiElement> getAllPsiElements(Project project, String routeKey) {
        List<PsiElement> elements = new ArrayList<>();
        Settings settings = Settings.getInstance(project);

        // Try to find in config file
        PsiElement configElement = getPsiElementFromConfigFile(project, routeKey);
        if (configElement != null) {
            elements.add(configElement);
        }

        // Try to find in PHP attributes
        if (settings.attributeClass != null && !settings.attributeClass.isEmpty()) {
            List<PsiElement> attributeElements = getPsiElementsFromAttribute(project, routeKey, settings.attributeClass);
            elements.addAll(attributeElements);
        }

        return elements;
    }

    /**
     * Find PSI element for a route defined in the config file
     */
    private static @Nullable PsiElement getPsiElementFromConfigFile(Project project, String routeKey) {
        RoutePsiFileCacheService cacheService = project.getService(RoutePsiFileCacheService.class);
        PsiFile psiFile;
        PsiFile cachedFile = cacheService.getRouteConfigPsiFile();

        if (cachedFile != null && cachedFile.isValid()) {
            psiFile = cachedFile;
        } else {
            psiFile = getRouteConfigFile(project);
        }

        if (psiFile instanceof PhpFile) {
            PhpFile phpFile = (PhpFile) psiFile;
            // Find setName() calls with this route key
            Collection<MethodReference> methodCalls = PsiTreeUtil.findChildrenOfType(phpFile, MethodReference.class);

            for (MethodReference methodCall : methodCalls) {
                if ("setName".equals(methodCall.getName())) {
                    PsiElement[] parameters = methodCall.getParameters();
                    if (parameters.length > 0 && parameters[0] instanceof StringLiteralExpression) {
                        StringLiteralExpression literal = (StringLiteralExpression) parameters[0];
                        if (routeKey.equals(literal.getContents())) {
                            return parameters[0];
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find PSI elements for a route defined via PHP attribute
     * Only searches in public methods ending with "Action" that are owned by the class
     * Returns the controller METHOD (not just the attribute parameter) for better navigation
     */
    private static List<PsiElement> getPsiElementsFromAttribute(Project project, String routeKey, String attributeClass) {
        List<PsiElement> elements = new ArrayList<>();
        FileBasedIndex index = FileBasedIndex.getInstance();
        ID<String, String> indexId = RouteKeyIndex.INDEX_ID;
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        // Get all files that contain this route key from the index
        Collection<VirtualFile> files = index.getContainingFiles(indexId, routeKey, scope);

        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile instanceof PhpFile) {
                PhpFile phpFile = (PhpFile) psiFile;

                // Find all Route attributes on valid controller methods
                List<RouteAttributeUtil.RouteAttributeInfo> routeAttributes =
                        RouteAttributeUtil.findRouteAttributesOnMethods(phpFile, attributeClass);

                for (RouteAttributeUtil.RouteAttributeInfo info : routeAttributes) {
                    // Check if this attribute has the matching route name
                    String foundRouteName = RouteAttributeUtil.extractRouteNameFromAttribute(
                        info.attribute,
                        project,
                        attributeClass
                    );

                    if (routeKey.equals(foundRouteName)) {
                        // Return the method itself for better navigation to the controller action
                        elements.add(info.method);
                    }
                }
            }
        }

        return elements;
    }

    private static @Nullable PsiFile getRouteConfigFile(Project project) {
        RoutePsiFileCacheService cacheService = project.getService(RoutePsiFileCacheService.class);
        Settings settings = Settings.getInstance(project);
        if (settings.pathToConfigFile == null || settings.pathToConfigFile.isEmpty()) {
            return null;
        }
        String pathToConfigFile = project.getBasePath()+ "/" + settings.pathToConfigFile;

        VirtualFile routeFile = VirtualFileUtil.findRouteConfigFile(pathToConfigFile);
        if (routeFile == null) {
            return null;
        }

        cacheService.setRouteConfigPsiFile(PsiManager.getInstance(project).findFile(routeFile));

        return cacheService.getRouteConfigPsiFile();
    }
}