package ro.raicabogdan.route;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "RoutePluginSettings",
        storages = {
                @Storage("phalcon-route.xml")
        }
)
public class Settings implements PersistentStateComponent<Settings>, DumbAware {

    public boolean pluginEnabled = false;
    public boolean dismissEnableNotification = false;
    public static final String DEFAULT_ROUTE_CONFIG_PATH = "config/routes.php";
    public static final String DEFAULT_ROUTE_ATTRIBUTE_CLASS = "App\\Attributes\\Route";
    public static final String DEFAULT_CONTROLLER_PATH = "src/Controllers/*Controller.php";
    public static final boolean DEFAULT_DISPLAY_ROUTE = true;
    public static final String DEFAULT_QUOTE_TYPE = "single";
    public static final String DEFAULT_FILE_EXTENSIONS = "php,volt";
    public static final String DEFAULT_FN_Name = "route";


    public String pathToConfigFile = DEFAULT_ROUTE_CONFIG_PATH;
    public String attributeClass = DEFAULT_ROUTE_ATTRIBUTE_CLASS;
    public String pathToControllers = DEFAULT_CONTROLLER_PATH;
    public boolean displayRoute = DEFAULT_DISPLAY_ROUTE;
    public String preferredQuotes = DEFAULT_QUOTE_TYPE;
    public String fileExtensions = DEFAULT_FILE_EXTENSIONS;
    public String defaultFnName = DEFAULT_FN_Name;

    public static Settings getInstance(Project project) {
        return project.getService(Settings.class);
    }

    @Nullable
    @Override
    public Settings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull Settings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }
}
