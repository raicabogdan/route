package ro.raicabogdan.route.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import ro.raicabogdan.route.Settings;
import ro.raicabogdan.route.RouteProjectComponent;
import ro.raicabogdan.route.util.RouteAttributeUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteKeyIndex extends FileBasedIndexExtension<String, String> {
    public static final ID<String, String> INDEX_ID = ID.create("ro.raicabogdan.route.index");

    private final DataIndexer<String, String, FileContent> indexer = inputData -> {
        Map<String, String> map = new HashMap<>();

        VirtualFile file = inputData.getFile();
        Settings settings = Settings.getInstance(inputData.getProject());
        PsiFile psiFile = inputData.getPsiFile();

        if (!(psiFile instanceof PhpFile)) {
            return map;
        }

        PhpFile phpFile = (PhpFile) psiFile;

        // Check if this is the route config file
        if (isRouteConfigFile(file, settings)) {
            indexRouteConfigFile(phpFile, settings, map);
        }

        // Check for route attributes only in controller files matching the path pattern
        if (settings.attributeClass != null && !settings.attributeClass.isEmpty()) {
            if (isControllerFile(file, settings, inputData.getProject())) {
                indexRouteAttributes(phpFile, settings, map);
            }
        }

        return map;
    };

    /**
     * Check if the file is the configured route config file
     */
    private boolean isRouteConfigFile(VirtualFile file, Settings settings) {
        if (settings.pathToConfigFile == null || settings.pathToConfigFile.isEmpty()) {
            return false;
        }
        String filePath = file.getPath();
        return filePath.endsWith(settings.pathToConfigFile);
    }

    /**
     * Check if the file matches the controller path pattern
     */
    private boolean isControllerFile(VirtualFile file, Settings settings, Project project) {
        if (settings.pathToControllers == null || settings.pathToControllers.isEmpty()) {
            return false;
        }

        String basePath = project.getBasePath();
        if (basePath == null) {
            return false;
        }

        String filePath = file.getPath();

        // Get relative path from project base
        if (!filePath.startsWith(basePath)) {
            return false;
        }

        String relativePath = filePath.substring(basePath.length() + 1);

        // Convert glob pattern to regex and match
        return matchesGlobPattern(relativePath, settings.pathToControllers);
    }

    /**
     * Match a file path against a glob pattern
     * Supports * (any characters except /) and ** (any characters including /)
     */
    private boolean matchesGlobPattern(String path, String pattern) {
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

    /**
     * Index routes from config file by finding setName() method calls
     */
    private void indexRouteConfigFile(PhpFile phpFile, Settings settings, Map<String, String> map) {
        Collection<MethodReference> methodCalls = PsiTreeUtil.findChildrenOfType(phpFile, MethodReference.class);

        for (MethodReference methodCall : methodCalls) {
            if ("setName".equals(methodCall.getName())) {
                PsiElement[] parameters = methodCall.getParameters();
                if (parameters.length > 0) {
                    String routeName = RouteAttributeUtil.extractStringValue(parameters[0]);
                    if (routeName != null) {
                        String routePath = "";
                        if (settings.displayRoute) {
                            routePath = extractRoutePathFromContext(methodCall);
                        }
                        map.put(routeName, routePath);
                    }
                }
            }
        }
    }

    /**
     * Index routes from PHP attributes like #[Route(..., name: 'route-name')]
     * Only indexes attributes on public methods ending with "Action" that are owned by the class
     */
    private void indexRouteAttributes(PhpFile phpFile, Settings settings, Map<String, String> map) {
        String attributeClassName = settings.attributeClass;

        // Find all Route attributes on valid controller methods
        List<RouteAttributeUtil.RouteAttributeInfo> routeAttributes =
                RouteAttributeUtil.findRouteAttributesOnMethods(phpFile, attributeClassName);

        for (RouteAttributeUtil.RouteAttributeInfo info : routeAttributes) {
            String routeName = RouteAttributeUtil.extractRouteNameFromAttribute(
                info.attribute,
                phpFile.getProject(),
                attributeClassName
            );

            if (routeName != null) {
                String routePath = "";
                if (settings.displayRoute) {
                    // First parameter is typically the route path
                    PsiElement[] parameters = info.attribute.getParameters();
                    if (parameters.length > 0) {
                        routePath = RouteAttributeUtil.extractStringValue(parameters[0]);
                    }
                }
                map.put(routeName, routePath != null ? routePath : "");
            }
        }
    }


    /**
     * Try to extract route path from the context of setName() call
     * This looks for the add() method call that precedes setName()
     */
    private String extractRoutePathFromContext(MethodReference setNameCall) {
        PsiElement parent = setNameCall.getParent();

        // Handle chained method calls: ->add(...)->setName(...)
        if (parent instanceof MethodReference) {
            MethodReference chainedCall = (MethodReference) parent;
            if ("add".equals(chainedCall.getName())) {
                PsiElement[] parameters = chainedCall.getParameters();
                if (parameters.length > 0) {
                    String path = RouteAttributeUtil.extractStringValue(parameters[0]);
                    return path != null ? path : "";
                }
            }
        }

        // Handle variable assignment: $route = $router->add(...); $route->setName(...)
        PsiElement classRef = setNameCall.getClassReference();
        if (classRef instanceof Variable) {
            Variable variable = (Variable) classRef;
            // Find assignment of this variable
            AssignmentExpression assignment = PsiTreeUtil.findChildOfType(
                setNameCall.getContainingFile(),
                AssignmentExpression.class
            );
            if (assignment != null) {
                PhpPsiElement value = assignment.getValue();
                if (value instanceof MethodReference) {
                    MethodReference addCall = (MethodReference) value;
                    if ("add".equals(addCall.getName())) {
                        PsiElement[] parameters = addCall.getParameters();
                        if (parameters.length > 0) {
                            String path = RouteAttributeUtil.extractStringValue(parameters[0]);
                            return path != null ? path : "";
                        }
                    }
                }
            }
        }

        return "";
    }

    @Override
    public @NotNull ID<String, String> getName() {
        return INDEX_ID;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
        return indexer;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return file -> {
            FileType fileType = file.getFileType();
            // Only index PHP files
            return "PHP".equals(fileType.getName());
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 1; // Incremented to force full reindex with new attribute logic
    }
}