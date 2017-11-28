# ign-loader-spark
Sample spark based ignite loader


Usage:
  
   ###Load data to cache:   
```
   ./run.sh load <cache_name> <BinaryObject_name> file:///<path_to_any_parquet_file>
```
   e.g.:
```
   ./run.sh load TEST DATASET1 hdfs:///data/file.parquet
```
 
  ###Drop cache:
```
   ./run.sh drop <cache_name>
```
 
  ###Run SQL:
```
  ./run.sh sql <cache_name>  <SQL_statement>
```
  e.g.:
```
  ./run.sh sql TEST 'SELECT COUNT(*) FROM DATASET1'
```
