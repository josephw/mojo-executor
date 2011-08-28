/*
 * Copyright 2008-2011 Don Brown
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.twdata.maven.mojoexecutor;

import static org.twdata.maven.mojoexecutor.PlexusConfigurationUtils.toXpp3Dom;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;

import java.util.List;
import java.util.Map;

/**
 * Executes an arbitrary mojo using a fluent interface.  This is meant to be executed within the context of a Maven 2
 * mojo.
 * <p/>
 * Here is an execution that invokes the dependency plugin:
 * <pre>
 * executeMojo(
 *              plugin(
 *                      groupId("org.apache.maven.plugins"),
 *                      artifactId("maven-dependency-plugin"),
 *                      version("2.0")
 *              ),
 *              goal("copy-dependencies"),
 *              configuration(
 *                      element(name("outputDirectory"), "${project.build.directory}/foo")
 *              ),
 *              executionEnvironment(
 *                      project,
 *                      session,
 *                      pluginManager
 *              )
 *          );
 * </pre>
 */
public class MojoExecutor {
    private static final String FAKE_EXECUTION_ID = "virtual-execution";

    /**
     * Entry point for executing a mojo
     *
     * @param plugin        The plugin to execute
     * @param goal          The goal to execute
     * @param configuration The execution configuration
     * @param env           The execution environment
     * @throws MojoExecutionException If there are any exceptions locating or executing the mojo
     */
    public static void executeMojo(Plugin plugin, String goal, Xpp3Dom configuration, ExecutionEnvironment env)
            throws MojoExecutionException {
        env.executeMojo(plugin, goal, configuration);
    }
    
    public static void executeMojoImpl(Plugin plugin, String goal, Xpp3Dom configuration, ExecutionEnvironmentM2 env)
            throws MojoExecutionException {
        Map<String, PluginExecution> executionMap = null;
        try {
            MavenSession session = env.getMavenSession();


            List buildPlugins = env.getMavenProject().getBuildPlugins();

            String executionId = null;
            if (goal != null && goal.length() > 0 && goal.indexOf('#') > -1) {
                int pos = goal.indexOf('#');
                executionId = goal.substring(pos + 1);
                goal = goal.substring(0, pos);
            }

            // You'd think we could just add the configuration to the mojo execution, but then it merges with the plugin
            // config dominate over the mojo config, so we are forced to fake the config as if it was declared as an
            // execution in the pom so that the merge happens correctly
            if (buildPlugins != null && executionId == null) {
                for (Object buildPlugin : buildPlugins) {
                    Plugin pomPlugin = (Plugin) buildPlugin;

                    if (plugin.getGroupId().equals(pomPlugin.getGroupId())
                            && plugin.getArtifactId().equals(pomPlugin.getArtifactId())) {
                        PluginExecution exec = new PluginExecution();
                        exec.setConfiguration(configuration);
                        executionMap = getExecutionsAsMap(pomPlugin);
                        executionMap.put(FAKE_EXECUTION_ID, exec);
                        executionId = FAKE_EXECUTION_ID;
                        break;
                    }
                }
            }

            PluginDescriptor pluginDescriptor = env.getPluginManager().verifyPlugin(plugin, env.getMavenProject(),
                    session.getSettings(), session.getLocalRepository());
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo(goal);
            if (mojoDescriptor == null) {
                throw new MojoExecutionException("Unknown mojo goal: " + goal);
            }
            MojoExecution exec = mojoExecution2(mojoDescriptor, executionId, configuration);
            env.getPluginManager().executeMojo(env.getMavenProject(), exec, env.getMavenSession());
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to execute mojo", e);
        } finally {
            if (executionMap != null)
                executionMap.remove(FAKE_EXECUTION_ID);
        }
    }
    
    /**
     * Entry point for executing a mojo
     *
     * @param plugin        The plugin to execute
     * @param goal          The goal to execute
     * @param configuration The execution configuration
     * @param env           The execution environment
     * @throws MojoExecutionException If there are any exceptions locating or executing the mojo
     */
    public static void executeMojoImpl(Plugin plugin, String goal, Xpp3Dom configuration, ExecutionEnvironmentM3 env)
            throws MojoExecutionException {
        if (configuration == null) {
            throw new NullPointerException("configuration may not be null");
        }
        try {
            String executionId = null;
            if (goal != null && goal.length() > 0 && goal.indexOf('#') > -1) {
                int pos = goal.indexOf('#');
                executionId = goal.substring(pos + 1);
                goal = goal.substring(0, pos);
            }

            MavenSession session = env.getMavenSession();

            PluginDescriptor pluginDescriptor =
                    env.getBuildPluginManager().loadPlugin(
                            plugin,
                            env.getMavenProject().getRemotePluginRepositories(),
                            session.getRepositorySession());
            MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo(goal);
            if (mojoDescriptor == null) {
                throw new MojoExecutionException("Could not find goal '" + goal + "' in plugin "
                        + plugin.getGroupId() + ":"
                        + plugin.getArtifactId() + ":"
                        + plugin.getVersion());
            }
            MojoExecution exec = mojoExecution3(mojoDescriptor, executionId, configuration);
            env.getBuildPluginManager().executeMojo(session, exec);
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to execute mojo", e);
        }
    }

    @SuppressWarnings({"unchecked"}) // Maven 2 API isn't generic
    private static Map<String, PluginExecution> getExecutionsAsMap(Plugin pomPlugin) {
        return (Map<String, PluginExecution>) pomPlugin.getExecutionsAsMap();
    }

    private static MojoExecution mojoExecution2(MojoDescriptor mojoDescriptor, String executionId,
            Xpp3Dom configuration) {
        if (executionId != null) {
            return new MojoExecution(mojoDescriptor, executionId);
        } else {
            return new MojoExecution(mojoDescriptor, configuration);
        }
    }

    private static MojoExecution mojoExecution3(MojoDescriptor mojoDescriptor, String executionId,
                                               Xpp3Dom configuration) {
        if (executionId != null) {
            return new MojoExecution(mojoDescriptor, executionId);
        } else {
            configuration = Xpp3DomUtils.mergeXpp3Dom(configuration, toXpp3Dom(mojoDescriptor.getMojoConfiguration()));
            return new MojoExecution(mojoDescriptor, configuration);
        }
    }

    /**
     * Constructs the {@link ExecutionEnvironment} instance fluently
     *
     * @param mavenProject  The current Maven project
     * @param mavenSession  The current Maven session
     * @param pluginManager The Maven plugin manager
     * @return The execution environment
     */
    public static ExecutionEnvironment executionEnvironment(MavenProject mavenProject, MavenSession mavenSession,
                                                            PluginManager pluginManager) {
        return new ExecutionEnvironmentM2(mavenProject, mavenSession, pluginManager);
    }

    /**
     * Constructs the {@link ExecutionEnvironment} instance fluently
     *
     * @param mavenProject  The current Maven project
     * @param mavenSession  The current Maven session
     * @param pluginManager The Maven plugin manager
     * @return The execution environment
     */
    public static ExecutionEnvironment executionEnvironment(MavenProject mavenProject, MavenSession mavenSession,
                                                            BuildPluginManager pluginManager) {
        return new ExecutionEnvironmentM3(mavenProject, mavenSession, pluginManager);
    }

    /**
     * Builds the configuration for the goal using Elements
     *
     * @param elements A list of elements for the configuration section
     * @return The elements transformed into the Maven-native XML format
     */
    public static Xpp3Dom configuration(Element... elements) {
        Xpp3Dom dom = new Xpp3Dom("configuration");
        for (Element e : elements) {
            dom.addChild(e.toDom());
        }
        return dom;
    }

    /**
     * Defines the plugin without its version
     *
     * @param groupId    The group id
     * @param artifactId The artifact id
     * @return The plugin instance
     */
    public static Plugin plugin(String groupId, String artifactId) {
        return plugin(groupId, artifactId, null);
    }

    /**
     * Defines a plugin
     *
     * @param groupId    The group id
     * @param artifactId The artifact id
     * @param version    The plugin version
     * @return The plugin instance
     */
    public static Plugin plugin(String groupId, String artifactId, String version) {
        Plugin plugin = new Plugin();
        plugin.setArtifactId(artifactId);
        plugin.setGroupId(groupId);
        plugin.setVersion(version);
        return plugin;
    }

    /**
     * Wraps the group id string in a more readable format
     *
     * @param groupId The value
     * @return The value
     */
    public static String groupId(String groupId) {
        return groupId;
    }

    /**
     * Wraps the artifact id string in a more readable format
     *
     * @param artifactId The value
     * @return The value
     */
    public static String artifactId(String artifactId) {
        return artifactId;
    }

    /**
     * Wraps the version string in a more readable format
     *
     * @param version The value
     * @return The value
     */
    public static String version(String version) {
        return version;
    }

    /**
     * Wraps the goal string in a more readable format
     *
     * @param goal The value
     * @return The value
     */
    public static String goal(String goal) {
        return goal;
    }

    /**
     * Wraps the element name string in a more readable format
     *
     * @param name The value
     * @return The value
     */
    public static String name(String name) {
        return name;
    }

    /**
     * Constructs the element with a textual body
     *
     * @param name  The element name
     * @param value The element text value
     * @return The element object
     */
    public static Element element(String name, String value) {
        return new Element(name, value);
    }

    /**
     * Constructs the element containing child elements
     *
     * @param name     The element name
     * @param elements The child elements
     * @return The Element object
     */
    public static Element element(String name, Element... elements) {
        return new Element(name, elements);
    }

    /**
     * Element wrapper class for configuration elements
     */
    public static class Element {
        private final Element[] children;
        private final String name;
        private final String text;

        public Element(String name, Element... children) {
            this(name, null, children);
        }

        public Element(String name, String text, Element... children) {
            this.name = name;
            this.text = text;
            this.children = children;
        }

        public Xpp3Dom toDom() {
            Xpp3Dom dom = new Xpp3Dom(name);
            if (text != null) {
                dom.setValue(text);
            }
            for (Element e : children) {
                dom.addChild(e.toDom());
            }
            return dom;
        }
    }

    /**
     * Collects Maven execution information
     */
    public static abstract class ExecutionEnvironment {
        private final MavenProject mavenProject;
        private final MavenSession mavenSession;

        public ExecutionEnvironment(MavenProject mavenProject, MavenSession mavenSession) {
            this.mavenProject = mavenProject;
            this.mavenSession = mavenSession;
        }

        public MavenProject getMavenProject() {
            return mavenProject;
        }

        public MavenSession getMavenSession() {
            return mavenSession;
        }
        
        public abstract void executeMojo(Plugin plugin, String goal, Xpp3Dom configuration)
                throws MojoExecutionException;
    }
    
    public static class ExecutionEnvironmentM2 extends ExecutionEnvironment {
        private final PluginManager pluginManager;
        
        public ExecutionEnvironmentM2(MavenProject mavenProject, MavenSession mavenSession, PluginManager pluginManager) {
            super(mavenProject, mavenSession);
            this.pluginManager = pluginManager;
        }

        public PluginManager getPluginManager() {
            return pluginManager;
        }
        
        public void executeMojo(Plugin plugin, String goal, Xpp3Dom configuration)
                throws MojoExecutionException {
            MojoExecutor.executeMojoImpl(plugin, goal, configuration, this);
        }
    }
    
    public static class ExecutionEnvironmentM3 extends ExecutionEnvironment {
        private final BuildPluginManager buildPluginManager;
        
        public ExecutionEnvironmentM3(MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager buildPluginManager) {
            super(mavenProject, mavenSession);
            this.buildPluginManager = buildPluginManager;
        }

        public BuildPluginManager getBuildPluginManager() {
            return buildPluginManager;
        }
        
        public void executeMojo(Plugin plugin, String goal, Xpp3Dom configuration)
                throws MojoExecutionException {
            MojoExecutor.executeMojoImpl(plugin, goal, configuration, this);
        }
    }
}
