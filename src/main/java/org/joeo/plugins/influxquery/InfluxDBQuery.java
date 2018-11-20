/*
 * The MIT License
 *
 * Copyright (c) 2018 Joe Offenberg
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

package org.joeo.plugins.influxquery;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;

/**
 * Executes an InfluxDB query supposed to return 1 row and compares result with expected threshold.
 * If result is above it marks build as unstable.
 */
public class InfluxDBQuery extends hudson.tasks.Recorder implements SimpleBuildStep {
    private String checkName;
    private String influxQuery;
    private double expectedThreshold;
    private int retryCount;
    private int retryInterval;
    private boolean markUnstable;
    private boolean showResults;

    @DataBoundConstructor
    public InfluxDBQuery(String influxQuery) {
        this.influxQuery = influxQuery;
    }

    @DataBoundSetter public void setInfluxQuery(String influxQuery) {
        this.influxQuery = influxQuery;
    }

    @DataBoundSetter public void setExpectedThreshold(double expectedThreshold) {
        this.expectedThreshold = expectedThreshold;
    }

    @DataBoundSetter public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @DataBoundSetter public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    @DataBoundSetter public void setMarkUnstable(boolean markUnstable) {
        this.markUnstable = markUnstable;
    }

    @DataBoundSetter public void setShowResults(boolean showResults) {
        this.showResults = showResults;
    }

    public String getInfluxQuery() {
        return influxQuery;
    }

    public double getExpectedThreshold() {
        return expectedThreshold;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean getMarkUnstable() {
        return markUnstable;
    }

    public boolean getShowResults() {
        return showResults;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // Job Plugin execution code
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        final EnvVars env = run.getEnvironment(listener);
        DescriptorImpl descriptorImpl = getDescriptor();
        String influxURL = descriptorImpl.getInfluxURL();
        String influxDB = descriptorImpl.getInfluxDB();
        String influxUser = descriptorImpl.getInfluxUser();
        Secret influxPWD = descriptorImpl.getInfluxPWD();

        int currentRetry = 0;
        int queryRecordCount = 0;

        PrintStream logger = listener.getLogger();
        logger.println("Connecting to url:" + influxURL + ", db:" + influxDB+", user:"+ influxUser);
        InfluxDB influxDBClient = null;
        try {
            influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
            String influxQueryEnv = env.expand(influxQuery);
            Query query = new Query(influxQueryEnv, influxDB);

            while (currentRetry <= retryCount) {
                logger.println("==================== Running Check:"+getCheckName()+" ====================");
                logger.println("Running Influx Query:"+query.getCommand()+", retry:" + currentRetry + " from Influx Query Plugin");
                if(currentRetry > 0) {
                    logger.println("Waiting " + retryInterval + " seconds before retry.");
                    TimeUnit.SECONDS.sleep(retryInterval);
                }
                try {
                    QueryResult influxQueryResult = influxDBClient.query(query);
                    if (showResults) {
                        logger.println(influxQueryEnv);
                        String emptyReturn = influxQueryResult.getResults().toString();
                        if (emptyReturn.contains("series=null")) {
                            queryRecordCount = 0;
                            logger.println("Query returned 0 records");
                        } else {
                            queryRecordCount = influxQueryResult.getResults().get(0).getSeries().get(0).getValues()
                                    .size();
                            logger.println("Query returned " + queryRecordCount + " records");
                            logger.println(influxQueryResult.getResults().get(0).getSeries().get(0));
                        }
                    }
                    Double result = null;
                    boolean fail = false;
                    if(queryRecordCount > 0) {
                        result = Double.valueOf(influxQueryResult.getResults().get(0).getSeries().get(0).getValues().get(0).get(1).toString());
                        fail = result > expectedThreshold;
                    } 

                    logger.println("InfluxDB Query "+ query.getCommand() + " returned :"+result);
                    if (result == null) {
                        logger.println("InfluxDB Query returned no results");
                    } else {
                        if (fail) {
                            if (markUnstable) {
                                logger.println("InfluxDB Query returned " + result + " which is more than threshold:"+expectedThreshold+", will mark build Unstable");
                                run.setResult(hudson.model.Result.UNSTABLE);
                                break;
                            } 
                            logger.println("InfluxDB Query returned " + result + " which is more than threshold:"+expectedThreshold+", but build will not be marked as Unstable as per your configuration");
                        } else {
                            logger.println("InfluxDB Query returned " + result + " which is less than threshold:"+expectedThreshold);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.println("Error running query:" + query.getCommand() + ", current retry:"+currentRetry+", max retries:"+retryCount+", message:" + e.getMessage());
                }
                currentRetry++;
            }
            if(currentRetry>retryCount) {
                logger.println("Max number of retries "+retryCount+" reached without being able to compute result");
                if (markUnstable) {
                    run.setResult(hudson.model.Result.UNSTABLE);
                } else {
                    logger.println("Not marking build as unstable");
                }
            }
        } finally {
            if (influxDBClient != null) {
                try {
                    influxDBClient.close();
                } catch (Exception e) {
                    // NOOP
                }
            }
        }
    }

    /**
     * @return the checkName
     */
    public String getCheckName() {
        return checkName;
    }

    /**
     * @param checkName the checkName to set
     */
    @DataBoundSetter public void setCheckName(String checkName) {
        this.checkName = checkName;
    }

}
