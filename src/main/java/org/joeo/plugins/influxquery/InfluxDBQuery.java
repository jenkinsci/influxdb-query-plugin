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
    private final int maxQueryRecordCount;
    private final int retryCount;
    private final int retryInterval;
    private final boolean markUnstable;
    private final boolean showResults;

    @DataBoundConstructor
    public InfluxDBQuery(String influxQuery, int retryCount, int retryInterval, int maxQueryRecordCount,
            boolean markUnstable, boolean showResults, String queryLinkField, String influxDB, String influxUser,
            Secret influxPWD) {
        this.influxQuery = influxQuery;
        this.maxQueryRecordCount = maxQueryRecordCount;
        this.retryInterval = retryInterval;
        this.retryCount = retryCount;
        this.markUnstable = markUnstable;
        this.showResults = showResults;
    }

    public String getinfluxQuery() {
        return influxQuery;
    }

    public int getmaxQueryRecordCount() {
        return maxQueryRecordCount;
    }

    public int getRetryInt() {
        return retryInterval;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean getmarkUnstable() {
        return markUnstable;
    }

    public boolean getshowResults() {
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

        String influxURL = getDescriptor().getinfluxURL();
        String influxDB = getDescriptor().getinfluxDB();
        String influxUser = getDescriptor().getinfluxUser();
        Secret influxPWD = getDescriptor().getinfluxPWD();

        boolean validationCheckResult = false;
        int x = 0;
        int queryRecordCount = 0;

        listener.getLogger().println("Connecting to " + influxURL + "/query?&db=" + influxDB);
        InfluxDB influxDBClient = null;
        try {
            influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
            String influxQueryEnv = env.expand(influxQuery);
            Query query = new Query(influxQueryEnv, influxDB);

            while (x < retryCount) {
                listener.getLogger().println("Influx Query  #" + x + " from Influx Query Plugin");
                if(x > 0) {
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
                            listener.getLogger().println("Query returened 0 records");
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
                            listener.getLogger().println("InfluxDB Query returned more results than max accepted, will mark Unstable build");
                            run.setResult(hudson.model.Result.UNSTABLE);
                            break;
                        }
                        listener.getLogger().println("InfluxDB Query returned more results than max accepted, but build will not be marked as Unstable as per your configuration");
                    } else {
                        listener.getLogger().println("InfluxDB Query returned more results than max accepted"+maxQueryRecordCount);
                        break;
                    }
                } catch (Exception e) {
                    listener.getLogger().println("Error running query=" + query + ", retry number="+retryCount+", message:" + e.getMessage());
                }
                x++;
            }
            if(x==retryCount) {
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
     * @param queryResult
     * @param maxQueryRecordCount
     * @return
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
