package ro.raicabogdan.route;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.jgit.annotations.Nullable;
import org.jetbrains.annotations.Nls;
import ro.raicabogdan.route.util.NotificationUtil;
import ro.raicabogdan.route.util.VirtualFileUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Objects;

public class SettingsForm implements Configurable {
    private final Project project;
    private boolean restartNeeded = false;
    private JCheckBox pluginEnabled;

    private JPanel panel;
    private TextFieldWithBrowseButton pathToConfigTextField;
    private JButton pathToConfigTextFieldReset;

    private JTextField attributeClass;
    private JTextField pathToControllers;

    private JTextField fileExtensionTextField;
    private JButton fileExtensionTextFieldReset;


    private JComboBox<String> preferredQuotes;
    private JCheckBox displayRoute;
    private JTextField defaultFnName;

    public SettingsForm(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Phalcon Route Helper";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        pathToConfigTextField.addBrowseFolderListener(createBrowseFolderListener(pathToConfigTextField.getTextField(), FileChooserDescriptorFactory.createSingleFolderDescriptor()));
        pathToConfigTextFieldReset.addMouseListener(createResetPathButtonMouseListener(pathToConfigTextField.getTextField(), Settings.DEFAULT_ROUTE_CONFIG_PATH));

        fileExtensionTextFieldReset.addMouseListener(createResetPathButtonMouseListener(fileExtensionTextField, Settings.DEFAULT_FILE_EXTENSIONS));

        return panel;
    }

    @Override
    public boolean isModified() {
        if (!pluginEnabled.isSelected() == getSettings().pluginEnabled
            || !displayRoute.isSelected() == getSettings().displayRoute
            || !attributeClass.getText().equals(getSettings().attributeClass)
            || !pathToControllers.getText().equals(getSettings().pathToControllers)
        ) {
            restartNeeded = true;
        }

        return !pluginEnabled.isSelected() == getSettings().pluginEnabled
                || !pathToConfigTextField.getText().equals(getSettings().pathToConfigFile)
                || !displayRoute.isSelected() == getSettings().displayRoute
                || !Objects.requireNonNull(preferredQuotes.getSelectedItem()).toString().equals(getSettings().preferredQuotes)
                || !fileExtensionTextField.getText().equals(getSettings().fileExtensions)
                || !attributeClass.getText().equals(getSettings().attributeClass)
                || !pathToControllers.getText().equals(getSettings().pathToControllers)
                || !defaultFnName.getText().equals(getSettings().defaultFnName);
    }

    @Override
    public void apply() {
        getSettings().pluginEnabled = pluginEnabled.isSelected();

        getSettings().pathToConfigFile = pathToConfigTextField.getText();
        getSettings().displayRoute = displayRoute.isSelected();
        getSettings().preferredQuotes = Objects.requireNonNull(preferredQuotes.getSelectedItem()).toString();
        getSettings().attributeClass = attributeClass.getText();
        getSettings().pathToControllers = pathToControllers.getText();
        getSettings().fileExtensions = fileExtensionTextField.getText();
        getSettings().defaultFnName = defaultFnName.getText();

        if (restartNeeded) {
            if (project != null) {
                NotificationUtil.showRestartNotification(project);
            }
        }
    }

    @Override
    public void reset() {
        updateUIFromSettings();
    }

    private Settings getSettings() {
        return Settings.getInstance(project);
    }

    private void updateUIFromSettings() {

        pluginEnabled.setSelected(getSettings().pluginEnabled);

        pathToConfigTextField.setText(getSettings().pathToConfigFile);
        displayRoute.setSelected(getSettings().displayRoute);
        preferredQuotes.setSelectedItem(getSettings().preferredQuotes);
        attributeClass.setText(getSettings().attributeClass);
        pathToControllers.setText(getSettings().pathToControllers);
        fileExtensionTextField.setText(getSettings().fileExtensions);
        defaultFnName.setText(getSettings().defaultFnName);
    }

    private TextBrowseFolderListener createBrowseFolderListener(final JTextField textField, final FileChooserDescriptor fileChooserDescriptor) {
        return new TextBrowseFolderListener(fileChooserDescriptor) {
            @Override
            public void actionPerformed(ActionEvent e) {
                VirtualFile projectDirectory = VirtualFileUtil.getProjectBaseDir(project);
                if (projectDirectory != null) {
                    VirtualFile selectedFile = FileChooser.chooseFile(
                            fileChooserDescriptor,
                            project,
                            VfsUtil.findRelativeFile(textField.getText(), projectDirectory)
                    );

                    if (null == selectedFile) {
                        return; // Ignore but keep the previous path
                    }

                    String path = VfsUtil.getRelativePath(selectedFile, projectDirectory, '/');
                    if (null == path) {
                        path = selectedFile.getPath();
                    }

                    textField.setText(path);
                }
            }
        };
    }

    private MouseListener createResetPathButtonMouseListener(final JTextField textField, final String defaultValue) {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                textField.setText(defaultValue);
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
            }
        };
    }
}