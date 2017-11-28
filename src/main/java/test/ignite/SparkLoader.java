package test.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteOutClosure;
import org.apache.ignite.spark.JavaIgniteContext;
import org.apache.ignite.spark.JavaIgniteRDD;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SparkLoader {

    private static final Logger logger = Logger.getLogger(SparkLoader.class);
    private static SparkConf sparkConf = new SparkConf()
        .setAppName("IgniteLoader")
        .setMaster("yarn")
        .set("spark.executor.instances", "5");


    public static void main(String[] args) {

        String action = args[0];

        // Adjust the logger to exclude the logs of no interest.
        Logger.getRootLogger().setLevel(Level.ERROR);
        Logger.getLogger("org.apache.ignite").setLevel(Level.INFO);
        Logger.getLogger("sncr.xdf.ignite").setLevel(Level.DEBUG);

       // Ignition.setClientMode(true);

        switch(action){

            case "sql" : {
                String cacheName = args[1];
                String sql = args[2];
                sql(cacheName, sql);
                break;
            }

            case "load" : {
                String cache = args[1];
                String tbl = args[2];
                String path = args[3];
                load(cache, tbl, path);
                break;
            }
            case "drop" : {
                String cacheName = args[1];
                drop(cacheName);
                break;
            }
        }
        logger.info("Done");
        System.exit(0);
    }

    private static void load(String cacheName, String tableName, String path){

        logger.info("Starting load...");

        SparkSession sparkSession = SparkSession.builder().config(sparkConf).getOrCreate();
        JavaSparkContext jctx= new JavaSparkContext(sparkSession.sparkContext());
        Dataset<Row> sourceData =  sparkSession.read().parquet(path);
        StructType schema = sourceData.schema();
        CacheConfiguration<BinaryObject, BinaryObject> cacheCfg = prepareLoaderConfiguration(cacheName, tableName, schema);

        IgniteOutClosure ioc = new IgniteOutClosure() {
            @Override
            public Object apply() {
                IgniteConfiguration cfg = new IgniteConfiguration();
                cfg.setCacheConfiguration(cacheCfg);
                return cfg;
            }
        };

        JavaIgniteContext<BinaryObject, BinaryObject> igniteContext =
            new JavaIgniteContext<BinaryObject, BinaryObject>(
                jctx, ioc , false);

        igniteContext.ignite().active(true);

        JavaIgniteRDD<BinaryObject, BinaryObject> igniteRDD
            = igniteContext.fromCache(cacheName).withKeepBinary();
        JavaRDD<BinaryObject> rdd = sourceData
            .javaRDD()
            .map(new RowToIgniteBinaryObjectConverter(schema,
                                                      tableName
                                                      ));
        igniteRDD.saveValues(rdd);

        logger.info("Load Complete");
        igniteContext.close(true);
    }

    private static void sql(String cacheName, String sqlStr){
        logger.info("======================================================");

        try {
            Ignite ignite = Ignition.start();
            IgniteCache<BinaryObject, BinaryObject> cache = ignite.cache(cacheName).withKeepBinary();
            logger.info("==Ignite Initialized================================");
            logger.info("Starting SQL query : " + sqlStr);
            logger.info("ALL Cache size is " + cache.sizeLong(CachePeekMode.ALL));
            logger.info("PRIMARY Cache size is " + cache.sizeLong(CachePeekMode.PRIMARY));
            logger.info("NEAR Cache size is " + cache.sizeLong(CachePeekMode.NEAR));
            logger.info("OFFHEAP Cache size is " + cache.sizeLong(CachePeekMode.OFFHEAP));
            logger.info("ONHEAP Cache size is " + cache.sizeLong(CachePeekMode.ONHEAP));

            SqlFieldsQuery sql = new SqlFieldsQuery(sqlStr).setLocal(false);
            FieldsQueryCursor<List<?>> result = cache.query(sql);
            printResults(result);

        } catch(Exception e){
            logger.error(e.getMessage());
        }
    }

    private static  void printResults(FieldsQueryCursor<List<?>> result){
        List<List<?>> res = result.getAll();
        logger.info("Result size : " + res.size());

        int i = 0;
        for(List<?> r : res) {
            System.out.print("row " + i + ":[");
            int j = 0;
            for(Object fld : r) {
                String fn = result.getFieldName(j);
                System.out.print(fn + "(" + fld.getClass().getSimpleName() + ") :" + fld + "; ");
                j++;
            }
            System.out.println("]");
            i++;
        }
    }

    private static void drop(String cacheName){
        IgniteConfiguration icfg = new IgniteConfiguration();
        try (Ignite ignite = Ignition.getOrStart(icfg)) {
            ignite.destroyCache(cacheName);
        }
    }

    private static CacheConfiguration<BinaryObject, BinaryObject> prepareLoaderConfiguration(
        String cacheName,  String tableName, StructType schema){
        CacheConfiguration<BinaryObject, BinaryObject> cfg = new CacheConfiguration<>();
        cfg.setName(cacheName);
        cfg.setCacheMode(CacheMode.PARTITIONED);

        // Onheap cache must be enabled for this to work
        //FifoEvictionPolicy<String, BinaryObject> fifoEvictionPolicy = new FifoEvictionPolicy<>(1000);
        //cfg.setEvictionPolicy(fifoEvictionPolicy);
        cfg.setOnheapCacheEnabled(false);

        final QueryEntity queryEntity = new QueryEntity();
        queryEntity.setTableName(tableName);
        queryEntity.setKeyType(String.class.getName());
        queryEntity.setValueType(tableName);

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();

        for (StructField f : schema.fields()) {
            String className = String.class.getName();

            switch(f.dataType().simpleString().toLowerCase()){
                case "string": {
                    className = String.class.getName();
                    break;
                }
                case "double": {
                    className = Double.class.getName();
                    break;
                }
                case "bigint": {
                    className = Long.class.getName();
                    break;
                }
                case "timestamp": {
                    className = String.class.getName();
                    break;
                }
            }
            fields.put(f.name(), className);
        }

        queryEntity.setFields(fields);
        ArrayList<QueryEntity> sqe = new ArrayList<>();
        sqe.add(queryEntity);
        cfg.setQueryEntities(sqe);

        return cfg;
    }
}
