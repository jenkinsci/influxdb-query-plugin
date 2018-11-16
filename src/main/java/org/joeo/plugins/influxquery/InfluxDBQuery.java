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
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;

public class InfluxDBQuery extends hudson.tasks.Recorder implements SimpleBuildStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBQuery.class);
    private String influxQuery;
    private int maxQueryRecordCount;
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

    @DataBoundSetter public void setMaxQueryRecordCount(int maxQueryRecordCount) {
        this.maxQueryRecordCount = maxQueryRecordCount;
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

    public int getMaxQueryRecordCount() {
        return maxQueryRecordCount;
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
        // TODO Auto-generated method stub
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

        String influxURL = getDescriptor().getInfluxURL();
        String influxDB = getDescriptor().getInfluxDB();
        String influxUser = getDescriptor().getInfluxUser();
        Secret influxPWD = getDescriptor().getInfluxPWD();

        boolean validationCheckResult = false;
        int currentRetry = 0;
        int queryRecordCount = 0;

        listener.getLogger().println("Connecting to url:" + influxURL + ", db:" + influxDB+", user:"+ influxUser);
        InfluxDB influxDBClient = null;
        try {
            influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
            String influxQueryEnv = env.expand(influxQuery);
            Query query = new Query(influxQueryEnv, influxDB);

            while (currentRetry <= retryCount) {
                listener.getLogger().println("Running Influx Query:"+query.getCommandWithUrlEncoded()+", retry:" + currentRetry + " from Influx Query Plugin");
                if(currentRetry > 0) {
                    listener.getLogger().println("Waiting " + retryInterval + " seconds before retry.");
                    TimeUnit.SECONDS.sleep(retryInterval);
                }
                try {
                    QueryResult influxQueryResult = influxDBClient.query(query);
                    if (showResults) {
                        listener.getLogger().println(influxQueryEnv);
                        String EmptyReturn = influxQueryResult.getResults().toString();
                        if (EmptyReturn.contains("series=null")) {
                            queryRecordCount = 0;
                            listener.getLogger().println("Query returned 0 records");
                        } else {
                            queryRecordCount = influxQueryResult.getResults().get(0).getSeries().get(0).getValues()
                                    .size();
                            listener.getLogger().println("Query returned " + queryRecordCount + " records");
                            listener.getLogger().println(influxQueryResult.getResults().get(0).getSeries().get(0));
                        }
                    }
                    validationCheckResult = checkValidation(influxQueryResult, maxQueryRecordCount);
                    if (validationCheckResult) {
                        if (markUnstable) {
                            listener.getLogger().println("InfluxDB Query returned more results than max accepted:"+maxQueryRecordCount+", will mark Unstable build");
                            run.setResult(hudson.model.Result.UNSTABLE);
                            break;
                        }
                        listener.getLogger().println("InfluxDB Query returned more results than max accepted:"+maxQueryRecordCount+", but build will not be marked as Unstable as per your configuration");
                    } else {
                        listener.getLogger().println("InfluxDB Query returned more results than max accepted"+maxQueryRecordCount);
                        break;
                    }
                } catch (Exception e) {
                    listener.getLogger().println("Error running query:" + query.getCommandWithUrlEncoded() + ", current retry:"+currentRetry+", max retries:"+retryCount+", message:" + e.getMessage());
                }
                currentRetry++;
            }
            if(currentRetry>retryCount) {
                listener.getLogger().println("Max number of retries "+retryCount+" reached without being able to compute result");
                if (markUnstable) {
                    run.setResult(hudson.model.Result.UNSTABLE);
                } else {
                    listener.getLogger().println("Not marking build as unstable");
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
     * set aside for more sophisticated validation in the future
     * 
     * @param queryResult {@link QueryResult} Influx query result
     * @param maxQueryRecordCount if exceeded validation is true
     * @return true if number of records in queryResult is higher than maxQueryRecordCount 
     */
    public boolean checkValidation(QueryResult queryResult, int maxQueryRecordCount) {
        int queryRecordCount = queryResult.getResults().get(0).getSeries().get(0).getValues().size();
        LOGGER.info("Got {} records, maxQueryRecordCount is {}", queryRecordCount, maxQueryRecordCount);
        if (queryRecordCount > maxQueryRecordCount) {
            return true;
        } else {
            return false;
        }
    }
}
