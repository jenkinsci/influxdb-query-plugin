package org.jenkinsci.plugins.sample;

import hudson.Functions;
import hudson.model.*;
import hudson.tasks.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.*;

public class BasicPluginTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();



    @Test public void freestyleEcho() throws Exception {
        final String command = "echo hello";

        // Create a new freestyle project with a unique name, with an "Execute shell" build step;
        // if running on Windows, this will be an "Execute Windows batch command" build step
        FreeStyleProject project = j.createFreeStyleProject();
        Builder step = Functions.isWindows() ? new BatchFile(command) : new Shell(command); 
        project.getBuildersList().add(step);

        // Enqueue a build of the project, wait for it to complete, and assert success
        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        // Assert that the console log contains the output we expect
        j.assertLogContains(command, build);
      }

}