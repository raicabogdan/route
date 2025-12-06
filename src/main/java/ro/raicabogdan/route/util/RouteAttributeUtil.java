package ro.raicabogdan.route.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for working with Route attributes on controller methods
 */
public class RouteAttributeUtil {

    /**
     * Result class containing a Route attribute and its associated method
     */
    public static class RouteAttributeInfo {
        public final PhpAttribute attribute;
        public final Method method;

        public RouteAttributeInfo(PhpAttribute attribute, Method method) {
            this.attribute = attribute;
            this.method = method;
        }
    }

    /**
     * Find all Route attributes in a PHP file that are on valid controller methods.
     * Valid methods are:
     * - Public
     * - Ending with "Action"
     * - Owned by the class (not inherited)
     *
     * @param phpFile The PHP file to search
     * @param attributeClassName The FQN of the Route attribute class
     * @return List of RouteAttributeInfo objects
     */
    public static List<RouteAttributeInfo> findRouteAttributesOnMethods(PhpFile phpFile, String attributeClassName) {
        List<RouteAttributeInfo> results = new ArrayList<>();

        Collection<PhpClass> classes = PsiTreeUtil.findChildrenOfType(phpFile, PhpClass.class);

        for (PhpClass phpClass : classes) {
            // Only check methods owned by this class (not inherited)
            Method[] methods = phpClass.getOwnMethods();

            for (Method method : methods) {
                // Only check public methods ending with "Action"
                if (!method.getAccess().isPublic() || !method.getName().endsWith("Action")) {
                    continue;
                }

                // Check if method has Route attribute
                Collection<PhpAttribute> attributes = method.getAttributes();

                if (attributes.isEmpty()) {
                    continue;
                }

                for (PhpAttribute attribute : attributes) {
                    String attributeFQN = attribute.getFQN();

                    // Normalize FQN by removing leading backslash for comparison
                    String normalizedFQN = attributeFQN.startsWith("\\") ? attributeFQN.substring(1) : attributeFQN;
                    String normalizedTarget = attributeClassName.startsWith("\\") ? attributeClassName.substring(1) : attributeClassName;

                    if (normalizedTarget.equals(normalizedFQN)) {
                        results.add(new RouteAttributeInfo(attribute, method));
                    }
                }
            }
        }

        return results;
    }

    /**
     * Extract string value from a PSI element (handles both single and double quotes)
     */
    public static @Nullable String extractStringValue(PsiElement element) {
        if (element instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) element).getContents();
        }
        return null;
    }

    /**
     * Get constructor parameter names and their positions from the attribute class
     * Returns a map of parameter name -> position
     */
    private static Map<String, Integer> getAttributeConstructorParams(Project project, String attributeClassName) {
        Map<String, Integer> paramMap = new HashMap<>();

        PhpIndex phpIndex = PhpIndex.getInstance(project);
        // Normalize class name (remove leading backslash)
        String normalizedClassName = attributeClassName.startsWith("\\")
            ? attributeClassName.substring(1)
            : attributeClassName;

        Collection<PhpClass> classes = phpIndex.getAnyByFQN(normalizedClassName);
        if (classes.isEmpty()) {
            return paramMap;
        }

        PhpClass attributeClass = classes.iterator().next();

        // Find constructor
        Method constructor = attributeClass.getConstructor();
        if (constructor == null) {
            return paramMap;
        }

        // Get constructor parameters
        Parameter[] parameters = constructor.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            paramMap.put(paramName, i);
        }

        return paramMap;
    }

    /**
     * Extract route name from attribute - supports both positional and named parameters
     * Uses fallback approach that doesn't require PhpIndex (safe for indexing phase)
     */
    public static @Nullable String extractRouteNameFromAttribute(PhpAttribute attribute, Project project, String attributeClassName) {
        // First try named parameter (name: 'value')
        String namedValue = extractNamedParameter(attribute, "name");
        if (namedValue != null) {
            return namedValue;
        }

        // Then try positional parameter at common positions
        // Most Route attributes have signature: Route(string $path, string $name = '', ...)
        // So name is typically at position 1 (second parameter)
        PsiElement[] parameters = attribute.getParameters();

        // Try position 1 (second parameter) - most common for Route attributes
        if (parameters.length > 1) {
            String positionalValue = extractStringValue(parameters[1]);
            if (positionalValue != null && !positionalValue.isEmpty()) {
                return positionalValue;
            }
        }

        return null;
    }

    /**
     * Extract named parameter value from attribute
     */
    public static @Nullable String extractNamedParameter(PhpAttribute attribute, String paramName) {
        PsiElement[] parameters = attribute.getParameters();

        for (PsiElement param : parameters) {
            if (param instanceof AssignmentExpression) {
                AssignmentExpression assignment = (AssignmentExpression) param;
                PhpPsiElement variable = assignment.getVariable();

                if (variable != null && paramName.equals(variable.getName())) {
                    PhpPsiElement value = assignment.getValue();
                    if (value != null) {
                        return extractStringValue(value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract the PSI element of a named parameter if its value matches the expected value
     */
    public static @Nullable PsiElement extractNamedParameterElement(PhpAttribute attribute, String paramName, String expectedValue) {
        PsiElement[] parameters = attribute.getParameters();
        for (PsiElement param : parameters) {
            if (param instanceof AssignmentExpression) {
                AssignmentExpression assignment = (AssignmentExpression) param;
                PhpPsiElement variable = assignment.getVariable();
                if (variable != null && paramName.equals(variable.getName())) {
                    PhpPsiElement value = assignment.getValue();
                    if (value instanceof StringLiteralExpression) {
                        StringLiteralExpression literal = (StringLiteralExpression) value;
                        if (expectedValue.equals(literal.getContents())) {
                            return value;
                        }
                    }
                }
            }
        }
        return null;
    }
}
