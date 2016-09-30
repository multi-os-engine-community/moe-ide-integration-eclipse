/*
 * Copyright (C) 2016 Migeran
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.moe.wizards;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.moe.generator.project.Generator;
import org.moe.generator.project.ProjectTemplate;
import org.moe.generator.project.config.Configuration;
import org.moe.gradle.GradleTask;
import org.moe.utils.MessageFactory;

public abstract class AbstractProjectWizard extends Wizard implements INewWizard {

	private XcodeWizardPage xcodeWizardPage;
	private ProjecrSettingsPage projectSettingsPage;
	private String mainClassName = "Main";
	private String error = null;

	public enum TemplateType {
		MasterDetail, PageBased, SingleView, Tabbed, Game
	}

	protected abstract TemplateType getTemplateType();

	public AbstractProjectWizard() {
		super();
		setWindowTitle("New Project");
		setNeedsProgressMonitor(true);
	}

	@Override
	public boolean performFinish() {

		error = null;

		final ProjectTemplate projectTemplate = new ProjectTemplate();

		String organizationName = xcodeWizardPage.getOrganizationName();
		String productName = xcodeWizardPage.getProductName();

		String packageName = "com." + organizationName + "." + productName;
		packageName = packageName.replace(" ", "").trim().toLowerCase();

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath projectPath = projectSettingsPage.getProjectLocationPath();
		IPath path = projectPath == null ? workspace.getRoot().getLocation() : projectPath;
		final String projectName = projectSettingsPage.getProjectName();
		final File projectFile = projectSettingsPage.isUseDefault() ? new File(path.toOSString() , projectName) : new File(path.toOSString());

		projectTemplate.rootPath(projectFile.getAbsolutePath()).packageName(packageName).projectName(productName)
				.organizationName(organizationName).companyIdentifier(xcodeWizardPage.getCompanyIdentifier())
				.mainClassName(mainClassName).keepXcode(true)
				.xcodeProjectPath("xcode").useEclipse(true);

		Configuration configuartion = new Configuration("1.2.+");
		configuartion.setProjectRoot(projectFile);
		configuartion.setGradleVersion(projectSettingsPage.getGradleVersion());

		final Generator projectGenerator = new Generator(configuartion);

		WorkspaceModifyOperation workspaceModifiy = new WorkspaceModifyOperation() {

			@Override
			protected void execute(IProgressMonitor monitor)
					throws CoreException, InvocationTargetException, InterruptedException {

				try {
					projectGenerator.createGradleWrapper();
				} catch (Exception e) {
					throw new CoreException(MessageFactory.getError("Unable create gradle wrapper", e));
				}
				monitor.worked(1);

				monitor.beginTask("Create project...", 4);

				projectTemplate.createProject(getTemplateType().toString().toLowerCase() + ".zip", "1.2.+",
						false);
				monitor.worked(1);

				int gradleResult = -1;
				String gradleOutput = "";

				try {
					GradleTask eclipseTask = new GradleTask(projectFile, "eclipse", null, monitor);
					gradleResult = eclipseTask.run();
					gradleOutput = eclipseTask.getOutput();
				} catch (IOException ignore) {
					System.out.println("Gradle error: " + ignore.getMessage());
				}

				if (gradleResult != 0) {
					throw new CoreException(MessageFactory.getError("Unable run eclipse command: " + gradleOutput));
				}
				monitor.worked(1);

				createProject(projectName, projectFile.getAbsolutePath(), monitor);
				monitor.worked(1);
			}
		};

		try {
			getContainer().run(true, true, workspaceModifiy);
		} catch (InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if (t instanceof CoreException) {
				error = "Unable generate project: " + ((CoreException)t).getStatus().getMessage();
			} else {
				error = "Unable generate project: " + e.getMessage();
			}
		} catch (InterruptedException e) {
			error = "Unable generate project: " + e.getMessage();
		}

		if (error != null) {
			MessageFactory.showErrorDialog("Error", error);
			return false;
		}

		return true;
	}

	@Override
	public void init(IWorkbench iWorkbench, IStructuredSelection iStructuredSelection) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addPages() {
		super.addPages();

		xcodeWizardPage = new XcodeWizardPage("Xcode Settings");
		projectSettingsPage = new ProjecrSettingsPage("Project Settings");
		addPage(xcodeWizardPage);
		addPage(projectSettingsPage);
	}
	
	private void createProject(String projectName, String projectPath, IProgressMonitor monitor) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProject project = workspace.getRoot().getProject(projectName);
		if (project != null) {
			IProjectDescription description = workspace.newProjectDescription(projectName);
			description.setName(projectName);
			IPath path = new Path(projectPath);
			description.setLocation(path);
			project.create(description, monitor);
			project.open(monitor);
		}
	}

}
