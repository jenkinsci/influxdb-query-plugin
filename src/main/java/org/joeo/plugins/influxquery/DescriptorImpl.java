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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

//DescriptorImpl governs the global config settings

    @Extension @Symbol("influxDbQuery") 
    public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

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



     //Allows for persisting global config settings in JSONObject
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


      public String getinfluxDB() {
            return influxDB;
        }
      public String getinfluxURL() {
            return influxURL;
        }
      public String getinfluxUser() {
            return influxUser;
        }
      public Secret getinfluxPWD() {
            return influxPWD;
        }

      public void setinfluxDB(String influxDB) {
          this.influxDB = influxDB;
      }

      public void setinfluxURL(String influxURL) {
          this.influxURL = influxURL;
      }
      public void setinfluxUser(String influxUser) {
          this.influxUser = influxUser;
      }

      public void setinfluxPWD(Secret influxPWD) {
          this.influxPWD = influxPWD;
      }
   



   //Added @POST to help protect against CSRF 
   @POST   
   public FormValidation doTestConnection(@QueryParameter("influxURL") final String influxURL,
        		 @QueryParameter("influxDB") final String influxDB, @QueryParameter("influxUser") final String influxUser,  @QueryParameter("influxPWD") final Secret influxPWD)
        		throws ServletException, IOException, InterruptedException  {
	   		//Admin permission check
	   		Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER); 
      	   
	   		try {

     	    	InfluxDB influxDBClient = InfluxDBFactory.connect(influxURL, influxUser, Secret.toString(influxPWD));
      	    	Query query = new Query("show measurements", influxDB);
      	    	System.out.println("Test Query from Jenkins Plugin");
      	    	System.out.println("Connecting to " + influxURL + "/" + influxDB );

      	    	QueryResult result = influxDBClient.query(query);

      	    	int numMeasurements = result.getResults().get(0).getSeries().get(0).getValues().size();
      	    	System.out.println("Connection Successful.  Found " + numMeasurements + " Measurements");
      	    	return FormValidation.ok("Connection Successful.  Found " + numMeasurements + " Measurements");

      	    } catch (Exception e) {
      	      e.printStackTrace();
      	      if (e.getMessage() != null)
      	      return FormValidation.error("Client error : " + e.getMessage());
              return FormValidation.error("Client error : Database Error");

      	    }

      	}

    }
