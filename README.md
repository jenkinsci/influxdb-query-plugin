# InfluxDB Query Jenkins Plugin
=================================


[![License](http://img.shields.io/:license-mit-brightgreen.svg)](https://opensource.org/licenses/MIT)

The plugin provides a mechanism for querying InfluxDB as a post build step for use as a deployment gateway.   
Using a time series database to for aggregating testing and development tool data makes sense if you can query it after all the testing is complete to determine if a build is stable.

## Installation
  Prequisites

  * Jenkins running on Java 1.7 or later

## Global Configuration

  Select **Manage Jenkins** -> *Configure System*, scroll down to **InfluxDB Query Plugin**
  
  * **InfluxDB URL:**  The complete url including port of the Influxdb e.g. http://localhost:8086 or http://host.domain.com:8086 
  
  * **InfluxDB Database**  Database name where relevant events are stored e.g. _overops_
  
  * **InfluxDB User**  InfluxDB username with access to the relevant events.
  
  * **InfluxDB Password**  Password for InfluxDB user.
  
Test connection would show you a count of available metrics.  If the count shows 0 measurements, credentials are correct but database may be wrong.  

If credentials are incorrect you will receive an authentication error.
  

## Job Post Step Configuration

 On Job, select **Add Post-build step**, select **Query InfluxDB** then configure: 

  * **Check Name** Name for the check to be run, it is display in console for better understanding of performed check.
  * **Influx Query**  InfluxDB select query supposed to return 1 value. 
    It can be a sum, count or function returning only one value. 
    May use Jenkins tokens such as build number in the query. e.g. 

  * **Expected Threshold**  Threshold for the value returned by query result. If exceeded and if Mark Build Unstable is selected, the build will be marked unstable.

  * **Retry Count**  Number of times to execute the query as a single post-build step.

  * **Retry Interval**  Time to wait in between each query in seconds.

  * **Mark Build Unstable**  Check if we should mark the build unstable if the Max Record Count is exceeded.  

  * **Show Query Results**  Check if we should should display the query results in the Jenkins console.

  You can configure multiple Queries.