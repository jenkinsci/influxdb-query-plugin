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

import javax.servlet.ServletException;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

//DescriptorImpl governs the global config settings

@Extension
@Symbol("influxDbQuery")
public final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBQuery.class);
    private String influxDB;
    private String influxURL;
    private String influxUser;
    private Secret influxPWD;

    public DescriptorImpl() {
        super(InfluxDBQuery.class);
        load();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Query InfluxDB";
    }

    // Allows for persisting global config settings in JSONObject
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        formData = formData.getJSONObject("InfluxDBQuery");
        influxDB = formData.getString("influxDB");
        influxURL = formData.getString("influxURL");
        influxUser = formData.getString("influxUser");
        influxPWD = Secret.fromString(formData.getString("influxPWD"));
        save();
        return false;
    }

    public String getInfluxDB() {
        return influxDB;
    }

    public String getInfluxURL() {
        return influxURL;
    }

    public String getInfluxUser() {
        return influxUser;
    }

    public Secret getInfluxPWD() {
        return influxPWD;
    }

    public void setInfluxDB(String influxDB) {
        this.influxDB = influxDB;
    }

    public void setInfluxURL(String influxURL) {
        this.influxURL = influxURL;
    }

    public void setInfluxUser(String influxUser) {
        this.influxUser = influxUser;
    }

    public void setInfluxPWD(Secret influxPWD) {
        this.influxPWD = influxPWD;
    }

    // Added @POST to help protect against CSRF
    @POST
    public FormValidation doTestConnection(@QueryParameter("influxURL") final String influxURL,
            @QueryParameter("influxDB") final String influxDB, @QueryParameter("influxUser") final String influxUser,
            @QueryParameter("influxPWD") final Secret influxPWD)
            throws ServletException, IOException, InterruptedException {
        // Admin permission check
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        InfluxDB influxDBClient = null;
        try {
            influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
            Query query = new Query("show measurements", influxDB);
            LOGGER.info("Testing query from Jenkins Plugin with url:{}", influxURL + "/" + influxDB);
            QueryResult result = influxDBClient.query(query);
            int numMeasurements = result.getResults().get(0).getSeries().get(0).getValues().size();
            LOGGER.info("Connection Successful. Found {} measurements", numMeasurements);
            return FormValidation.ok("Connection Successful.  Found " + numMeasurements + " Measurements");

        } catch (Exception e) {
            LOGGER.error("Error testing connection with url({}), influxDB({}), influxUser({}), got error : {}", influxURL, influxDB,influxUser, 
                    e.getMessage(), e);
            if (e.getMessage() != null) {
                return FormValidation.error(e, "Client error : " + e.getMessage());
            }
            return FormValidation.error(e, "Client error : Database Error");
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
}
