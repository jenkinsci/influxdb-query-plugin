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

import hudson.Launcher;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.util.Secret;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.kohsuke.stapler.DataBoundConstructor;
import java.io.IOException;

import java.util.concurrent.TimeUnit;

import jenkins.tasks.SimpleBuildStep;

public class InfluxDBQuery extends hudson.tasks.Recorder implements SimpleBuildStep  {

    private String influxQuery;
    private final int maxQueryRecordCount;
    private final int RetryCount;
    private final int RetryInt;
    private final boolean markUnstable;
    private final boolean showResults;

    @DataBoundConstructor
    public InfluxDBQuery(String influxQuery, 
    							int RetryCount, 
    							int RetryInt, 
    							int maxQueryRecordCount,
    							boolean markUnstable, 
    							boolean showResults, 
    							String queryLinkField,
    							String influxDB, 
    							String influxUser, 
    							Secret influxPWD) {
        
    		this.influxQuery = influxQuery;
        this.maxQueryRecordCount = maxQueryRecordCount;
        this.RetryInt = RetryInt;
        this.RetryCount = RetryCount;
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
        return RetryInt;
    }

    public int getRetryCount() {
        return RetryCount;
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
	
	//Job Plugin execution code
    @Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		final EnvVars env = run.getEnvironment(listener);
		
		String influxURL = getDescriptor().getinfluxURL();
		String influxDB = getDescriptor().getinfluxDB();
		String influxUser = getDescriptor().getinfluxUser();
		Secret influxPWD =  getDescriptor().getinfluxPWD();

		boolean ValidationCheckResult = false;
		int x = 1;
		int queryRecordCount = 0;
		
		listener.getLogger().println("Connecting to " + influxURL + "/query?&db=" + influxDB);
		InfluxDB influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
		String influxQueryEnv= env.expand(influxQuery);
		Query query = new Query(influxQueryEnv, influxDB);

		while (x <= RetryCount) {

			listener.getLogger().println("Influx Query  #" + x + " from Influx Query Plugin");
			listener.getLogger().println("Waiting " + RetryInt + " seconds.");
			TimeUnit.SECONDS.sleep(RetryInt);

			try {

				QueryResult influxQueryResult = influxDBClient.query(query);

				if (showResults == true) {
					listener.getLogger().println(influxQueryEnv);
					String EmptyReturn = influxQueryResult.getResults().toString();
					if (EmptyReturn.contains("series=null")) {
						queryRecordCount = 0;
						listener.getLogger().println("Query returened 0 records");
					} else {
						queryRecordCount = influxQueryResult.getResults().get(0).getSeries().get(0).getValues().size();
						listener.getLogger().println("Query returened " + queryRecordCount + " records");
						listener.getLogger().println(influxQueryResult.getResults().get(0).getSeries().get(0));
					}
				}

				ValidationCheckResult = checkValidation(influxQueryResult, maxQueryRecordCount);
				if (ValidationCheckResult == true) {
					if (markUnstable == true) {
						listener.getLogger().println("InfluxDB Query vlaidation check results in Unstable build");
						x = RetryCount;

						run.setResult(hudson.model.Result.UNSTABLE);

					}

				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			x++;
		}


	}
        
 
 
//set aside for more sophisticated validation in the future
   public boolean checkValidation (QueryResult queryResult,  int maxQueryRecordCount ) {
	   int queryRecordCount = queryResult.getResults().get(0).getSeries().get(0).getValues().size();
//	   System.out.println(queryRecordCount);
		if (queryRecordCount > maxQueryRecordCount) {
		return true;}
		else {return false;}
		}
   






    



}
