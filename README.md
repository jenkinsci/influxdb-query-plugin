# InfluxDB Query Jenkins Plugin

## Installation
  Prequisites
  * OverOps application deplyment naming in place 
  https://support.overops.com/hc/en-us/articles/218437947-Naming-Your-Application-Servers-and-Deployments-
  * OverOps configrued to send statsd metrics to influxdb 
  https://support.overops.com/hc/en-us/articles/218438597-Publish-Metrics
  * Jenkins running on Java 1.7 or later
  
  1. Download CheckOverOps.hpi file from the /bin directory
  2. Select Manage Jenkins ->  Manage Plugins -> Advanced Tab
  3. Upload a Plugin -> Choose file from saved location, click upload
  4. Check "Restart Jenkins when installation is complete and no jobs are running."

## Global Configuration

  Select Manage Jenkins -> Configure Plugin 
  scroll down to **Check OverOps Plugin**
  
  **OverOps InfluxDB URL:**  The complete url including port of the Influxdb e.g. http://localhost:8086 or http://host.domain.com:8086 
  
  **OverOps InfluxDB Database**  Database name where relevant OverOps events are stored e.g. _overops_
  
  **OverOps InfluxDB User**  InfluxDB username with access to the relevant events.
  
  **OverOps InfluxDB Password**  Password for InfluxDB user.
  
  Test connection would show you a count of available metrics.  If the count shows 0 measurements, credentials are correct but    database may be wrong.  If credentials are incoorect you will receive an authentication error.
  

## Job Post Build Configuration
**Influx Query**  InfluxDB select query to retun errors pertaining to the current build.  May use Jenkins tokens such as build number in the query.  e.g. 
      select * from DevOps where application = 'ArchRival2.1' deployment = '2-1-$BUILD_NUMBER'

**Max Record Count**  Number of acceptable errors.  If query record count exceeds this limit and if Mark Build Unstable is selected, the build will be marked unstable.

**Retry Count**  Number of times to execute the query as a single post-build step.

**Retry Interval**  Time to wait in between each query in seconds.

**Query Link Field**  The field name in the database which contains the ARC link data. Defaults to _link_

**ARC Link URL**  This is needed to provide ARC links in the console log.  The statsd data only contains the unique part of the link.  The ARC link (tiny link) provided by OverOps can be different from one organization to the next or if the install is on premise instead of SaaS.  Defaults to _http://tkp.to_  

**Mark Build Unstable**  Check if we should mark the build unstable if the Max Record Count is exceeded.  

**Show Query Results**  Check if we should should display the query results in the Jenkins console.
