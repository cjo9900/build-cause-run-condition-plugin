/*
 * The MIT License
 *
 * Copyright (C) 2012 by Chris Johnson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package  org.jenkins_ci.plugins.build_cause_run_condition;

import hudson.model.Cause.LegacyCodeCause;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Cause.UserCause;
import hudson.model.*;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.matrix.AxisList;
import hudson.tasks.Builder;
import org.jvnet.hudson.test.MockBuilder;

import hudson.triggers.TimerTrigger.TimerTriggerCause;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.jenkins_ci.plugins.build_cause_run_condition.ExtendedCauseCondition.UpstreamCauseCondition;
import org.jenkins_ci.plugins.build_cause_run_condition.ExtendedCauseCondition.UserBuildCauseCondition;
import org.jenkins_ci.plugins.run_condition.RunCondition;

import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner.Run;
import org.jenkinsci.plugins.conditionalbuildstep.ConditionalBuilder;

import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import java.util.Collections;

public class ExtendedCauseConditionTest extends HudsonTestCase {
    //-------------------------------------------------------
    //UserCause deprecated after Jenkins 1.427
    @Test
    public void testUCAnyUser() throws Exception {

        List<Cause> BuildCauses = new ArrayList<Cause>();

// tests with no users defined

        ExtendedCauseCondition condition = new ExtendedCauseCondition(new UserBuildCauseCondition(""), false);

        // started by SYSTEM user
        BuildCauses.add(new UserCause());
        runtest(BuildCauses, condition, true);

        // started by user
        BuildCauses.clear();
        BuildCauses.add(createUserCause("fred"));
        runtest(BuildCauses, condition, true);

        // started by different causes
        BuildCauses.clear();
        BuildCauses.add(new LegacyCodeCause());
        runtest(BuildCauses, condition, false);

// test with a single user defined

        condition = new ExtendedCauseCondition(new UserBuildCauseCondition("fred"), false);

        // started by correct user
        BuildCauses.clear();
        BuildCauses.add(createUserCause("fred"));
        runtest(BuildCauses, condition, true);

        // started by different user
        BuildCauses.clear();
        BuildCauses.add(createUserCause("eviloverlord"));
        runtest(BuildCauses, condition, false);

        // started by mltiple users including requested
        BuildCauses.clear();
        BuildCauses.add(createUserCause("tom"));
        BuildCauses.add(createUserCause("fred"));
        BuildCauses.add(createUserCause("harry"));

        runtest(BuildCauses, condition, true);

        // started by different users not requested
        BuildCauses.clear();
        BuildCauses.add(createUserCause("tom"));
        BuildCauses.add(createUserCause("harry"));
        BuildCauses.add(createUserCause("eviloverlord"));
        runtest(BuildCauses, condition, false);

        // started by different causes
        BuildCauses.clear();
        BuildCauses.add(new LegacyCodeCause());
        runtest(BuildCauses, condition, false);

        // multiple different causes
        BuildCauses.add(new RemoteCause("dummy_host", "dummynote") );
        runtest(BuildCauses, condition, false);

// test with multiple users defined

        condition = new ExtendedCauseCondition(new UserBuildCauseCondition("fred,tom"), false);

        // user 1
        BuildCauses.clear();
        BuildCauses.add(createUserCause("fred"));
        runtest(BuildCauses, condition, true);  // matching case #1

        //user 2
        BuildCauses.clear();
        BuildCauses.add(createUserCause("tom"));
        runtest(BuildCauses, condition, true);     // matching case#2

        // different user
        BuildCauses.clear();
        BuildCauses.add(createUserCause("eviloverlord"));
        runtest(BuildCauses, condition, false);     // non-matching case

// test with Exclusive set

        condition = new ExtendedCauseCondition(new UserBuildCauseCondition("fred"), true);

        // started by correct user
        BuildCauses.clear();
        BuildCauses.add(createUserCause("fred"));
        runtest(BuildCauses, condition, true);

        // started by several users
        BuildCauses.clear();
        BuildCauses.add(createUserCause("eviloverlord"));
        BuildCauses.add(createUserCause("fred"));
        runtest(BuildCauses, condition, false);

        // started by several causes
        BuildCauses.clear();
        BuildCauses.add(createUserCause("eviloverlord"));
        BuildCauses.add(createUserCause("fred"));
        BuildCauses.add(new LegacyCodeCause());
        BuildCauses.add(new RemoteCause("dummy_host", "dummynote") );
        BuildCauses.add(new TimerTriggerCause());
        runtest(BuildCauses, condition, false);
    }

    @Test
    public void testUpstreamCause() throws Exception {

        FreeStyleProject upProject = createFreeStyleProject("firstProject");
        FreeStyleBuild upBuild = upProject.scheduleBuild2(0).get();

        List<Cause> BuildCauses = new ArrayList<Cause>();
        BuildCauses.add(new UpstreamCause(upBuild));

        ExtendedCauseCondition condition = new ExtendedCauseCondition(new UpstreamCauseCondition(""), false);

        runtest(BuildCauses, condition, true);

        BuildCauses.clear();
        BuildCauses.add(new RemoteCause("dummy_host", "dummynote") );
        runtest(BuildCauses, condition, false);


        BuildCauses.clear();
        BuildCauses.add(new LegacyCodeCause());
        BuildCauses.add(createUserCause("remote"));
        BuildCauses.add(new RemoteCause("dummy_host", "dummynote") );

        runtest(BuildCauses, condition, false);


        BuildCauses.clear();
        BuildCauses.add(new LegacyCodeCause());
        BuildCauses.add(createUserCause("remote"));
        BuildCauses.add(new UpstreamCause(upBuild));
        BuildCauses.add(new RemoteCause("dummy_host", "dummynote") );

        runtest(BuildCauses, condition, true);

        condition = new ExtendedCauseCondition(new UpstreamCauseCondition("firstProject"), false);

        // use same causes as above
        runtest(BuildCauses, condition, true);

        condition = new ExtendedCauseCondition(new UpstreamCauseCondition("not_exist_proj"), false);

        // use same causes as above
        runtest(BuildCauses, condition, false);
    }


    @Bug(14438)
    @Test
    public void testMatrixUpstreamCause() throws Exception {

        // setup some Causes
        FreeStyleProject upProject = createFreeStyleProject("firstProject");
        FreeStyleBuild upBuild = upProject.scheduleBuild2(0).get();

        Cause upstreamCause = new UpstreamCause(upBuild);
        Cause userCause = createUserCause("testUser");

        // test upstream condition
        RunCondition condition = new ExtendedCauseCondition(new UpstreamCauseCondition("firstProject"), false);
        runMatrixTest(upstreamCause, condition, true);
        runMatrixTest(userCause, condition, false);

        //test User condition
        condition = new ExtendedCauseCondition(new UpstreamCauseCondition("not_exist_proj"), false);
        runMatrixTest(upstreamCause, condition, false);
        runMatrixTest(userCause, condition, false);



    }
    @Bug(14438)
    @Test
    public void testMatrixUserCause() throws Exception {

        // setup some Causes
        FreeStyleProject upProject = createFreeStyleProject("secondProject");
        FreeStyleBuild upBuild = upProject.scheduleBuild2(0).get();

        Cause upstreamCause = new UpstreamCause(upBuild);
        Cause userCause = createUserCause("testUser");

        //test Any User condition
        RunCondition condition = new ExtendedCauseCondition(new UserBuildCauseCondition(""), false);

        runMatrixTest(upstreamCause, condition, false);
        runMatrixTest(userCause, condition, true);

        //test User condition
        condition = new ExtendedCauseCondition(new UserBuildCauseCondition("testUser"), false);

        runMatrixTest(upstreamCause, condition, false);
        runMatrixTest(userCause, condition, true);

        //test not User condition
        condition = new ExtendedCauseCondition(new UserBuildCauseCondition("NotMatching"), false);

        runMatrixTest(upstreamCause, condition, false);
        runMatrixTest(userCause, condition, false);
    }


    private void runMatrixTest(Cause buildTrigger, RunCondition condition, Boolean builderRuns) throws Exception {
        MatrixProject matrixProject = createMatrixProject();

        // if the builder should run the result for each subbuild should be unstable
        // if the builder is not to run the result for each subbuild should be success.
        Result testResult = builderRuns ? Result.UNSTABLE:Result.SUCCESS;

        // create conditional build step requirements
        List<Builder> builders = Collections.singletonList((Builder)new MockBuilder(Result.UNSTABLE));

        BuildStepRunner runner = new Run();

        // add conditional build step
        matrixProject.getBuildersList().add(new ConditionalBuilder(condition, runner, builders));

        MatrixBuild matrixBuild = matrixProject.scheduleBuild2(0, buildTrigger).get();

        List<MatrixRun> runs = matrixBuild.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(testResult, run);
        }

    }

    @Override
    protected MatrixProject createMatrixProject(String name) throws IOException {
        MatrixProject p = super.createMatrixProject(name);

        // set up 2x2 matrix
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db","mysql","oracle"));
        axes.add(new TextAxis("direction","north","south"));
        p.setAxes(axes);

        return p;
    }


    private void runtest(List<Cause> causes, ExtendedCauseCondition condition, boolean expected) throws Exception  {

        FreeStyleProject project = createFreeStyleProject();
        FreeStyleBuild build;

        if (causes.size() > 0) {
            build = project.scheduleBuild2(5, causes.remove(0)).get();
        } else {
            build = project.scheduleBuild2(5).get();
        }
        if (causes.size() > 0) {
            // add other causes
            build.getAction(CauseAction.class).getCauses().addAll(causes);
        }

        System.out.println(build.getDisplayName()+" completed");
        List<Cause> buildCauses = build.getCauses();
        for (Cause cause2 : buildCauses) {
            System.out.println("DESC:" + cause2.getShortDescription());
            if (Cause.UserCause.class.isInstance(cause2)) {
                UserCause userCause = (UserCause)cause2;
                System.out.println("UN:" + userCause.getUserName());
            }
        }
        StreamBuildListener listener = new StreamBuildListener(System.out,Charset.defaultCharset());

        boolean testresult = condition.runPerform(build, listener);
        assertEquals(expected, testresult);
    }

    private UserCause createUserCause(String userid) {
        Authentication a = new UsernamePasswordAuthenticationToken(userid, userid);

        a = hudson.getSecurityRealm().getSecurityComponents().manager.authenticate(a);
        SecurityContextHolder.getContext().setAuthentication(a);
        UserCause cause = new UserCause();

        return cause;
    }
}
