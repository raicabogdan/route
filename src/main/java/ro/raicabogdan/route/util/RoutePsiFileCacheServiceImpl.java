package ro.raicabogdan.route.util;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class RoutePsiFileCacheServiceImpl implements RoutePsiFileCacheService {
    private PsiFile routeConfigPsiFile;

    @Override
    public @Nullable PsiFile getRouteConfigPsiFile() {
        return routeConfigPsiFile;
    }

    @Override
    public void setRouteConfigPsiFile(@Nullable PsiFile psiFile) {
        this.routeConfigPsiFile = psiFile;
    }
}
