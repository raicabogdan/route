package ro.raicabogdan.route.contributor;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.twig.elements.TwigCompositeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ro.raicabogdan.route.Settings;
import ro.raicabogdan.route.RouteProjectComponent;
import ro.raicabogdan.route.util.ElementsUtil;
import ro.raicabogdan.route.util.RouteIndexUtil;

import java.util.*;
import java.util.List;

public class RouteCompletionContributor {

    public static class Completion extends CompletionContributor {
        public Completion() {
            // Matches route('<caret>') in PHP, Twig, and Vue files
            extend(CompletionType.BASIC, PlatformPatterns.or(
                    ElementsUtil.getParameterInsideFunctionReferencePattern(),
                    ElementsUtil.getParameterInsideTwigFunctionReferencePattern(),
                    ElementsUtil.getParameterInsideVueFunctionReferencePattern()
            ), new CompletionProvider<>() {
                @Override
                protected void addCompletions(@NotNull CompletionParameters parameters,
                                              @NotNull ProcessingContext context,
                                              @NotNull CompletionResultSet result) {
                    PsiElement element = parameters.getOriginalPosition();
                    if (element == null) {
                        return;
                    }

                    // Check if plugin is enabled
                    if (!RouteProjectComponent.isEnabled(element.getProject())) {
                        return;
                    }

                    // Check if element is inside a string literal
                    if (!isInsideStringLiteral(element)) {
                        return;
                    }

                    InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(element.getProject());
                    PsiFile file = ilm.getTopLevelFile(element.getContainingFile());
                    Settings settings = Settings.getInstance(file.getProject());

                    // Check if file extension is allowed
                    if (!isAllowedFileExtension(file, settings)) {
                        return;
                    }

                    // Check if we're inside the route function
                    if (!isInsideRouteFunction(element, settings)) {
                        return;
                    }

                    // Fetch all routes and add them to completion
                    Map<String, String> routes = RouteIndexUtil.getAllRoutes(file.getProject());

                    for (Map.Entry<String, String> entry : routes.entrySet()) {
                        LookupElementBuilder lookupElement = LookupElementBuilder.create(entry.getKey());

                        // Add route path as type text if available
                        if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                            lookupElement = lookupElement.withTypeText(entry.getValue());
                        }

                        result.addElement(lookupElement);
                    }
                }
            });
        }

        /**
         * Check if element is inside a string literal (PHP, Twig, or Vue)
         */
        private boolean isInsideStringLiteral(PsiElement element) {
            PsiElement parent = element.getParent();
            return parent instanceof StringLiteralExpression
                    || parent instanceof TwigCompositeElement
                    || parent instanceof JSLiteralExpression;
        }

        /**
         * Check if the file extension is in the allowed list from settings
         */
        private boolean isAllowedFileExtension(PsiFile file, Settings settings) {
            if (settings.fileExtensions == null || settings.fileExtensions.isEmpty()) {
                return false;
            }

            String fileName = file.getName().toLowerCase();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex == -1) {
                return false;
            }

            String fileExtension = fileName.substring(dotIndex + 1);

            Set<String> allowedExtensions = new HashSet<>();
            for (String ext : settings.fileExtensions.split(",")) {
                allowedExtensions.add(ext.trim().replaceFirst("^\\.", "").toLowerCase());
            }

            return allowedExtensions.contains(fileExtension);
        }

        /**
         * Check if we're inside the configured route function
         */
        private boolean isInsideRouteFunction(PsiElement element, Settings settings) {
            PsiElement parent = element.getParent();

            if (parent instanceof TwigCompositeElement) {
                return checkTwigRouteContext((TwigCompositeElement) parent, settings);
            } else if (parent instanceof JSLiteralExpression) {
                return checkJsRouteContext((JSLiteralExpression) parent, settings);
            } else if (parent instanceof StringLiteralExpression) {
                return checkPhpRouteContext(parent, settings);
            }

            return false;
        }

        /**
         * Check if we're inside route() function in PHP
         */
        private boolean checkPhpRouteContext(PsiElement element, Settings settings) {
            PsiElement grandParent = element.getParent().getParent();

            if (!(grandParent instanceof FunctionReference)) {
                return false;
            }

            FunctionReference functionReference = (FunctionReference) grandParent;
            String functionName = functionReference.getName();

            return functionName != null && functionName.equals(settings.defaultFnName);
        }

        /**
         * Check if we're inside route() function in Twig
         */
        private boolean checkTwigRouteContext(TwigCompositeElement element, Settings settings) {
            if (element.getFirstChild() == null || element.getFirstChild().getContext() == null) {
                return false;
            }

            String text = element.getFirstChild().getContext().getText();
            if (text == null) {
                return false;
            }

            String functionPattern = settings.defaultFnName + "(";
            return text.contains(functionPattern);
        }

        /**
         * Check if we're inside route() function in Vue/JavaScript
         */
        private boolean checkJsRouteContext(JSLiteralExpression element, Settings settings) {
            if (element.getParent() == null || element.getParent().getContext() == null) {
                return false;
            }

            String text = element.getParent().getContext().getText();
            if (text == null) {
                return false;
            }

            String functionPattern = settings.defaultFnName + "(";
            return text.contains(functionPattern);
        }
    }

    public static class GotoDeclaration implements GotoDeclarationHandler {
        @Override
        public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
            if (sourceElement == null) {
                return null;
            }

            // Check if element is inside a string literal
            PsiElement parent = sourceElement.getParent();
            if (!(parent instanceof StringLiteralExpression
                    || parent instanceof TwigCompositeElement
                    || parent instanceof JSLiteralExpression)) {
                return null;
            }

            // Check if element matches our patterns
            if (!ElementsUtil.getParameterInsideFunctionReferencePattern().accepts(sourceElement)
                    && !ElementsUtil.getParameterInsideTwigFunctionReferencePattern().accepts(sourceElement)
                    && !ElementsUtil.getParameterInsideVueFunctionReferencePattern().accepts(sourceElement)) {
                return null;
            }

            Project project = sourceElement.getProject();

            // Extract route key by stripping quotes
            String routeKey = sourceElement.getText()
                    .replace("'", "")
                    .replace("\"", "");

            // Find all PSI elements for this route (may include both config file and controller method)
            List<PsiElement> psiElements = RouteIndexUtil.getAllPsiElements(project, routeKey);

            // Filter out invalid elements
            psiElements.removeIf(element -> !RouteProjectComponent.psiElementIsValid(element));

            if (psiElements.isEmpty()) {
                return null;
            }

            return psiElements.toArray(new PsiElement[0]);
        }
    }
}
