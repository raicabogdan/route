package ro.raicabogdan.route.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import ro.raicabogdan.route.Settings;

public class NotificationUtil {
    public static void showRestartNotification(final Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("ro.raicabogdan.route")
                .createNotification("Plugin enabled for project, restart required",
                        "A setting change requires PhpStorm to be restarted for it to take effect.",
                        NotificationType.INFORMATION)
                .addAction(new AnAction("Restart PhpStorm") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        ApplicationManager.getApplication().restart();
                    }
                })
                .notify(project);
    }

    public static void showEnableMessage(final Project project) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("ro.raicabogdan.route")
                .createNotification("Detected configuration file path", NotificationType.INFORMATION);

        notification.addAction(new NotificationAction("Enable plugin") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                Project project1 = e.getProject();
                if (project1 == null) {
                    return;
                }

                Settings.getInstance(project1).pluginEnabled = true;

                showRestartNotification(project);

                notification.expire();
            }
        });

        notification.addAction(new NotificationAction("Don't show again") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                Settings.getInstance(project).dismissEnableNotification = true;
                notification.expire();
            }
        });

        notification.setTitle("Phalcon route");
        notification.notify(project);
    }
}
