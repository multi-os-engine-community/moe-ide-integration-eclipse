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

package org.moe.runconfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.moe.MOEPlugin;
import org.moe.common.configuration.RemoteSettings;
import org.moe.common.exec.ExecRunner;
import org.moe.common.exec.ExecRunnerBase.ExecRunnerListener;
import org.moe.common.exec.GradleExec;
import org.moe.common.junit.MOEITestRunListener;
import org.moe.common.junit.MOETestResultParser;
import org.moe.preferences.PreferenceConstants;
import org.moe.utils.MessageFactory;
import org.moe.utils.logger.LoggerFactory;

public class ApplicationManager {

	private static final Logger LOG = LoggerFactory.getLogger(ApplicationManager.class);

	public static final String REMOTEBUILD_PROPERTIES_IGNORE = "moe.remotebuild.properties.ignore";
	public static final String REMOTEBUILD_HOST = "moe.remotebuild.host=";
	public static final String REMOTEBUILD_PORT = "moe.remotebuild.port=";
	public static final String REMOTEBUILD_USER = "moe.remotebuild.user=";
	public static final String REMOTEBUILD_KNOWNHOSTS = "moe.remotebuild.knownhosts";
	public static final String REMOTEBUILD_IDENTITY = "moe.remotebuild.identity=";
	public static final String REMOTEBUILD_KEYCHAIN_NAME = "moe.remotebuild.keychain.name=";
	public static final String REMOTEBUILD_KEYCHAIN_PASS = "moe.remotebuild.keychain.pass=";
	public static final String REMOTEBUILD_KEYCHAINLOCKTIMEOUT = "moe.remotebuild.keychain.locktimeout";
	public static final String REMOTEBUILD_GRADLE_REPOSITORIES = "moe.remotebuild.gradle.repositories=";
	public static final String REMOTEBUILD_ENABLED = "moe.remotebuild=";
	public static final String CONFIGURATION_MAVEN = "moe.configuration=";
	public static final String LAUNCHER_SIMULATORS = "moe.launcher.simulators=";
	public static final String SIMULATOR_UDID = "moe.simulator.udid=";
	public static final String DEVICE_UDID = "moe.launcher.devices=";
	public static final String DEBUG_PORT_KEY = "debug_port";
	public static final int DEFAULT_DEBUG_PORT = 8000;
	public static final String DEBUG_REMOTE_PORT_KEY = "debug_remote_port";
	public static final int DEFAULT_DEBUG_REMOTE_PORT = 8000;
	public static final String RUN_ON_SIMULATOR_KEY = "run_on_simulator";
	public static final String DEFAULT_CONFIGURATION = "Debug";
	public static final String CONFIGURATION_KEY = "configuration";
	public static final String SIMULATOR_UUID_KEY = "imulator_uuid";
	public static final String DEVICE_UUID_KEY = "device_uuid";
	public static final String REMOTE_BUILD_ENABLED_KEY = "remoteBuildEnabled";
	public static final String REMOTE_HOST_KEY = "remoteHost";
	public static final String REMOTE_PORT_KEY = "remotePort";
	public static final String REMOTE_USER_KEY = "remoteUser";
	public static final String REMOTE_KNOWN_HOSTS_KEY = "remoteKnownhosts";
	public static final String REMOTE_IDENTITY_KEY = "remoteIdentity";
	public static final String REMOTE_KEYCHAIN_PASS_KEY = "remoteKeychainPass";
	public static final String REMOTE_KEYCHAIN_LOCK_TIMEOUT_KEY = "remoteKeychainLocktimeout";
	public static final String REMOTE_GRADLE_REPOSITORIES_KEY = "remoteGradleRepositories";
	public static final String RUN_JUNIT_TEST_KEY = "run_junit_test";
	public static final String ENVIRONMENT_VARIABLES_KEY = "environment_variables";
	public static final String VM_ARGUMENTS_KEY = "vm_arguments";
	public static final String PROGRAM_ARGUMENTS_KEY = "program_arguments";
	public static final String REMOTE_KEYCHAIN_NAME_KEY = "remoteKeychainName";
	public static final String GRADLE_LOG_LEVEL_PROPERTY = "moe.gradle.log.level=";
	public static final String GRADLE_STACKTRACE_LEVEL_PROPERTY = "moe.gradle.stacktrace.level=";
	public static final String OPEN_DEPLOYMENT_TARGET_DIALOD_KEY = "openDeploymentTargetDialog";

	private IProject project;
	private ILaunchConfiguration launchConfiguration;
	private IProgressMonitor progressMonitor;
	private ILaunch launch;
	private List<String> testArgs = null;
	private String simulatorUDID;
	private String deviceUDID;

	public ApplicationManager(IProject project, ILaunchConfiguration launchConfiguration, ILaunch launch,
			IProgressMonitor progressMonitor) {
		this.project = project;
		this.launchConfiguration = launchConfiguration;
		this.launch = launch;
		this.progressMonitor = progressMonitor;
	}

	public void build() throws CoreException {
		LOG.debug("Build project: " + project.getName());

		if (progressMonitor == null) {
			progressMonitor = new NullProgressMonitor();
		}

		progressMonitor.beginTask("Build...", 4);

		List<String> args = new ArrayList<String>();

		OptionsBuilder optionsBuilder = new OptionsBuilder();

		progressMonitor.subTask("Set Configuration...");

		if (isJUnitTest()) {
			args.add("moeTest");
		} else {
			args.add("moeLaunch");
		}
		
		IPreferenceStore store = MOEPlugin.getDefault().getPreferenceStore();
		String logLevel = store.getString(PreferenceConstants.GRADLE_LOG_LEVEL_KEY);
		if (logLevel != null && !logLevel.isEmpty()) {
            args.add(logLevel);
        }
		String stacktraceLevel = store.getString(PreferenceConstants.GRADLE_STACKTRACE_LEVEL_KEY);
		if (stacktraceLevel != null && !stacktraceLevel.isEmpty()) {
            args.add(stacktraceLevel);
        }
		
		optionsBuilder.push("no-launch");
		optionsBuilder.push("install-on-target");
		optionsBuilder.push("config:" + getConfiguration());

		if (isRemoteBuildEnabled()) {
			args.add("-P" + REMOTEBUILD_PROPERTIES_IGNORE);
			addRemoteBuildArguments(args);
		}

		addSimulatorUDIDArgument("-P", false, args);

		launchBuild(optionsBuilder, args);

	}

	private void addRemoteMavenBuildArguments(List<String> args) throws CoreException {
		args.add("-D" + REMOTEBUILD_ENABLED + "true");
		addRemoteArguments("-D", args);
	}

	private void addRemoteBuildArguments(List<String> args) throws CoreException {
		addRemoteArguments("-P", args);
	}

	private void addRemoteArguments(String prefix, List<String> args) throws CoreException {
		Properties properties = RemoteSettings.getProperties(getRemoteHost(), String.valueOf(getRemotePort()),
				getRemoteUser(), getRemoteKnownHosts(), getRemoteIdentity(),
				getRemoteKeychainName(), getRemoteKeychainPass(),
				String.valueOf(getRemoteKeychainLockTimeout()), getRemoteGradleRepository());
		RemoteSettings.getArguments(prefix, properties, args);
	}

	private void addSimulatorUDIDArgument(String prefix, boolean isMaven, List<String> args) throws CoreException {
		boolean runOnSimulator = isRunOnSimulator();

		if (runOnSimulator) {
			if (isMaven) {
				args.add(prefix + SIMULATOR_UDID + getSimulatoreUdid());
			} else {
				args.add(prefix + LAUNCHER_SIMULATORS + getSimulatoreUdid());
			}
		}
	}

	@SuppressWarnings("restriction")
	public void buildMavenProject() throws CoreException {
		if (progressMonitor == null) {
			progressMonitor = new NullProgressMonitor();
		}

		List<String> args = new ArrayList<String>();
		
		args.add("-Dmoe.install.ontarget=true");
		
		IPreferenceStore store = MOEPlugin.getDefault().getPreferenceStore();
		String logLevel = store.getString(PreferenceConstants.GRADLE_LOG_LEVEL_KEY);
		if (logLevel != null && !logLevel.isEmpty()) {
            args.add("-D" + GRADLE_LOG_LEVEL_PROPERTY + logLevel);
        }
		String stacktraceLevel = store.getString(PreferenceConstants.GRADLE_STACKTRACE_LEVEL_KEY);
		if (stacktraceLevel != null && !stacktraceLevel.isEmpty()) {
            args.add("-D" + GRADLE_STACKTRACE_LEVEL_PROPERTY + stacktraceLevel);
        }

		args.add("-D" + CONFIGURATION_MAVEN + getConfiguration());

		progressMonitor.beginTask("Build...", 4);
		LOG.debug("Start maven build");

		boolean isRemoteBuildEnabled = isRemoteBuildEnabled();

		progressMonitor.subTask("Set Configuration...");

		if (isRemoteBuildEnabled) {
			addRemoteMavenBuildArguments(args);
		}

		addSimulatorUDIDArgument("-D", true, args);

		progressMonitor.worked(1);

		MOEMavenLaunchDelegate delegate = new MOEMavenLaunchDelegate();
		delegate.setRemoteArguments(args);
		LOG.debug("MOEMavenLaunchDelegate set arguments");
		MOEMavenLaunch mavenLaunch = new MOEMavenLaunch(launch);
		delegate.launch(launchConfiguration, ILaunchManager.RUN_MODE, mavenLaunch, progressMonitor);

		IProcess[] process = launch.getProcesses();

		if (process.length < 1) {
			throw new CoreException(MessageFactory.getError("Unable to build project with maven"));
		}

		IProcess mavenProcess = process[0];
		
		StringBuffer errorBuffer = new StringBuffer();
		
		mavenProcess.getStreamsProxy().getOutputStreamMonitor().addListener(new IStreamListener() {
			
			@Override
			public void streamAppended(String line, IStreamMonitor arg1) {
				if (line.startsWith("[ERROR]")) {
					errorBuffer.append(line);
				}
			}
		});

		while (!launch.isTerminated()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOG.error("InterruptedException", e);
			}
		}

		progressMonitor.subTask("Build ended...");

		progressMonitor.worked(1);

		int result = mavenProcess.getExitValue();

		if (result != 0) {
			throw new CoreException(MessageFactory.getError("Maven build error", new Exception(errorBuffer.toString())));
		}

		progressMonitor.subTask("Build successful");
	}

	public void launchBuild(OptionsBuilder optionsBuilder, List<String> args) throws CoreException {
		MessageConsole console = MOEProjectBuildConsole.getBuildConsole();
		console.activate();
		console.clearConsole();

		File f = project.getLocation().toFile();
		GradleExec exec = new GradleExec(f);
		if (optionsBuilder != null) {
			args.add(optionsBuilder.toString());
		}
		exec.getArguments().addAll(args);
		ExecRunner runner = null;
		try {
			runner = exec.getRunner();
		} catch (IOException e) {
			throw new CoreException(MessageFactory.getError("GradleExec error", e));
		}

		StringBuffer errorBuffer = new StringBuffer();
		
		if (runner != null) {
			runner.getBuilder().directory(f);
			final MessageConsoleStream consoleStream = console.newMessageStream();
			runner.setListener(new ExecRunnerListener() {

				@Override
				public void stdout(String message) {
					consoleStream.println(message);
				}

				@Override
				public void stderr(String message) {
					consoleStream.println(message);
					errorBuffer.append(message);
					errorBuffer.append("\n");
				}
			});
		}

		try {
			int result = runner.run(null);
			if (result != 0) {
				throw new CoreException(MessageFactory.getError("Gradle build error", new Exception(errorBuffer.toString())));
			}
			LOG.debug("Build result: " + result);
		} catch (IOException e) {
			throw new CoreException(MessageFactory.getError("Gradle launch error", e));
		}
	}

	public Launcher launchApplication(MOEITestRunListener testListener) throws CoreException {

		boolean isDebug = launch.getLaunchMode().equals(ILaunchManager.DEBUG_MODE);
		boolean runOnSimulator = isRunOnSimulator();

		List<String> args = new ArrayList<String>();
		Map<String, String> vmArgs = new HashMap<String, String>();

		int debugPort = 0;
		int debugRemotePort = 0;

		OptionsBuilder optionsBuilder = new OptionsBuilder();

		List<String> appVMArguments = getVMArguments();
		if (appVMArguments != null) {
			for (String arg : appVMArguments) {
				if (arg != null && !arg.isEmpty()) {
					optionsBuilder.push("vmarg:" + arg);
				}
			}
		}

		List<String> environmentVariables = getEnvironmentVariables();
		if (environmentVariables != null) {
			for (String arg : environmentVariables) {
				if (arg != null && !arg.isEmpty()) {
					optionsBuilder.push("env:" + arg);
				}
			}
		}

		List<String> programArguments = getProgramArguments();
		if (programArguments != null) {
			for (String arg : programArguments) {
				if (arg != null && !arg.isEmpty()) {
					optionsBuilder.push("arg:" + arg);
				}
			}
		}

		if (isJUnitTest()) {
			args.add("moeTest");
			optionsBuilder.push("raw-test-output");
			if (testArgs != null) {
				for (String testArg : testArgs) {
					optionsBuilder.push("arg:" + testArg);
				}
			}
		} else {
			args.add("moeLaunch");
		}
		
		IPreferenceStore store = MOEPlugin.getDefault().getPreferenceStore();
		String logLevel = store.getString(PreferenceConstants.GRADLE_LOG_LEVEL_KEY);
		if (logLevel != null && !logLevel.isEmpty()) {
            args.add(logLevel);
        }
		String stacktraceLevel = store.getString(PreferenceConstants.GRADLE_STACKTRACE_LEVEL_KEY);
		if (stacktraceLevel != null && !stacktraceLevel.isEmpty()) {
            args.add(stacktraceLevel);
        }

		if (isDebug) {
			vmArgs.put("hostname", "localhost");
			vmArgs.put("timeout", "0");
			debugPort = getDebugPort();
			debugRemotePort = getDebugRemotePort();
		}

		optionsBuilder.push("no-build");
		optionsBuilder.push("no-install-on-target");
		optionsBuilder.push("config:" + getConfiguration());

		if (runOnSimulator) {
			args.add("-P" + ApplicationManager.LAUNCHER_SIMULATORS + getSimulatoreUdid());
			if (isDebug) {
				optionsBuilder.push("debug:" + Integer.toString(debugPort));
				vmArgs.put("port", Integer.toString(debugPort));
			}
		} else {
			args.add("-P" + ApplicationManager.DEVICE_UDID + getDeviceUdid());
			if (isDebug) {
				optionsBuilder.push("debug:" + Integer.toString(debugPort) + ":" + Integer.toString(debugRemotePort));
				vmArgs.put("port", Integer.toString(debugRemotePort));
			}
		}

		File projectFile = project.getLocation().toFile();
		GradleExec exec = new GradleExec(projectFile);
		args.add(optionsBuilder.toString());

		exec.getArguments().addAll(args);
		ExecRunner runner = null;
		try {
			runner = exec.getRunner();
		} catch (IOException e) {
			throw new CoreException(MessageFactory.getError("GradleExec error", e));
		}

		Process process = null;
		if (runner != null) {
			runner.getBuilder().directory(projectFile);
		}

		try {
			process = runner.getBuilder().start();
		} catch (IOException e) {
			throw new CoreException(MessageFactory.getError("Gradle launch error", e));
		}

		if (process != null) {
			Launcher launcher = new Launcher(launch, process, vmArgs, progressMonitor);
			if (testListener != null) {
				launcher.setTestResultParser(new MOETestResultParser(testListener));
			}
			launcher.start();
			return launcher;
		} else {
			throw new CoreException(MessageFactory.getError("Gradle launch error, process is null"));
		}
	}

	public static class OptionsBuilder {
		private final StringBuilder builder = new StringBuilder();

		OptionsBuilder push(String value) {
			builder.append(",").append(value.replaceAll(",", "\\\\,"));
			return this;
		}

		@Override
		public String toString() {
			return builder.length() == 1 ? "" : ("-Pmoe.launcher.options=" + builder.substring(1));
		}
	}

	protected int getDebugPort() {
		try {
			return launchConfiguration.getAttribute(DEBUG_PORT_KEY, DEFAULT_DEBUG_PORT);
		} catch (CoreException ignored) {

		}
		return DEFAULT_DEBUG_PORT;
	}

	protected boolean isRunOnSimulator() {
		if (deviceUDID != null) {
			return false;
		}
		try {
			return launchConfiguration.getAttribute(RUN_ON_SIMULATOR_KEY, true);
		} catch (CoreException ignored) {

		}
		return true;
	}

	protected String getConfiguration() {
		try {
			return launchConfiguration.getAttribute(CONFIGURATION_KEY, DEFAULT_CONFIGURATION);
		} catch (CoreException ignored) {

		}
		return DEFAULT_CONFIGURATION;
	}

	protected String getArch() {
		if (isRunOnSimulator()) {
			return "i386";
		}
		return "armv7,arm64";
	}

	protected boolean isJUnitTest() {
		try {
			return launchConfiguration.getAttribute(RUN_JUNIT_TEST_KEY, false);
		} catch (CoreException ignore) {

		}
		return false;
	}

	protected String getSimulatoreUdid() {
		if (simulatorUDID != null) {
			return simulatorUDID;
		}
		try {
			return launchConfiguration.getAttribute(SIMULATOR_UUID_KEY, "");
		} catch (CoreException ignored) {

		}
		return "";
	}

	protected String getDeviceUdid() {
		if (deviceUDID != null) {
			return deviceUDID;
		}
		try {
			return launchConfiguration.getAttribute(DEVICE_UUID_KEY, "");
		} catch (CoreException ignored) {

		}
		return "";
	}

	protected int getDebugRemotePort() {
		try {
			return launchConfiguration.getAttribute(DEBUG_REMOTE_PORT_KEY, DEFAULT_DEBUG_REMOTE_PORT);
		} catch (CoreException ignored) {

		}
		return DEFAULT_DEBUG_REMOTE_PORT;
	}

	protected boolean isRemoteBuildEnabled() {
		try {
			return launchConfiguration.getAttribute(REMOTE_BUILD_ENABLED_KEY, false);
		} catch (CoreException ignored) {

		}
		return false;
	}

	protected String getRemoteHost() {
		try {
			return launchConfiguration.getAttribute(REMOTE_HOST_KEY, "");
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected int getRemotePort() {
		try {
			return launchConfiguration.getAttribute(REMOTE_PORT_KEY, RunConfigurationEditorRemote.DEFAULT_REMOTE_BUILD_PORT);
		} catch (CoreException ignore) {

		}
		return 0;
	}

	protected String getRemoteUser() {
		try {
			return launchConfiguration.getAttribute(REMOTE_USER_KEY, "");
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected String getRemoteKnownHosts() {
		try {
			return launchConfiguration.getAttribute(REMOTE_KNOWN_HOSTS_KEY, "");
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected String getRemoteIdentity() {
		try {
			return launchConfiguration.getAttribute(REMOTE_IDENTITY_KEY, "");
		} catch (CoreException ignore) {

		}
		return "";
	}
	
	protected String getRemoteKeychainName() {
		try {
			return launchConfiguration.getAttribute(REMOTE_KEYCHAIN_NAME_KEY, RunConfigurationEditorRemote.DEFAULT_REMOTE_KEYCHAIN_NAME);
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected String getRemoteKeychainPass() {
		try {
			return launchConfiguration.getAttribute(REMOTE_KEYCHAIN_PASS_KEY, "");
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected String getRemoteGradleRepository() {
		try {
			return launchConfiguration.getAttribute(REMOTE_GRADLE_REPOSITORIES_KEY, RunConfigurationEditorRemote.DEFAULT_REMOTE_BUILD_REPOSITORY);
		} catch (CoreException ignore) {

		}
		return "";
	}

	protected int getRemoteKeychainLockTimeout() {
		try {
			return launchConfiguration.getAttribute(REMOTE_KEYCHAIN_LOCK_TIMEOUT_KEY, RunConfigurationEditorRemote.DEFAULT_REMOTE_BUILD_TIMEOUT);
		} catch (CoreException ignore) {

		}
		return 0;
	}

	public ILaunch getLaunch() {
		return launch;
	}

	public Object getProject() {
		return project;
	}

	public void setTestArguments(List<String> tests) {
		this.testArgs = tests;
	}

	private List<String> getVMArguments() {
		try {
			String arguments = launchConfiguration.getAttribute(VM_ARGUMENTS_KEY, "");
			if (arguments != null && !arguments.isEmpty()) {
				return getArgumentList(arguments);
			}
		} catch (CoreException ignore) {

		}
		return null;
	}

	private List<String> getEnvironmentVariables() {
		try {
			Map<String, String> arguments = launchConfiguration.getAttribute(ENVIRONMENT_VARIABLES_KEY,
					(Map<String, String>) null);
			if (arguments != null) {
				List<String> vmArguments = new ArrayList<>();
				for (String key : arguments.keySet()) {
					String value = arguments.get(key);
					value = value == null ? "" : value;
					vmArguments.add(key + "=" + value);
				}
				return vmArguments;
			}
		} catch (CoreException ignore) {

		}
		return null;
	}

	private List<String> getProgramArguments() {
		try {
			String arguments = launchConfiguration.getAttribute(PROGRAM_ARGUMENTS_KEY, "");
			if (arguments != null && !arguments.isEmpty()) {
				return getArgumentList(arguments);
			}
		} catch (CoreException ignore) {

		}
		return null;
	}

	private List<String> getArgumentList(String content) {
		List<String> args = new ArrayList<>();
		String[] lines = content.split("\n");
		for (String s : lines) {
			args.add(s);
		}
		return args;
	}
	
	public void setSimulatorUDID(String simulatorUDID) {
		this.simulatorUDID = simulatorUDID;
	}
	
	public void setDeviceUDID(String deviceUDID) {
		this.deviceUDID = deviceUDID;
	}

}
