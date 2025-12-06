package ro.raicabogdan.route.util;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface RoutePsiFileCacheService {
    @Nullable
    PsiFile getRouteConfigPsiFile();

    void setRouteConfigPsiFile(@Nullable PsiFile psiFile);
}
