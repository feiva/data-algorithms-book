package org.dataalgorithms.bonus.logquery.spark;

import java.util.List;
import com.google.common.collect.Lists;
import scala.Tuple2;
import scala.Tuple3;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;

/**
 * Spark Log Query
 *  
 * Usage: SparkLogQuery [logFile]
 *
 * @author Mahmoud Parsian
 */
public class SparkLogQuery {

  /**
   * here we assume that we have normalized web server log files
   * and we have extracted essential information for each log record
   */
  public static final List<String> EXAMPLE_LOGS = Lists.newArrayList(
  	// <ip-address><,><user-id><,><number-of-bytes><,><query>
    "10.20.30.40,u200,500,query1",
    "10.20.30.41,u300,600,query1",
    "10.20.30.42,u400,700,query2",
    "10.20.30.40,u200,-,query1",	// "-" signifies undefined number of bytes
    "10.20.30.41,u300,600,query1",
    "10.20.30.47,-,600,query1", 	// "-" signifies undefined user
    "10.20.30.42,u400,700,query2"
    );

  // tokens = [<ip-address><user-id><number-of-bytes><query>]
  public static Tuple3<String, String, String> createKey(String[] tokens) {
     String userID = tokens[1];
     if (userID.equals("-")) {
        // undefined user
        return new Tuple3<String, String, String>(null, null, null);
     }
     else {
       String ipAddress = tokens[0];
       // String userID = tokens[1];
       String query = tokens[3];
       return new Tuple3<String, String, String>(ipAddress, userID, query);
     }
  }

  // tokens = [<ip-address><user-id><number-of-bytes><query>]
  public static LogStatistics createLogStatistics(String[] tokens) {
     String numberOfBytesAsString = tokens[2];
     if (numberOfBytesAsString.equals("-")) {
        return new LogStatistics(1, 0);
     }
     else {
        int numberOfBytes = Integer.parseInt(numberOfBytesAsString);
        return new LogStatistics(1, numberOfBytes);
     } 
  }

  public static void main(String[] args) {

    // create a context object, which is a factory for creating new RDDs
    SparkConf sparkConf = new SparkConf().setAppName("basic log query");
    JavaSparkContext sc = new JavaSparkContext(sparkConf);

    // create the logs RDD as JavaRDD<String>
    JavaRDD<String> logs = null;
    if (args.length == 1) {
       logs  = sc.textFile(args[0]);
    }
    else {
       logs = sc.parallelize(EXAMPLE_LOGS);
    }

    // extract all essential log data
    JavaPairRDD<Tuple3<String, String, String>, LogStatistics> extracted = 
       logs.mapToPair(new PairFunction<String, Tuple3<String, String, String>, LogStatistics>() {
      @Override
      public Tuple2<Tuple3<String, String, String>, LogStatistics> call(String logRecord) {
         String[] tokens = logRecord.split(",");
         Tuple3<String, String, String> key = createKey(tokens);
         LogStatistics value = createLogStatistics(tokens);
         return new Tuple2<Tuple3<String, String, String>, LogStatistics>(key, value);
      }
    });
    
    // filter the ones where userID is undefined
    JavaPairRDD<Tuple3<String, String, String>, LogStatistics>  filtered = 
        extracted.filter(new Function<
                                      Tuple2<Tuple3<String, String, String>, LogStatistics>, 
                                      Boolean
                                     >() {
        public Boolean call(Tuple2<Tuple3<String, String, String>, LogStatistics> s) { 
            Tuple3<String, String, String> t3 = s._1;
            return (t3._1() != null); // exclude Tuple3(null,null,null)
        }
    });

    // reduce by key
    JavaPairRDD<Tuple3<String, String, String>, LogStatistics> counts = 
       filtered.reduceByKey(new Function2<LogStatistics, LogStatistics, LogStatistics>() {
       @Override
       public LogStatistics call(LogStatistics stats, LogStatistics stats2) {
         return stats.merge(stats2);
       }
    });

    // emit final output
    List<Tuple2<Tuple3<String, String, String>, LogStatistics>> output = counts.collect();
    for (Tuple2<?,?> t : output) {
       System.out.println(t._1() + "\t" + t._2());
    }
    
    // done
    sc.stop();
    System.exit(0);
  }
}
